import { useCallback, useEffect, useState } from 'react';

/**
 * The handful of values this operator last typed into one field, at one site.
 *
 * <p>Origin and destination are free text on both the trip and the driver-logbook forms, and the same
 * dozen places get retyped all day - a centre, a warehouse, the HQ gate. Nothing in the API offers a
 * list of them, because they are not reference data: a driver may legitimately go somewhere new, and
 * the SRS keeps these fields open for exactly that reason. So this remembers rather than constrains,
 * and it feeds a `<datalist>`, which suggests without restricting.
 *
 * <h2>Why it is local, and why that is the right call here</h2>
 *
 * <p>This is browser-local and per-actor. It is a typing aid with no authority: it never travels to
 * the service, it is not evidence, and nothing is decided by it. That also keeps it inside the site
 * scoping the platform enforces everywhere else - the key carries the site, so a manager scoped to
 * four centres is not offered one centre's routes while filing against another.
 *
 * <p>Values are ordered most-recent-first, de-duplicated case-insensitively, and capped, so the list
 * stays short enough to scan. A corrupt or absent store is not an error state - the field simply has
 * no suggestions, which is where it started.
 */

const LIMIT = 8;
const PREFIX = 'sfl.recent';

const storageKey = (field: string, scope: string | undefined) =>
  `${PREFIX}.${field}.${(scope || 'all').toUpperCase()}`;

const read = (key: string): string[] => {
  try {
    const raw = globalThis.localStorage?.getItem(key);
    if (!raw) {
      return [];
    }
    const parsed: unknown = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed.filter((v): v is string => typeof v === 'string') : [];
  } catch {
    // Private-mode storage, a quota refusal or a value someone hand-edited. None of them are worth
    // surfacing for a typing aid.
    return [];
  }
};

export interface RecentValues {
  /** Most recent first. Safe to spread straight into `suggestions`. */
  values: string[];
  /** Records one value. Blank input is ignored, so an untouched optional field never pollutes it. */
  remember: (value: string) => void;
}

export const useRecentValues = (field: string, scope?: string): RecentValues => {
  const key = storageKey(field, scope);
  const [values, setValues] = useState<string[]>(() => read(key));

  // Changing site must swap the list, not merge it - the whole point of scoping the key.
  useEffect(() => {
    setValues(read(key));
  }, [key]);

  const remember = useCallback(
    (value: string) => {
      const trimmed = value.trim();
      if (!trimmed) {
        return;
      }
      // Read, compute and write **outside** the state updater, then set state from the result.
      //
      // The first version did the write inside `setValues`, and it silently never persisted anything.
      // Every caller is a dialog that closes on success - `remember(...)` then `onClose()` - so the
      // component unmounted before React ran the updater, and the write went with it. A state updater
      // must be pure for exactly this reason: React decides when, whether and how many times to call
      // it, and in StrictMode calls it twice.
      //
      // Reading current storage rather than the `values` state also makes two dialogs open at once
      // converge instead of overwriting each other.
      const current = read(key);
      const next = [
        trimmed,
        ...current.filter((existing) => existing.toLowerCase() !== trimmed.toLowerCase()),
      ].slice(0, LIMIT);
      try {
        globalThis.localStorage?.setItem(key, JSON.stringify(next));
      } catch {
        // Keep the in-memory list for this session even when the write is refused.
      }
      setValues(next);
    },
    [key],
  );

  return { values, remember };
};
