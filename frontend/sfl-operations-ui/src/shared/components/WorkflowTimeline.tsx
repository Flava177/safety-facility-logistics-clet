import { cn } from './cn';
import { formatDateTime } from './format';

export interface TimelineEntry {
  id: string;
  title: string;
  detail?: string | null;
  actor?: string | null;
  occurredAt: string;
  tone?: 'default' | 'accent' | 'danger';
}

interface WorkflowTimelineProps {
  entries: TimelineEntry[];
  emptyMessage?: string;
}

const dotTone = {
  default: 'bg-teal-700',
  accent: 'bg-gold-800',
  danger: 'bg-error-800',
} as const;

/**
 * Append-only history rendered as a timeline.
 *
 * The service exposes transitions and comments as an immutable sequence, so this never offers an
 * edit affordance — the record is the audit trail.
 */
const WorkflowTimeline = ({
  entries,
  emptyMessage = 'No recorded activity yet.',
}: WorkflowTimelineProps) => {
  if (entries.length === 0) {
    return <p className="text-theme-sm text-gray-600">{emptyMessage}</p>;
  }

  return (
    <ol className="relative">
      {entries.map((entry, index) => {
        const last = index === entries.length - 1;
        return (
          <li key={entry.id} className="flex gap-3">
            <div className="flex w-3 shrink-0 flex-col items-center">
              <span
                className={cn(
                  'mt-1.5 h-2.5 w-2.5 shrink-0 rounded-full',
                  dotTone[entry.tone ?? 'default'],
                )}
              />
              {!last && <span className="my-1 w-px flex-1 bg-gray-200" />}
            </div>

            <div className={cn('min-w-0 flex-1', last ? 'pb-0' : 'pb-5')}>
              <div className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-0.5">
                <p className="text-theme-sm font-semibold text-gray-900">{entry.title}</p>
                <time className="text-theme-xs text-gray-600">
                  {formatDateTime(entry.occurredAt)}
                </time>
              </div>
              {entry.detail && (
                <p className="mt-0.5 text-theme-sm break-words text-gray-700">{entry.detail}</p>
              )}
              {entry.actor && (
                <p className="mt-0.5 text-theme-xs text-gray-600">by {entry.actor}</p>
              )}
            </div>
          </li>
        );
      })}
    </ol>
  );
};

export default WorkflowTimeline;
