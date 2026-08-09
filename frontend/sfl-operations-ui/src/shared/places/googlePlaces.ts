/**
 * Google Places, loaded only if it is going to be used.
 *
 * <h2>Why a loader rather than a script tag</h2>
 *
 * <p>A `<script>` in `index.html` would fetch Google on every page load for every user, including the
 * ones who never open a trip form, and would put a hard third-party dependency in front of the first
 * paint of an operations dashboard. This loads on first use and caches the promise, so the cost falls
 * only on the screens that need it.
 *
 * <h2>Everything here can fail, and none of it may break the form</h2>
 *
 * <p>The SRS requires an examination centre to keep working through WAN loss (§2.5, §23.3), and Places
 * is the definition of a network dependency. A driver standing at a fuel station with no signal must
 * still be able to record where they are. So every failure path - no key configured, script blocked,
 * offline, quota exhausted, API disabled on the key - resolves to `null` rather than throwing, and the
 * caller falls back to a plain text field. Autocomplete is a convenience that improves consistency; it
 * is never the only way to answer.
 *
 * <h2>Degrading quietly is not the same as degrading silently</h2>
 *
 * <p>It used to be. Every failure became an empty suggestion list, which is indistinguishable from
 * "Google knows no such place" - so a key whose project had the Places API switched off looked exactly
 * like a working field that found nothing, for every query, forever. That is the state this codebase
 * shipped in, and the only symptom was a user typing and watching nothing appear.
 *
 * <p>So a lookup now reports *why* it produced nothing. The field still accepts free text and still
 * never throws; it just stops pretending the absence of suggestions was an answer. The reason is
 * carried back to the caller for the helper text and logged once, in full, for whoever has to fix the
 * key - the useful detail (which API, which project) is in Google's own message.
 */

/** Minimal surface of the Places API this app uses; avoids a dependency on @types/google.maps. */
interface PlacesLibrary {
  AutocompleteSuggestion: {
    fetchAutocompleteSuggestions(request: {
      input: string;
      sessionToken?: unknown;
      includedRegionCodes?: string[];
    }): Promise<{ suggestions: RawSuggestion[] }>;
  };
  AutocompleteSessionToken: new () => unknown;
}

interface RawSuggestion {
  placePrediction?: {
    placeId?: string;
    text?: { toString(): string };
    mainText?: { toString(): string };
    secondaryText?: { toString(): string };
  };
}

/** One place the user can pick. `description` is what is stored - the services take a string. */
export interface PlaceSuggestion {
  placeId: string;
  /** The full formatted description, e.g. "Tema Oil Refinery, Tema, Ghana". */
  description: string;
  /** The distinctive part, shown first. */
  primary: string;
  /** The disambiguating part - town, region - shown quieter. */
  secondary: string;
}

/**
 * What one lookup produced.
 *
 * `unavailable` separates "the service answered, and no place matches" from "the service never
 * answered". Both yield no suggestions; only one of them is worth telling the user about.
 */
export interface PlaceLookup {
  suggestions: PlaceSuggestion[];
  /** A short, actionable reason the lookup could not run. Null when it ran. */
  unavailable: string | null;
}

declare global {
  interface Window {
    google?: { maps?: { importLibrary?: (name: string) => Promise<unknown> } };
  }
}

const apiKey = (import.meta.env.VITE_GOOGLE_MAPS_API_KEY as string | undefined) ?? '';

/** True when a key is configured at all. Callers use it to decide whether to offer the control. */
export const placesConfigured = (): boolean => apiKey.trim().length > 0;

let loading: Promise<PlacesLibrary | null> | null = null;

/** Logged in full exactly once. Repeating it per keystroke would bury it in its own noise. */
let reported = false;

const reportOnce = (reason: string, detail?: unknown) => {
  if (reported) {
    return;
  }
  reported = true;
  // The console is where whoever has to fix the key will look, and Google's own message names both
  // the project and the page that enables the API.
  console.warn(`[places] Address suggestions are unavailable: ${reason}`, detail ?? '');
};

/**
 * Turns whatever Google threw into something the person reading it can act on.
 *
 * <p>The failure that actually happens in practice is a project with the API switched off, and its
 * message names the project and the console page that enables it. That detail belongs in the log; the
 * summary returned here belongs under a form field, so it stays short and blames the configuration
 * rather than the person typing.
 */
