import { Alert, Box, Stack, Typography } from '@mui/material';
import { BlockerResponse } from 'modules/fleet/api/dto';
import { humanise } from 'modules/fleet/api/enums';
import StatusChip from './StatusChip';

interface BlockerListProps {
  blockers: BlockerResponse[];
  /** Shown when there is nothing blocking — silence would read as "not checked". */
  clearMessage?: string;
  dense?: boolean;
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
  dense,
}: BlockerListProps) => {
  if (blockers.length === 0) {
    return (
      <Alert severity="success" variant="outlined">
        {clearMessage}
      </Alert>
    );
  }

  const blocking = blockers.filter((blocker) => blocker.severity === 'BLOCKING');
  const warnings = blockers.filter((blocker) => blocker.severity !== 'BLOCKING');

  return (
    <Stack spacing={1}>
      {blocking.length > 0 && (
        <Alert severity="error" variant="outlined" sx={{ py: dense ? 0.5 : 1 }}>
          <Typography variant="subtitle2" fontWeight={700} sx={{ mb: 0.5 }}>
            {blocking.length} blocking {blocking.length === 1 ? 'issue' : 'issues'} — assignment
            will be refused
          </Typography>
          <Stack spacing={0.75}>
            {blocking.map((blocker) => (
              <Box key={`${blocker.code}-${blocker.message}`}>
                <Stack
                  direction="row"
                  spacing={0.75}
                  alignItems="center"
                  flexWrap="wrap"
                  useFlexGap
                >
                  <StatusChip
                    value={blocker.severity}
                    label={humanise(blocker.code)}
                    tone="blocked"
                  />
                </Stack>
                <Typography variant="body2" sx={{ mt: 0.25 }}>
                  {blocker.message}
                </Typography>
              </Box>
            ))}
          </Stack>
        </Alert>
      )}

      {warnings.length > 0 && (
        <Alert severity="warning" variant="outlined" sx={{ py: dense ? 0.5 : 1 }}>
          <Typography variant="subtitle2" fontWeight={700} sx={{ mb: 0.5 }}>
            {warnings.length} advisory {warnings.length === 1 ? 'warning' : 'warnings'}
          </Typography>
          <Stack spacing={0.75}>
            {warnings.map((blocker) => (
              <Box key={`${blocker.code}-${blocker.message}`}>
                <StatusChip
                  value={blocker.severity}
                  label={humanise(blocker.code)}
                  tone="caution"
                />
                <Typography variant="body2" sx={{ mt: 0.25 }}>
                  {blocker.message}
                </Typography>
              </Box>
            ))}
          </Stack>
        </Alert>
      )}
    </Stack>
  );
};

export default BlockerList;
