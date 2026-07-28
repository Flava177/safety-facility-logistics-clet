import { ReactNode } from 'react';
import Icon, { IconName } from './Icon';
import { cn } from './cn';

export type StatTone = 'neutral' | 'good' | 'caution' | 'critical' | 'accent';

/**
 * Tone touches the icon plate and the caption — never the figure.
 *
 * A KPI row where every number is a different colour is the loudest thing a dashboard can do, and
 * it makes the two figures that need attention indistinguishable from the six that do not. So the
 * value stays near-black and the caption underneath is where a measure says it is in trouble, in
 * words as well as colour.
 *
 * `neutral` is CLET Gold rather than grey: it is the resting state for most of the row, and a row
 * of grey plates reads as unfinished rather than calm.
 */
const tones: Record<StatTone, { plate: string; icon: string; caption: string }> = {
  neutral: { plate: 'bg-gold-50', icon: 'text-gold-800', caption: 'text-gray-500' },
  accent: { plate: 'bg-gold-100', icon: 'text-gold-900', caption: 'text-gold-900' },
  good: { plate: 'bg-success-50', icon: 'text-success-700', caption: 'text-success-800' },
  caution: { plate: 'bg-warning-50', icon: 'text-warning-700', caption: 'text-warning-800' },
  critical: { plate: 'bg-error-50', icon: 'text-error-800', caption: 'text-error-800' },
};

interface StatCardProps {
  label: string;
  value: number | string;
  icon: IconName;
  tone?: StatTone;
  caption?: ReactNode;
  onClick?: () => void;
}

/**
 * A headline figure.
 *
 * Wide and shallow on purpose. Eight of these open the dashboard, and a tall card pushes the charts
 * and the exception lists — the things an operator actually acts on — below the fold.
 */
const StatCard = ({ label, value, icon, tone = 'neutral', caption, onClick }: StatCardProps) => {
  const palette = tones[tone];

  const inner = (
    <>
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0">
          <p className="truncate text-theme-sm font-medium text-gray-600">{label}</p>
          <p className="mt-1.5 text-title-sm leading-none font-bold text-gray-900 tabular-nums">
            {value}
          </p>
        </div>
        <span
          className={cn(
            'flex h-10 w-10 shrink-0 items-center justify-center rounded-lg',
            palette.plate,
            palette.icon,
          )}
          aria-hidden="true"
        >
          <Icon name={icon} size={19} />
        </span>
      </div>

      <div className="mt-3 flex flex-wrap items-center justify-between gap-x-3 gap-y-1">
        {caption ? (
          <p className={cn('text-theme-sm', palette.caption)}>{caption}</p>
        ) : (
          <span />
        )}
        {onClick && (
          <span className="inline-flex items-center gap-1 text-theme-sm font-medium text-teal-700 group-hover:underline">
            View records
            <Icon name="chevron-right" size={14} aria-hidden="true" />
          </span>
        )}
      </div>
    </>
  );

  if (!onClick) {
    return (
      <div className="flex flex-col rounded-lg border border-gray-200 bg-white px-5 py-4">
        {inner}
      </div>
    );
  }

  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={`${label}: ${value}. Show the records behind this figure.`}
      className="group flex w-full flex-col rounded-lg border border-gray-200 bg-white px-5 py-4 text-left transition-colors hover:border-gold-300 hover:bg-gold-25"
    >
      {inner}
    </button>
  );
};

export default StatCard;
