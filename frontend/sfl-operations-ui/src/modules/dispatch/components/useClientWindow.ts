/**
 * The dispatch registers' paging helper — now a thin binding over the shared one.
 *
 * S174 needed the same behaviour, because its collections are unpaged for the same reason
 * dispatch's are. Rather than let a second copy drift, the implementation moved to
 * `shared/hooks/useClientWindow`. This file stays so the dispatch screens keep importing from their
 * own module, and so the module's default window size is applied in one place rather than at eleven
 * call sites.
 */
import { DEFAULT_WINDOW } from 'modules/dispatch/api/dispatchApi';
import {
  useClientWindow as useSharedClientWindow,
  type ClientWindow,
} from 'shared/hooks/useClientWindow';

export type { ClientWindow };

export function useClientWindow<T>(
  all: T[] | undefined,
  /** Any value that changes the query. Paging resets to the first page when it does. */
  filterKey: string,
  /** How many records the service returned, before any client-side filtering. */
  serverCount?: number,
  requestedSize: number = DEFAULT_WINDOW,
): ClientWindow<T> {
  return useSharedClientWindow(all, filterKey, serverCount, requestedSize);
}
