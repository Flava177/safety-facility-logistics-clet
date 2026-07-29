import { useEffect, useMemo, useState } from 'react';
import { defaultPageSize } from 'shared/api/config';
import { DEFAULT_WINDOW } from 'modules/fuel/api/fuelApi';

/**
 * Paging over the window the fuel service returned, because the fuel service does not page.
 *
 * Every fuel collection returns a bare `List<T>` capped by `size`; there is no `page`, no
 * `totalElements` and no `PageResponse` envelope (gap 4). Two things follow, and both are the
 * point of this hook.
 *
 * First, the register still needs a footer an operator can navigate, so the rows are sliced here
 * and handed to `DataTable` with a total that is honestly the size of *the window* — not of the
 * register. Second, and more important: when the service returns exactly the limit it was asked
 * for, the window is almost certainly truncated, and a table that says "1–25 of 200" while the site
 * holds four hundred transactions is the kind of thing an operator discovers when a record is
 * missing. `truncated` is what the screens use to say so out loud.
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
   * the filtered list would silently switch the warning off exactly when it is most needed.
   * Defaults to the list's own length for callers that do no client-side filtering.
   */
  serverCount?: number,
  requestedSize: number = DEFAULT_WINDOW,
): ClientWindow<T> {
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(defaultPageSize);

  // A filter change must land on page 0: page 4 of the previous result set is meaningless against
  // the new one, and an empty table on a filter that matched records reads as "nothing found".
  useEffect(() => {
    setPage(0);
  }, [filterKey]);

  const records = useMemo(() => all ?? [], [all]);
  const total = records.length;

  // Deleting or voiding the last row of the last page would otherwise strand the operator on a page
  // that no longer exists.
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