const describeFailure = (error: unknown): string => {
  const text = error instanceof Error ? error.message : String(error ?? '');
  if (/not been used in project|SERVICE_DISABLED|is disabled/i.test(text)) {
    return 'the Places API is not enabled for this key’s Google Cloud project';
  }
  if (/ApiTargetBlocked|not authorized|PERMISSION_DENIED|REQUEST_DENIED/i.test(text)) {
    return 'this key is not authorised for the Places API';
  }
  if (/RefererNotAllowed/i.test(text)) {
    return 'this key does not allow requests from this address';
  }
  if (/OverQuota|RESOURCE_EXHAUSTED|quota/i.test(text)) {
    return 'the Places quota for this key is exhausted';
  }
  return 'the suggestion service could not be reached';
};

const loadPlaces = (): Promise<PlacesLibrary | null> => {
  if (loading) {
    return loading;
  }
  if (!placesConfigured()) {
    loading = Promise.resolve(null);
    return loading;
  }

  loading = new Promise<PlacesLibrary | null>((resolve) => {
    const finish = async () => {
      try {
        const library = await window.google?.maps?.importLibrary?.('places');
        resolve((library as PlacesLibrary) ?? null);
      } catch (error) {
        reportOnce(describeFailure(error), error);
        resolve(null);
      }
    };

    if (window.google?.maps?.importLibrary) {
      void finish();
      return;
    }

    const script = document.createElement('script');
    // `loading=async` is Google's own recommendation and keeps the bootstrap off the critical path.
    // It also *requires* a callback, and warns in the console without one; `__sflPlacesReady` is it.
    // Waiting on `script.onload` alone is not the same thing - the tag has loaded before the API has
    // finished initialising, and importLibrary can be undefined at that moment.
    const callback = '__sflPlacesReady';
    (window as unknown as Record<string, unknown>)[callback] = () => void finish();
    script.src =
      `https://maps.googleapis.com/maps/api/js?key=${encodeURIComponent(apiKey)}` +
      `&libraries=places&loading=async&v=weekly&callback=${callback}`;
    script.async = true;
    script.onerror = () => {
      reportOnce('the Google Maps script could not be loaded (offline, blocked, or an invalid key)');
      resolve(null);
    };
    document.head.appendChild(script);
  });

  return loading;
};

/**
 * A billing session, not a security one.
 *
 * <p>Places charges per session: every keystroke while typing one place belongs to one token, and the
 * token is spent when a place is chosen. Without this each keystroke bills as its own request, which
 * for a field somebody types "Kumasi Examination Centre" into is roughly a 25× overcharge. Callers
 * take a token when the field gains focus and release it on selection.
 */
export const newSessionToken = async (): Promise<unknown | null> => {
  const places = await loadPlaces();
  if (!places) {
    return null;
  }
  try {
    return new places.AutocompleteSessionToken();
  } catch {
    return null;
  }
};

/**
 * Suggestions for what has been typed so far.
 *
 * <p>Biased to Ghana: CLET operates four examination centres and a headquarters, all domestic, and an
 * unbiased search offers a Kumasi in three other countries first. A caller needing somewhere abroad
 * still has the free-text fallback.
 *
 * <p>Returns an empty list rather than throwing for every failure, so a form never breaks because a
 * suggestion service did.
 */
export const fetchPlaceSuggestions = async (
  input: string,
  sessionToken?: unknown,
): Promise<PlaceLookup> => {
  const query = input.trim();
  if (query.length < 3) {
    // Below three characters the suggestions are noise and every keystroke is billable.
    return { suggestions: [], unavailable: null };
  }
  if (!placesConfigured()) {
    return { suggestions: [], unavailable: 'no Google Maps key is configured' };
  }
  const places = await loadPlaces();
  if (!places) {
    return { suggestions: [], unavailable: 'the suggestion service could not be loaded' };
  }
  try {
    const { suggestions } = await places.AutocompleteSuggestion.fetchAutocompleteSuggestions({
      input: query,
      sessionToken,
      includedRegionCodes: ['gh'],
    });
    return {
      suggestions: (suggestions ?? [])
        .map((raw) => raw.placePrediction)
        .filter((p): p is NonNullable<RawSuggestion['placePrediction']> => Boolean(p))
        .map((p) => ({
          placeId: p.placeId ?? '',
          description: p.text?.toString() ?? '',
          primary: p.mainText?.toString() ?? p.text?.toString() ?? '',
          secondary: p.secondaryText?.toString() ?? '',
        }))
        .filter((p) => p.description.length > 0),
      unavailable: null,
    };
  } catch (error) {
    const reason = describeFailure(error);
    reportOnce(reason, error);
    return { suggestions: [], unavailable: reason };
  }
};
