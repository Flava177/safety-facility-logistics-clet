import { BlockerResponse } from 'modules/fleet/api/dto';
import { humanise } from 'modules/fleet/api/enums';
import Alert from './Alert';
import StatusChip from './StatusChip';

interface BlockerListProps {
  blockers: BlockerResponse[];
  /** Shown when there is nothing blocking — silence would read as "not checked". */
  clearMessage?: string;
}

/**
 * Readiness and eligibility blockers, exactly as the service returned them.
 *
 * The service is explicit that each blocker carries a machine-readable code *and* human wording;
 * both are shown. Blocking entries are separated from warnings because only the first kind will
 * cause the submission to be refused.
 */
const BlockerList = ({
  blockers,
  clearMessage = 'No blockers. This assignment can proceed.',
}: BlockerListProps) => {
  if (blockers.length === 0) {
    return <Alert variant="success">{clearMessage}</Alert>;
  }

  const blocking = blockers.filter((blocker) => blocker.severity === 'BLOCKING');
  const warnings = blockers.filter((blocker) => blocker.severity !== 'BLOCKING');

  return (
    <div className="space-y-2.5">
      {blocking.length > 0 && (
        <Alert
          variant="error"
          title={`${blocking.length} blocking ${
            blocking.length === 1 ? 'issue' : 'issues'
          } — the service will refuse this`}
        >
          <ul className="mt-1.5 space-y-2">
            {blocking.map((blocker) => (
              <li key={`${blocker.code}-${blocker.message}`}>
                <StatusChip value={blocker.severity} label={humanise(blocker.code)} tone="blocked" />
                <p className="mt-1 text-theme-sm text-gray-700">{blocker.message}</p>
              </li>
            ))}
          </ul>
        </Alert>
      )}

      {warnings.length > 0 && (
        <Alert
          variant="warning"
          title={`${warnings.length} advisory ${warnings.length === 1 ? 'warning' : 'warnings'}`}
        >
          <ul className="mt-1.5 space-y-2">
            {warnings.map((blocker) => (
              <li key={`${blocker.code}-${blocker.message}`}>
                <StatusChip value={blocker.severity} label={humanise(blocker.code)} tone="caution" />
                <p className="mt-1 text-theme-sm text-gray-700">{blocker.message}</p>
              </li>
            ))}
          </ul>
        </Alert>
      )}
    </div>
  );
};

export default BlockerList;
