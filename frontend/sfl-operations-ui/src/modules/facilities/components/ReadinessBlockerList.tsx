import Alert from 'shared/components/Alert';
import Button from 'shared/components/Button';
import StatusChip from 'shared/components/StatusChip';
import type { ReadinessBlocker } from '../api/dto';
import { humaniseCode, relativeTime, severityTone } from './facilitiesFormat';

interface ReadinessBlockerListProps {
  blockers: ReadinessBlocker[];
  /** Shown when nothing is open — silence would read as "not checked" rather than "clear". */
  clearMessage?: string;
  /** Omitted when the actor cannot resolve, so no button is offered that would be refused. */
  onResolve?: (blocker: ReadinessBlocker) => void;
}

/**
 * The open blockers on a space, worst first.
 *
 * A sibling of the shared `BlockerList` rather than a reuse of it: that component is bound to the
 * fleet's two-value vocabulary (`BLOCKING` / everything else) and to `modules/fleet/api/dto`. S152
 * has four severities and the distinction between them is the whole model — a critical blocker
 * forbids READY, a major one degrades, an advisory one is noted and changes nothing. Flattening that
 * into "blocking or not" would throw away the information an operator is here for.
 *
 * Critical blockers are separated and labelled with what they prevent, because the number that
 * matters on this screen is not "how many problems" but "how many of them stop this hall being used".
 */
const ReadinessBlockerList = ({
  blockers,
  clearMessage = 'No open blockers. This space is clear.',
  onResolve,
}: ReadinessBlockerListProps) => {
  const open = blockers.filter((blocker) => !blocker.resolved);

  if (open.length === 0) {
    return <Alert variant="success">{clearMessage}</Alert>;
  }

  const critical = open.filter((blocker) => blocker.severity === 'CRITICAL');
  const degrading = open.filter(
    (blocker) => blocker.severity === 'MAJOR' || blocker.severity === 'MINOR',
  );
  const advisory = open.filter((blocker) => blocker.severity === 'ADVISORY');

  const row = (blocker: ReadinessBlocker) => (
    <li key={blocker.id} className="flex flex-wrap items-start justify-between gap-2">
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <StatusChip value={blocker.severity} tone={severityTone(blocker.severity)} />
          <span className="text-theme-xs text-gray-500">
            {humaniseCode(blocker.source)}
            {blocker.sourceReference ? ` · ${blocker.sourceReference}` : ''} · raised{' '}
            {relativeTime(blocker.raisedAt)} by {blocker.raisedBy}
          </span>
        </div>
        <p className="mt-1 text-theme-sm break-words text-gray-700">{blocker.description}</p>
      </div>
      {onResolve && (
        <Button variant="outline" size="sm" onClick={() => onResolve(blocker)}>
          Resolve
        </Button>
      )}
    </li>
  );

  return (
    <div className="space-y-2.5">
      {critical.length > 0 && (
        <Alert
          variant="error"
          title={`${critical.length} critical blocker${critical.length === 1 ? '' : 's'} — this space cannot be marked ready`}
        >
          <ul className="mt-1.5 space-y-3">{critical.map(row)}</ul>
        </Alert>
      )}

      {degrading.length > 0 && (
        <Alert
          variant="warning"
          title={`${degrading.length} blocker${degrading.length === 1 ? '' : 's'} degrading this space`}
        >
          <ul className="mt-1.5 space-y-3">{degrading.map(row)}</ul>
        </Alert>
      )}

      {advisory.length > 0 && (
        <Alert
          variant="info"
          title={`${advisory.length} advisory note${advisory.length === 1 ? '' : 's'}`}
        >
          <ul className="mt-1.5 space-y-3">{advisory.map(row)}</ul>
        </Alert>
      )}
    </div>
  );
};

export default ReadinessBlockerList;
