import Alert from 'shared/components/Alert';

interface WindowNoticeProps {
  truncated: boolean;
  total: number;
  requestedSize: number;
  /** "transactions", "logbooks", "anomaly cases" — used in the sentence. */
  noun: string;
}

/**
 * What a fuel register is actually showing.
 *
 * The fuel collections take a `size` limit and return an unpaged array, so a register can only ever
 * show a window. When the service returns fewer records than the limit, that window *is* everything
 * matching the filter and the footer is the whole truth. When it returns exactly the limit, records
 * were almost certainly left behind — and the operator has to be told, because narrowing the filter
 * is the only way to see them.
 *
 * Recorded as gap 4. This banner disappears the day the fuel endpoints return `PageResponse<T>`.
 */
const WindowNotice = ({ truncated, total, requestedSize, noun }: WindowNoticeProps) => {
  if (!truncated) {
    return null;
  }
  return (
    <Alert
      variant="warning"
      title={`Showing the first ${total.toLocaleString()} ${noun} only`}
      className="mt-4"
    >
      The fuel service returns an unpaged window of up to {requestedSize.toLocaleString()} records
      and it came back full, so there are very likely more {noun} matching these filters. Narrow the
      site, status or date range to see them.
    </Alert>
  );
};

export default WindowNotice;
