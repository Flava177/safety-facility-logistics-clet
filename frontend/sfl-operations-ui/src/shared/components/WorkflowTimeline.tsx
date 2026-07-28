import { Box, Stack, Typography } from '@mui/material';
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

const toneColor = {
  default: 'primary.main',
  accent: 'secondary.main',
  danger: 'error.main',
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
    return (
      <Typography variant="body2" color="text.secondary">
        {emptyMessage}
      </Typography>
    );
  }

  return (
    <Stack spacing={0}>
      {entries.map((entry, index) => (
        <Stack key={entry.id} direction="row" spacing={1.5} sx={{ position: 'relative' }}>
          <Stack alignItems="center" sx={{ width: 18, flexShrink: 0 }}>
            <Box
              sx={{
                width: 10,
                height: 10,
                borderRadius: '50%',
                mt: 0.75,
                bgcolor: toneColor[entry.tone ?? 'default'],
                flexShrink: 0,
              }}
            />
            {index < entries.length - 1 && (
              <Box sx={{ flex: 1, width: '2px', bgcolor: 'divider', my: 0.5 }} />
            )}
          </Stack>

          <Box sx={{ pb: index < entries.length - 1 ? 2.5 : 0, minWidth: 0, flex: 1 }}>
            <Stack
              direction="row"
              spacing={1}
              alignItems="baseline"
              justifyContent="space-between"
              flexWrap="wrap"
              useFlexGap
            >
              <Typography variant="subtitle2" fontWeight={700}>
                {entry.title}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                {formatDateTime(entry.occurredAt)}
              </Typography>
            </Stack>
            {entry.detail && (
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.25 }}>
                {entry.detail}
              </Typography>
            )}
            {entry.actor && (
              <Typography variant="caption" color="text.disabled">
                by {entry.actor}
              </Typography>
            )}
          </Box>
        </Stack>
      ))}
    </Stack>
  );
};

export default WorkflowTimeline;
