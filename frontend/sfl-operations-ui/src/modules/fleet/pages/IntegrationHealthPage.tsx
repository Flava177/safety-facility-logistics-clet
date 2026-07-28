import { useState } from 'react';
import { Alert, Box, Button, Divider, Stack, Typography } from '@mui/material';
import { integrationsApi } from 'modules/fleet/api/fleetApi';
import IndicatorTile from 'modules/fleet/components/IndicatorTile';
import DataState from 'shared/components/DataState';
import { useNotifier } from 'shared/components/Notifier';
import PageHeader from 'shared/components/PageHeader';
import SectionCard from 'shared/components/SectionCard';
import StatusChip from 'shared/components/StatusChip';
import { TextInput } from 'shared/components/fields';
import { formatDateTime } from 'shared/components/format';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { fleetPaths } from 'shared/layout/navigation';
import IconifyIcon from 'components/base/IconifyIcon';

/** Reads a display value out of a loosely-typed integration message summary. */
const field = (summary: Record<string, unknown>, ...keys: string[]): string | undefined => {
  for (const key of keys) {
    const value = summary[key];
    if (typeof value === 'string' || typeof value === 'number') {
      return String(value);
    }
  }
  return undefined;
};

/**
 * Telematics and integration intake health.
 *
 * The service exposes a health projection and a replay operation, but no inbox search — so recent
 * messages come from the health projection itself, and replay takes a message id. Dead letters are
 * called out because they are the only class of message that will not resolve on its own.
 */
const IntegrationHealthPage = () => {
  const { notifyError, notifySuccess } = useNotifier();
  const [replayId, setReplayId] = useState('');
  const [replaying, setReplaying] = useState(false);

  const health = useApiQuery((signal) => integrationsApi.health(signal), []);

  const replay = async () => {
    if (!replayId.trim()) {
      return;
    }
    setReplaying(true);
    try {
      await integrationsApi.replay(replayId.trim());
      notifySuccess('Replay accepted.');
      setReplayId('');
      health.refetch();
    } catch (error) {
      notifyError(error);
    } finally {
      setReplaying(false);
    }
  };

  const recent = (health.data?.recentMessages ?? []) as Record<string, unknown>[];

  return (
    <Box>
      <PageHeader
        title="Integration health"
        subtitle="Signed telematics intake: what has been processed, rejected or dead-lettered."
        crumbs={[{ label: 'Fleet', to: fleetPaths.dashboard }, { label: 'Integration health' }]}
        actions={
          <Button
            variant="soft"
            color="neutral"
            onClick={health.refetch}
            startIcon={<IconifyIcon icon="material-symbols:refresh-rounded" />}
          >
            Refresh
          </Button>
        }
        meta={
          health.data && (
            <Typography variant="caption" color="text.secondary">
              Checked {formatDateTime(health.data.checkedAt)}
            </Typography>
          )
        }
      />

      <DataState
        loading={health.initialising}
        error={health.error}
        onRetry={health.refetch}
        minHeight={280}
      >
        {health.data && (
          <Stack spacing={2.5}>
            {health.data.deadLetterMessages > 0 && (
              <Alert severity="error">
                {health.data.deadLetterMessages} message
                {health.data.deadLetterMessages === 1 ? '' : 's'} require replay or operator review.
                Until they are cleared, vehicle movement data may be stale.
              </Alert>
            )}

            <Box
              sx={{
                display: 'grid',
                gap: 2,
                gridTemplateColumns: {
                  xs: 'repeat(1, minmax(0, 1fr))',
                  sm: 'repeat(3, minmax(0, 1fr))',
                },
              }}
            >
              <IndicatorTile
                label="Processed"
                value={health.data.processedMessages}
                icon="material-symbols:check-circle-outline-rounded"
                tone="good"
                caption="Accepted and applied"
              />
              <IndicatorTile
                label="Rejected"
                value={health.data.rejectedMessages}
                icon="material-symbols:error-outline-rounded"
                tone={health.data.rejectedMessages > 0 ? 'caution' : 'good'}
                caption="Signature, allowlist or schema"
              />
              <IndicatorTile
                label="Dead letters"
                value={health.data.deadLetterMessages}
                icon="material-symbols:report-outline-rounded"
                tone={health.data.deadLetterMessages > 0 ? 'critical' : 'good'}
                caption="Awaiting replay"
              />
            </Box>

            <SectionCard
              title="Replay a dead-lettered message"
              subtitle="Privileged and idempotent — replaying the same message twice is safe"
            >
              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} alignItems="flex-start">
                <TextInput
                  label="Integration message ID"
                  value={replayId}
                  onChange={setReplayId}
                  sx={{ maxWidth: { sm: 420 } }}
                />
                <Button
                  variant="contained"
                  color="secondary"
                  onClick={replay}
                  disabled={replaying || !replayId.trim()}
                  startIcon={<IconifyIcon icon="material-symbols:restart-alt-rounded" />}
                >
                  {replaying ? 'Replaying…' : 'Replay'}
                </Button>
              </Stack>
            </SectionCard>

            <SectionCard
              title="Recent messages"
              subtitle="From the service's health projection"
              flush
            >
              <Box sx={{ p: 2.5 }}>
                {recent.length === 0 ? (
                  <Typography variant="body2" color="text.secondary">
                    No recent integration messages in the projection.
                  </Typography>
                ) : (
                  <Stack divider={<Divider />} spacing={0}>
                    {recent.map((summary, index) => {
                      const id = field(summary, 'id', 'messageId');
                      const status = field(summary, 'status');
                      return (
                        <Stack
                          key={id ?? index}
                          direction={{ xs: 'column', sm: 'row' }}
                          justifyContent="space-between"
                          spacing={1}
                          sx={{ py: 1.5 }}
                        >
                          <Box sx={{ minWidth: 0 }}>
                            <Typography variant="body2" fontWeight={700}>
                              {field(summary, 'sourceSystem', 'source') ?? 'Unknown source'} ·{' '}
                              {field(summary, 'eventType') ?? 'event'}
                            </Typography>
                            <Typography variant="caption" color="text.secondary">
                              {id ?? '—'} · received{' '}
                              {formatDateTime(field(summary, 'receivedAt', 'occurredAt') ?? null)}
                            </Typography>
                            {field(summary, 'failureReason') && (
                              <Typography variant="caption" color="error.main" display="block">
                                {field(summary, 'failureReason')}
                              </Typography>
                            )}
                          </Box>
                          <Stack
                            direction="row"
                            spacing={1}
                            alignItems="center"
                            sx={{ flexShrink: 0 }}
                          >
                            {status && <StatusChip value={status} />}
                            {status === 'DEAD_LETTER' && id && (
                              <Button size="small" variant="text" onClick={() => setReplayId(id)}>
                                Use ID
                              </Button>
                            )}
                          </Stack>
                        </Stack>
                      );
                    })}
                  </Stack>
                )}
              </Box>
            </SectionCard>

            <Alert severity="info">
              The service does not expose an inbox search endpoint, so this page shows only the
              messages carried in the health projection. Replay is available by message identifier.
            </Alert>
          </Stack>
        )}
      </DataState>
    </Box>
  );
};

export default IntegrationHealthPage;
