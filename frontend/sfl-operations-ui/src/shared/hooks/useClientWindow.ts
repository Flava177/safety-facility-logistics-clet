import { useEffect, useMemo, useState } from 'react';
import { defaultPageSize } from 'shared/api/config';

/**
 * Paging over the window a service returned, for the services that do not page.
 *
 * Some SFL collections return a bare `List<T>` capped by a `size` limit: there is no `page`, no
 * `totalElements` and no `PageResponse` envelope. Dispatch (S171) and emergency notification (S174)
 * are both like this today. Two things follow, and both are the point of this hook.
 *
 * The register still needs a footer an operator can navigate, so the rows are sliced here and handed
 * to `DataTable` with a total that is honestly the size of *the window*, not of the register. And
 * when the service returns exactly the limit it was asked for, the window is almost certainly
 * truncated — a table reading "1–25 of 200" while the site holds four hundred records is the kind
 * of thing an operator discovers when something is missing. `truncated` is what the screens use to
 * say so, through `WindowNotice`.
 *
 * The fuel module had exactly this and no longer needs it: its collections were moved to a real
 * paged envelope. When the other endpoints follow, their callers go the same way.
 */
export interface ClientWindow<T> {
  rows: T[];
  page: number;
  pageSize: number;
  /** The size of the returned window, which is what the footer counts. */
  total: number;
  /** The service returned exactly the limit it was given, so records were probably left behind. */
  truncated: boolean;
  setPage: (page: number) => void;
  setPageSize: (size: number) => void;
}

export function useClientWindow<T>(
  all: T[] | undefined,
  /** Any value that changes the query. Paging resets to the first page when it does. */
  filterKey: string,
  /**
   * How many records the **service** returned, before any client-side filtering.
   *
   * Truncation is a fact about the response, not about the table. A client-side filter that cuts a
   * full window of 200 down to 12 rows has not made the window any less truncated — reading it off
   * the filtered list would switch the warning off exactly when it is most needed.
   */
  serverCount?: number,
  requestedSize = 200,
): ClientWindow<T> {
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(defaultPageSize);

  // A filter change must land on page 0: page four of the previous result set is meaningless
  // against the new one, and an empty table reads as "nothing found".
  useEffect(() => {
    setPage(0);
  }, [filterKey]);

  const records = useMemo(() => all ?? [], [all]);
  const total = records.length;

  useEffect(() => {
    const lastPage = Math.max(0, Math.ceil(total / pageSize) - 1);
    if (page > lastPage) {
      setPage(lastPage);
    }
  }, [total, pageSize, page]);

  const rows = useMemo(
    () => records.slice(page * pageSize, page * pageSize + pageSize),
    [records, page, pageSize],
  );

  return {
    rows,
    page,
    pageSize,
    total,
    truncated: (serverCount ?? total) >= requestedSize,
    setPage,
    setPageSize: (size: number) => {
      setPageSize(size);
      setPage(0);
    },
  };
}
