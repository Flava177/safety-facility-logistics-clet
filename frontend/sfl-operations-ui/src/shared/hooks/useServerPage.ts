import { useEffect, useState } from 'react';
import { defaultPageSize } from 'shared/api/config';

/**
 * Page state for a server-paged register.
 *
 * It replaced the client-side window the fuel registers needed while that service returned bare,
 * capped arrays, and now serves dispatch and emergency for the same reason — all three page
 * properly. What is left to hold is which page and what size, plus the one rule that matters:
 * **a filter change resets to page 0**. Page four of the previous result set is meaningless against
 * the new one, and an empty table on a filter that did match records reads as "nothing found".
 *
 * This lives in `shared` because three modules use it. Its predecessor, `useClientWindow`, is gone:
 * every SFL collection now returns a paged envelope, so there is no window left to warn about.
 */
export interface ServerPage {
  page: number;
  size: number;
  setPage: (page: number) => void;
  setSize: (size: number) => void;
}

export function useServerPage(
  /** Any value that changes the query. Paging resets when it changes. */
  filterKey: string,
  initialSize: number = defaultPageSize,
): ServerPage {
  const [page, setPage] = useState(0);
  const [size, setSizeState] = useState(initialSize);

  /**
   * Reset to page 0 when the filter changes — during render, not in an effect.
   *
   * The effect version called `setPage(0)` on a filter change, which React 19 flags as a cascading
   * render: the table paints once with the old page against the new filter, then again. Adjusting
   * state during render is React's own documented pattern for exactly this, and it means the wrong
   * page is never painted at all.
   */
  const [lastFilterKey, setLastFilterKey] = useState(filterKey);
  if (filterKey !== lastFilterKey) {
    setLastFilterKey(filterKey);
    setPage(0);
  }

  return {
    page,
    size,
    setPage,
    setSize: (next: number) => {
      setSizeState(next);
      setPage(0);
    },
  };
}

/**
 * Clamps a page that has fallen off the end of a shrinking result set.
 *
 * Voiding the last record on the last page would otherwise strand the operator on a page the
 * service no longer has, which renders as an empty table rather than as the end of the register.
 */
export function useClampPage(
  page: number,
  totalPages: number | undefined,
  setPage: (page: number) => void,
): void {
  useEffect(() => {
    if (totalPages !== undefined && totalPages > 0 && page > totalPages - 1) {
      setPage(totalPages - 1);
    }
  }, [page, totalPages, setPage]);
}
