import { DependencyList, useCallback, useEffect, useRef, useState } from 'react';
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
  const loadedOnce = useRef(false);

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
        loadedOnce.current = true;
        setData(result);
        setError(undefined);
      })
      .catch((cause: unknown) => {
        if (!active || (cause instanceof DOMException && cause.name === 'AbortError')) {
          return;
        }
        loadedOnce.current = true;
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
  }, [...deps, reloadToken]);

  return { data, loading, error, initialising: loading && !loadedOnce.current, refetch };
}
