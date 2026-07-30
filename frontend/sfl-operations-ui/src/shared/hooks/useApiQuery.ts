import { DependencyList, useCallback, useEffect, useState } from 'react';
import { FleetApiError } from 'shared/errors/FleetApiError';

export interface ApiQueryState<T> {
  data: T | undefined;
  loading: boolean;
  error: FleetApiError | undefined;
  /** `true` for the first load only, so a refresh does not blank an already-populated table. */
  initialising: boolean;
  refetch: () => void;
}

/**
 * Minimal read hook: fetch on mount and whenever `deps` change, with cancellation.
 *
 * Deliberately small — the app has no data-fetching library, and every screen needs the same four
 * states (loading, empty, error, success). Keeping refetching explicit also keeps mutations honest:
 * screens refetch after a write rather than guessing the new server state.
 */
export function useApiQuery<T>(
  fetcher: (signal: AbortSignal) => Promise<T>,
  deps: DependencyList,
): ApiQueryState<T> {
  const [data, setData] = useState<T | undefined>(undefined);
  const [error, setError] = useState<FleetApiError | undefined>(undefined);
  const [loading, setLoading] = useState(true);
  const [reloadToken, setReloadToken] = useState(0);
  /**
   * Whether a first response has ever landed — state, not a ref.
   *
   * It was a ref, and `initialising` read `loadedOnce.current` during render. That is exactly
   * what `react-hooks/refs` forbids: a ref read during render is invisible to React, so under
   * concurrent rendering the value can differ between the render that computed it and the one
   * that commits. It drives a spinner, so it has to be state.
   */
  const [loadedOnce, setLoadedOnce] = useState(false);

  const refetch = useCallback(() => setReloadToken((token) => token + 1), []);

  useEffect(() => {
    const controller = new AbortController();
    let active = true;

    setLoading(true);
    fetcher(controller.signal)
      .then((result) => {
        if (!active) {
          return;
        }
        setLoadedOnce(true);
        setData(result);
        setError(undefined);
      })
      .catch((cause: unknown) => {
        if (!active || (cause instanceof DOMException && cause.name === 'AbortError')) {
          return;
        }
        setLoadedOnce(true);
        setError(
          cause instanceof FleetApiError
            ? cause
            : FleetApiError.transport('The request could not be completed.'),
        );
      })
      .finally(() => {
        if (active) {
          setLoading(false);
        }
      });

    return () => {
      active = false;
      controller.abort();
    };
    // The dependency array is the caller's, by design: this hook exists to re-run a fetch when
    // the caller's inputs change, and it cannot know what those are. `fetcher` is deliberately
    // absent — it is an inline closure at every call site and would re-run on every render.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, reloadToken]);

  return { data, loading, error, initialising: loading && !loadedOnce, refetch };
}
