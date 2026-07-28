import { useMemo, useState } from 'react';
import { integrationsApi } from 'modules/fleet/api/fleetApi';
import Alert from 'shared/components/Alert';
import Button from 'shared/components/Button';
import DataState from 'shared/components/DataState';
import DataTable, { Column } from 'shared/components/DataTable';
import { useNotifier } from 'shared/components/Notifier';
import PageHeader from 'shared/components/PageHeader';
import SectionCard from 'shared/components/SectionCard';
import StatCard from 'shared/components/StatCard';
import StatusChip from 'shared/components/StatusChip';
import { TextInput } from 'shared/components/fields';
import { formatDateTime } from 'shared/components/format';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { fleetPaths } from 'shared/layout/navigation';

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

/** The projection's entries have no guaranteed identifier, so position is the key of last resort. */
interface MessageRow {
  key: string;
  summary: Record<string, unknown>;
}

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

  const rows = useMemo<MessageRow[]>(() => {
    const recent = (health.data?.recentMessages ?? []) as Record<string, unknown>[];
    return recent.map((summary, index) => ({
      key: field(summary, 'id', 'messageId') ?? String(index),
      summary,
    }));
  }, [health.data]);

  const columns = useMemo<Column<MessageRow>[]>(
    () => [
      {
        key: 'message',
        header: 'Message',
        width: 340,
        cell: ({ summary }) => {
          const failureReason = field(summary, 'failureReason');
          return (
            <div className="min-w-0">
              <div className="truncate font-semibold text-gray-800">
                {field(summary, 'sourceSystem', 'source') ?? 'Unknown source'} ·{' '}
                {field(summary, 'eventType') ?? 'event'}
              </div>
              <div className="truncate text-theme-xs text-gray-500">
                {field(summary, 'id', 'messageId') ?? '—'} · received{' '}
                {formatDateTime(field(summary, 'receivedAt', 'occurredAt') ?? null)}
              </div>
              {failureReason && (
                <div className="text-theme-xs text-error-600">{failureReason}</div>
              )}
            </div>
          );
        },
      },
      {
        key: 'status',
        header: 'Status',
        width: 130,
        cell: ({ summary }) => {
          const status = field(summary, 'status');
          return status ? <StatusChip value={status} /> : null;
        },
      },
      {
        key: 'actions',
        header: <span className="sr-only">Actions</span>,
        width: 110,
        align: 'right',
        cell: ({ summary }) => {
          const id = field(summary, 'id', 'messageId');
          return field(summary, 'status') === 'DEAD_LETTER' && id ? (
            <Button size="sm" variant="ghost" onClick={() => setReplayId(id)}>
              Use ID
            </Button>
          ) : null;
        },
      },
    ],
    [],
  );

  return (
    <div>
      <PageHeader
        title="Integration health"
        subtitle="Signed telematics intake: what has been processed, rejected or dead-lettered."
        crumbs={[{ label: 'Fleet', to: fleetPaths.dashboard }, { label: 'Integration health' }]}
        actions={
          <Button variant="outline" startIcon="refresh" onClick={health.refetch}>
            Refresh
          </Button>
        }
        meta={
          health.data && (
            <p className="text-theme-xs text-gray-500">
              Checked {formatDateTime(health.data.checkedAt)}
            </p>
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
          <div className="space-y-5">
            {health.data.deadLetterMessages > 0 && (
              <Alert variant="error">
                {health.data.deadLetterMessages} message
                {health.data.deadLetterMessages === 1 ? '' : 's'} require replay or operator review.
                Until they are cleared, vehicle movement data may be stale.
              </Alert>
            )}

            <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
              <StatCard
                label="Processed"
                value={health.data.processedMessages}
                icon="check-circle"
                tone="good"
                caption="Accepted and applied"
              />
              <StatCard
                label="Rejected"
                value={health.data.rejectedMessages}
                icon="alert-circle"
                tone={health.data.rejectedMessages > 0 ? 'caution' : 'good'}
                caption="Signature, allowlist or schema"
              />
              <StatCard
                label="Dead letters"
                value={health.data.deadLetterMessages}
                icon="alert-triangle"
                tone={health.data.deadLetterMessages > 0 ? 'critical' : 'good'}
                caption="Awaiting replay"
              />
            </div>

            <SectionCard
              title="Replay a dead-lettered message"
              subtitle="Privileged and idempotent — replaying the same message twice is safe"
            >
              <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
                <TextInput
                  label="Integration message ID"
                  value={replayId}
                  onChange={setReplayId}
                  className="sm:max-w-[420px] sm:flex-1"
                />
                <Button
                  variant="primary"
                  startIcon="refresh"
                  loading={replaying}
                  disabled={!replayId.trim()}
                  onClick={replay}
                >
                  {replaying ? 'Replaying…' : 'Replay'}
                </Button>
              </div>
            </SectionCard>

            <SectionCard
              title="Recent messages"
              subtitle="From the service's health projection"
              flush
            >
              {rows.length === 0 ? (
                <p className="p-5 text-theme-sm text-gray-500">
                  No recent integration messages in the projection.
                </p>
              ) : (
                <DataTable
                  rows={rows}
                  columns={columns}
                  getRowId={(row) => row.key}
                  loading={health.loading}
                  dense
                />
              )}
            </SectionCard>

            <Alert variant="info">
              The service does not expose an inbox search endpoint, so this page shows only the
              messages carried in the health projection. Replay is available by message identifier.
            </Alert>
          </div>
        )}
      </DataState>
    </div>
  );
};

export default IntegrationHealthPage;
