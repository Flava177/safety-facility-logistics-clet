import { ReactNode } from 'react';
import Icon, { IconName } from './Icon';
import { cn } from './cn';

export type AlertVariant = 'success' | 'error' | 'warning' | 'info';

/**
 * Severity is carried by a 3px rule and the icon; the copy stays neutral dark.
 *
 * A tinted panel with tinted text puts two weak-contrast colours on top of each other — the reason
 * the previous alerts had to use pale backgrounds and still only just cleared 4.5:1. Here the text
 * is `gray-800` on a near-white tint (about 12:1), and the colour does its work at the edge, where
 * SC 1.4.11's 3:1 for non-text is the bar it has to clear rather than 4.5:1.
 */
const styles: Record<AlertVariant, { surface: string; rule: string; icon: string; name: IconName }> =
  {
    success: {
      surface: 'bg-success-50',
      rule: 'bg-success-700',
      icon: 'text-success-700',
      name: 'check-circle',
    },
    error: {
      surface: 'bg-error-50',
      rule: 'bg-error-800',
      icon: 'text-error-800',
      name: 'alert-circle',
    },
    warning: {
      surface: 'bg-warning-50',
      rule: 'bg-warning-700',
      icon: 'text-warning-700',
      name: 'alert-triangle',
    },
    info: { surface: 'bg-teal-50', rule: 'bg-teal-700', icon: 'text-teal-700', name: 'info' },
  };

interface AlertProps {
  variant: AlertVariant;
  title?: ReactNode;
  children?: ReactNode;
  /** Small print under the message — a correlation id, a code. */
  footnote?: ReactNode;
  action?: ReactNode;
  className?: string;
}

/** Inline message block. Used for form failures, empty-state guidance and blocker summaries. */
const Alert = ({ variant, title, children, footnote, action, className }: AlertProps) => {
  const style = styles[variant];

  return (
    <div
      className={cn('flex overflow-hidden rounded-lg', style.surface, className)}
      role={variant === 'error' ? 'alert' : 'status'}
    >
      <span className={cn('w-[3px] shrink-0', style.rule)} aria-hidden="true" />
      <div className="flex min-w-0 flex-1 items-start gap-3 p-3.5">
        <Icon name={style.name} size={18} className={cn('mt-0.5 shrink-0', style.icon)} />
        <div className="min-w-0 flex-1">
          {title && (
            <p className="text-theme-sm font-semibold break-words text-gray-900">{title}</p>
          )}
          {children && (
            <div className={cn('text-theme-sm break-words text-gray-700', title && 'mt-0.5')}>
              {children}
            </div>
          )}
          {footnote && <p className="mt-1 text-theme-xs text-gray-600">{footnote}</p>}
        </div>
        {action && <div className="shrink-0">{action}</div>}
      </div>
    </div>
  );
};

export default Alert;
