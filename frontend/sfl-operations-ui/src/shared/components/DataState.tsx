import { ReactNode } from 'react';
import { Alert, AlertTitle, Box, Button, CircularProgress, Stack, Typography } from '@mui/material';
import { FleetApiError } from 'shared/errors/FleetApiError';

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
      <Stack alignItems="center" justifyContent="center" sx={{ minHeight }} spacing={1.5}>
        <CircularProgress size={28} color="secondary" />
        <Typography variant="body2" color="text.secondary">
          Loading…
        </Typography>
      </Stack>
    );
  }

  if (error) {
    return (
      <Box sx={{ minHeight, display: 'flex', alignItems: 'center' }}>
        <Alert
          severity={error.isForbidden ? 'warning' : 'error'}
          sx={{ width: 1 }}
          action={
            onRetry ? (
              <Button color="inherit" size="small" onClick={onRetry}>
                Retry
              </Button>
            ) : undefined
          }
        >
          <AlertTitle sx={{ mb: 0.5 }}>{error.code.replace(/_/g, ' ')}</AlertTitle>
          <Typography variant="body2">{error.message}</Typography>
          {error.correlationId && (
            <Typography variant="caption" color="text.secondary">
              Correlation ID: {error.correlationId}
            </Typography>
          )}
        </Alert>
      </Box>
    );
  }

  if (empty) {
    return (
      <Stack alignItems="center" justifyContent="center" sx={{ minHeight }} spacing={0.5}>
        <Typography variant="subtitle1" fontWeight={600}>
          {emptyTitle}
        </Typography>
        {emptyHint && (
          <Typography variant="body2" color="text.secondary" textAlign="center">
            {emptyHint}
          </Typography>
        )}
      </Stack>
    );
  }

  return <>{children}</>;
};

export default DataState;
