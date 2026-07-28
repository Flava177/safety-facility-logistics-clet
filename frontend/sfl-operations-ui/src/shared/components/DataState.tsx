import { ReactNode } from 'react';
import { FleetApiError, errorLabel } from 'shared/errors/FleetApiError';
import Alert from './Alert';
import Button from './Button';
import Icon from './Icon';

interface DataStateProps {
  loading: boolean;
  error?: FleetApiError;
  empty?: boolean;
  emptyTitle?: string;
  emptyHint?: string;
  onRetry?: () => void;
  minHeight?: number;
  children: ReactNode;
}

export const Spinner = ({ size = 26 }: { size?: number }) => (
  <svg
    className="animate-spin text-teal-700"
    width={size}
    height={size}
    viewBox="0 0 24 24"
    fill="none"
    aria-hidden="true"
  >
    <circle cx="12" cy="12" r="9.5" stroke="currentColor" strokeOpacity="0.2" strokeWidth="2.5" />
    <path d="M21.5 12A9.5 9.5 0 0 0 12 2.5" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" />
  </svg>
);

/**
 * The four states every screen owes the operator: loading, error, empty and content.
 *
 * Centralised so an unfinished screen cannot quietly render an empty table that looks like "no
 * vehicles" when the real answer is "the service is down". The error branch shows the service's own
 * message — SRS-defined wording — plus the correlation id, which is what support will ask for.
 */
const DataState = ({
  loading,
  error,
  empty,
  emptyTitle = 'Nothing to show',
  emptyHint,
  onRetry,
  minHeight = 220,
  children,
}: DataStateProps) => {
  if (loading) {
    return (
      <div
        className="flex flex-col items-center justify-center gap-3"
        style={{ minHeight }}
        role="status"
      >
        <Spinner />
        <p className="text-theme-sm text-gray-600">Loading…</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex items-center" style={{ minHeight }}>
        <Alert
          variant={error.isForbidden ? 'warning' : 'error'}
          title={errorLabel(error)}
          footnote={error.correlationId ? `Correlation ID: ${error.correlationId}` : undefined}
          action={
            onRetry ? (
              <Button size="sm" variant="outline" startIcon="refresh" onClick={onRetry}>
                Retry
              </Button>
            ) : undefined
          }
          className="w-full"
        >
          {error.message}
        </Alert>
      </div>
    );
  }

  if (empty) {
    return (
      <div
        className="flex flex-col items-center justify-center gap-1.5 px-6 text-center"
        style={{ minHeight }}
      >
        <span className="mb-1 flex h-11 w-11 items-center justify-center rounded-full bg-gray-100 text-gray-600">
          <Icon name="inbox" size={22} />
        </span>
        <p className="text-theme-sm font-semibold text-gray-900">{emptyTitle}</p>
        {emptyHint && <p className="max-w-sm text-theme-sm text-gray-600">{emptyHint}</p>}
      </div>
    );
  }

  return <>{children}</>;
};

export default DataState;
