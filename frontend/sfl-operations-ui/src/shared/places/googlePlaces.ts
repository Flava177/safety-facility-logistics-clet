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
 * still be able to record where they are. So every failure path — no key configured, script blocked,
 * offline, quota exhausted, API disabled on the key — resolves to `null` rather than throwing, and the
 * caller falls back to a plain text field. Autocomplete is a convenience that improves consistency; it
 * is never the only way to answer.
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

/** One place the user can pick. `description` is what is stored — the services take a string. */
export interface PlaceSuggestion {
  placeId: string;
  /** The full formatted description, e.g. "Tema Oil Refinery, Tema, Ghana". */
  description: string;
  /** The distinctive part, shown first. */
  primary: string;
  /** The disambiguating part — town, region — shown quieter. */
  secondary: string;
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
      } catch {
        resolve(null);
      }
    };

    if (window.google?.maps?.importLibrary) {
      void finish();
      return;
    }

    const script = document.createElement('script');
    // `loading=async` is Google's own recommendation and keeps the bootstrap off the critical path.
    script.src = `https://maps.googleapis.com/maps/api/js?key=${encodeURIComponent(apiKey)}&libraries=places&loading=async&v=weekly`;
    script.async = true;
    script.onerror = () => resolve(null);
    script.onload = () => void finish();
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
): Promise<PlaceSuggestion[]> => {
  const query = input.trim();
  if (query.length < 3) {
    // Below three characters the suggestions are noise and every keystroke is billable.
    return [];
  }
  const places = await loadPlaces();
  if (!places) {
    return [];
  }
  try {
    const { suggestions } = await places.AutocompleteSuggestion.fetchAutocompleteSuggestions({
      input: query,
      sessionToken,
      includedRegionCodes: ['gh'],
    });
    return (suggestions ?? [])
      .map((raw) => raw.placePrediction)
      .filter((p): p is NonNullable<RawSuggestion['placePrediction']> => Boolean(p))
      .map((p) => ({
        placeId: p.placeId ?? '',
        description: p.text?.toString() ?? '',
        primary: p.mainText?.toString() ?? p.text?.toString() ?? '',
        secondary: p.secondaryText?.toString() ?? '',
      }))
      .filter((p) => p.description.length > 0);
  } catch {
    return [];
  }
};
