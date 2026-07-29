import { useEffect, useState } from 'react';
import { DEFAULT_PAGE_SIZE } from 'modules/fuel/api/fuelApi';

/**
 * Page state for a server-paged fuel register.
 *
 * It replaces the client-side window the fuel registers needed while the service returned bare,
 * capped arrays. That is gone: the collections page properly now, so the only thing left to hold is
 * which page and what size, plus the one rule that matters — **a filter change resets to page 0**.
 * Page four of the previous result set is meaningless against the new one, and an empty table on a
 * filter that did match records reads as "nothing found".
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
  initialSize: number = DEFAULT_PAGE_SIZE,
): ServerPage {
  const [page, setPage] = useState(0);
  const [size, setSizeState] = useState(initialSize);

  useEffect(() => {
    setPage(0);
  }, [filterKey]);

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
