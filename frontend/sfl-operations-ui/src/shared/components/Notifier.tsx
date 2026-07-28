import {
  PropsWithChildren,
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
} from 'react';
import { Alert, AlertTitle, Snackbar, Typography } from '@mui/material';
import { describeError, isFleetApiError } from 'shared/errors/FleetApiError';

type Severity = 'success' | 'error' | 'warning' | 'info';

interface Notification {
  key: number;
  severity: Severity;
  message: string;
  detail?: string;
}

interface NotifierContextValue {
  notifySuccess: (message: string, detail?: string) => void;
  notifyInfo: (message: string, detail?: string) => void;
  /** Renders the service's message plus its correlation id, which support will ask for. */
  notifyError: (error: unknown, fallback?: string) => void;
}

const NotifierContext = createContext<NotifierContextValue | undefined>(undefined);

/**
 * Global, non-blocking feedback for anything that is not a field error.
 *
 * Field-level problems belong on the field (see `useFleetForm`); this is for the rest — a
 * successful transition, an authorisation refusal, a service that cannot be reached.
 */
export const NotifierProvider = ({ children }: PropsWithChildren) => {
  const [current, setCurrent] = useState<Notification | undefined>(undefined);

  const push = useCallback((severity: Severity, message: string, detail?: string) => {
    setCurrent({ key: Date.now(), severity, message, detail });
  }, []);

  const value = useMemo<NotifierContextValue>(
    () => ({
      notifySuccess: (message, detail) => push('success', message, detail),
      notifyInfo: (message, detail) => push('info', message, detail),
      notifyError: (error, fallback) => {
        const message = fallback ?? describeError(error);
        const detail = isFleetApiError(error)
          ? [error.code, error.correlationId ? `Correlation ID ${error.correlationId}` : null]
              .filter(Boolean)
              .join(' · ')
          : undefined;
        push(isFleetApiError(error) && error.isForbidden ? 'warning' : 'error', message, detail);
      },
    }),
    [push],
  );

  return (
    <NotifierContext.Provider value={value}>
      {children}
      <Snackbar
        key={current?.key}
        open={Boolean(current)}
        autoHideDuration={current?.severity === 'error' ? 10000 : 5000}
        onClose={() => setCurrent(undefined)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
      >
        <Alert
          severity={current?.severity ?? 'info'}
          variant="filled"
          onClose={() => setCurrent(undefined)}
          sx={{ maxWidth: 460 }}
        >
          <AlertTitle sx={{ mb: current?.detail ? 0.25 : 0 }}>{current?.message}</AlertTitle>
          {current?.detail && (
            <Typography variant="caption" sx={{ opacity: 0.85 }}>
              {current.detail}
            </Typography>
          )}
        </Alert>
      </Snackbar>
    </NotifierContext.Provider>
  );
};

export const useNotifier = (): NotifierContextValue => {
  const context = useContext(NotifierContext);
  if (!context) {
    throw new Error('useNotifier must be used inside a NotifierProvider');
  }
  return context;
};
