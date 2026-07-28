import {
  PropsWithChildren,
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react';
import { describeError, isFleetApiError } from 'shared/errors/FleetApiError';
import Icon, { IconName } from './Icon';
import { cn } from './cn';

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

const toneStyles: Record<Severity, { bar: string; icon: string; name: IconName }> = {
  success: { bar: 'bg-success-700', icon: 'text-success-700', name: 'check-circle' },
  error: { bar: 'bg-error-800', icon: 'text-error-800', name: 'alert-circle' },
  warning: { bar: 'bg-warning-700', icon: 'text-warning-700', name: 'alert-triangle' },
  info: { bar: 'bg-teal-700', icon: 'text-teal-700', name: 'info' },
};

/**
 * Global, non-blocking feedback for anything that is not a field error.
 *
 * Field-level problems belong on the field (see `useFleetForm`); this is for the rest — a
 * successful transition, an authorisation refusal, a service that cannot be reached. Failures stay
 * on screen twice as long as confirmations, because they are the ones worth reading.
 */
export const NotifierProvider = ({ children }: PropsWithChildren) => {
  const [current, setCurrent] = useState<Notification | undefined>(undefined);

  const push = useCallback((severity: Severity, message: string, detail?: string) => {
    setCurrent({ key: Date.now(), severity, message, detail });
  }, []);

  useEffect(() => {
    if (!current) {
      return undefined;
    }
    const timer = window.setTimeout(
      () => setCurrent(undefined),
      current.severity === 'error' ? 10000 : 5000,
    );
    return () => window.clearTimeout(timer);
  }, [current]);

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

  const style = current ? toneStyles[current.severity] : undefined;

  return (
    <NotifierContext.Provider value={value}>
      {children}

      <div
        aria-live="polite"
        className="pointer-events-none fixed right-4 bottom-4 z-999999 flex w-full max-w-sm flex-col gap-2"
      >
        {current && style && (
          <div
            key={current.key}
            className="pointer-events-auto flex overflow-hidden rounded-lg border border-gray-200 bg-white shadow-theme-lg"
          >
            <span className={cn('w-[3px] shrink-0', style.bar)} aria-hidden="true" />
            <div className="flex flex-1 items-start gap-3 p-3.5">
              <Icon name={style.name} size={19} className={cn('mt-0.5 shrink-0', style.icon)} />
              <div className="min-w-0 flex-1">
                <p className="text-theme-sm font-semibold break-words text-gray-900">
                  {current.message}
                </p>
                {current.detail && (
                  <p className="mt-0.5 text-theme-xs break-words text-gray-600">
                    {current.detail}
                  </p>
                )}
              </div>
              <button
                type="button"
                onClick={() => setCurrent(undefined)}
                aria-label="Dismiss"
                className="flex h-6 w-6 shrink-0 items-center justify-center rounded text-gray-600 transition-colors hover:bg-gray-100 hover:text-gray-900"
              >
                <Icon name="close" size={15} />
              </button>
            </div>
          </div>
        )}
      </div>
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
