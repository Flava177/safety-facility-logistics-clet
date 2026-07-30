import { useCallback, useMemo, useState } from 'react';
import { InboxMessageResponse } from 'modules/fleet/api/dto';
import { INTEGRATION_MESSAGE_STATUSES, IntegrationMessageStatus } from 'modules/fleet/api/enums';
import { integrationsApi } from 'modules/fleet/api/fleetApi';
import Alert from 'shared/components/Alert';
import Button from 'shared/components/Button';
import DataState from 'shared/components/DataState';
import DataTable, { CellStack, Column } from 'shared/components/DataTable';
import FilterBar from 'shared/components/FilterBar';
import { useNotifier } from 'shared/components/Notifier';
import PageHeader from 'shared/components/PageHeader';
import SectionCard from 'shared/components/SectionCard';
import StatCard from 'shared/components/StatCard';
import StatusChip from 'shared/components/StatusChip';
import { EnumSelect, TextInput } from 'shared/components/fields';
import { formatDateTime } from 'shared/components/format';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { fleetPaths } from 'shared/layout/navigation';

/** How many messages the inbox search asks for. The service clamps at 500. */
const SEARCH_LIMIT = 100;

/**
 * Telematics and integration intake health.
 *
 * The counters come from the health projection; the messages come from `GET /integrations/messages`.
 * That search is why this page changed: replay takes a message identifier, and the only messages the
 * dashboard could see were the handful the health projection happened to carry — so replaying a dead
 * letter meant knowing its id from somewhere else entirely. Dead-letter replay was a documented
 * capability that could not be reached from here at all.
 *
 * The rows are typed now rather than read out of a loosely-shaped projection summary, so a missing
 * field is a compile error instead of an empty cell.
 *
 * Replay is offered on any message that is not already `PROCESSED`. That mirrors the service, which
 * treats replaying a processed message as a no-op rather than an error — the operation is
 * idempotent, and the audit entry is written either way.
 */
const IntegrationHealthPage = () => {
  const { notifyError, notifySuccess } = useNotifier();
  const [sourceSystem, setSourceSystem] = useState('');
  const [status, setStatus] = useState<IntegrationMessageStatus | ''>('');
  const [eventType, setEventType] = useState('');
  const [replayId, setReplayId] = useState('');
  const [replaying, setReplaying] = useState<string | null>(null);
  const filtered = Boolean(sourceSystem || status || eventType);

  const health = useApiQuery((signal) => integrationsApi.health(signal), []);

  const messages = useApiQuery(
    (signal) =>
      integrationsApi.messages(
        {
          sourceSystem: sourceSystem.trim() || undefined,
          status: status || undefined,
          eventType: eventType.trim() || undefined,
          size: SEARCH_LIMIT,
        },
        signal,
      ),
    [sourceSystem, status, eventType],
  );

  // Pulled out as locals because they are the stable half of the query objects, and the memoised
  // `replay` below depends on the refetch and not on the data.
  const { refetch: refetchHealth } = health;
  const { refetch: refetchMessages } = messages;

  const refreshAll = useCallback(() => {
    refetchHealth();
    refetchMessages();
  }, [refetchHealth, refetchMessages]);

  /**
   * Shared by the row action and the replay-by-id card, so both report the same way.
   *
   * Memoised because the column definitions close over it, and `useApiQuery` hands back a stable
   * `refetch`, so this identity only changes when the notifier does.
   */
  const replay = useCallback(
    async (messageId: string, onDone?: () => void) => {
      setReplaying(messageId);
      try {
        await integrationsApi.replay(messageId);
        notifySuccess('Replay accepted.');
        onDone?.();
        refreshAll();
      } catch (error) {
        notifyError(error);
      } finally {
        setReplaying(null);
      }
    },
    [refreshAll, notifyError, notifySuccess],
  );

  const columns = useMemo<Column<InboxMessageResponse>[]>(
    () => [
      {
        key: 'message',
        header: 'Message',
        width: 300,
        cell: (row) => (
          <div className="min-w-0">
            <CellStack
              primary={`${row.sourceSystem} · ${row.eventType ?? 'event'}`}
              secondary={`Received ${formatDateTime(row.receivedAt)}`}
            />
            {row.failureReason && (
              <div className="mt-1 text-theme-xs text-error-600">{row.failureReason}</div>
            )}
          </div>
        ),
      },
      {
        key: 'status',
        header: 'Status',
        width: 140,
        cell: (row) => (
          <div>
            <StatusChip value={row.status} />
            {/* Attempts only tell a story once there has been more than one. */}
            {row.attempts > 1 && (
              <div className="mt-1 text-theme-xs text-gray-500">{row.attempts} attempts</div>
            )}
          </div>
        ),
      },
      {
        key: 'site',
        header: 'Site',
        width: 100,
        hideBelowLg: true,
        cell: (row) => (
          <span className="text-theme-xs text-gray-600">{row.siteCode ?? '—'}</span>
        ),
      },
      {
        key: 'processedAt',
        header: 'Processed',
        width: 170,
        hideBelowLg: true,
        cell: (row) =>
          row.processedAt ? (
            <span className="text-theme-xs text-gray-600">{formatDateTime(row.processedAt)}</span>
          ) : (
            <span className="text-theme-xs text-gray-500">Not processed</span>
          ),
      },
      {
        key: 'correlation',
        header: 'Correlation',
        width: 130,
        hideBelowLg: true,
        cell: (row) => (
          <span className="font-mono text-theme-xs text-gray-600">
            {row.correlationId ? row.correlationId.slice(0, 8) : '—'}
          </span>
        ),
      },
      {
        key: 'actions',
        header: <span className="sr-only">Actions</span>,
        width: 110,
        align: 'right',
        cell: (row) =>
          row.status === 'PROCESSED' ? null : (
            <Button
              size="sm"
              variant="ghost"
              startIcon="refresh"
              loading={replaying === row.id}
              onClick={() => void replay(row.id)}
            >
              Replay
            </Button>
          ),
      },
    ],
    // `replaying` decides which row shows a spinner, so the columns depend on it.
    [replaying, replay],
  );

  return (
    <div>
      <PageHeader
        title="Integration health"
        subtitle="Signed telematics intake: what has been processed, rejected or dead-lettered."
        crumbs={[{ label: 'Fleet', to: fleetPaths.dashboard }, { label: 'Integration health' }]}
        actions={
          <Button variant="outline" startIcon="refresh" onClick={refreshAll}>
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
                <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                  <span>
                    {health.data.deadLetterMessages} message
                    {health.data.deadLetterMessages === 1 ? '' : 's'} require replay or operator
                    review. Until they are cleared, vehicle movement data may be stale.
                  </span>
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => setStatus('DEAD_LETTER')}
                    disabled={status === 'DEAD_LETTER'}
                  >
                    Show them
                  </Button>
                </div>
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

            <SectionCard title="Inbound messages" subtitle="Newest first" flush>
              <FilterBar
                onReset={() => {
                  setSourceSystem('');
                  setStatus('');
                  setEventType('');
                }}
                resetDisabled={!filtered}
              >
                <TextInput
                  label="Source system"
                  value={sourceSystem}
                  onChange={setSourceSystem}
                  helperText="Matches on part of the name."
                />
                <EnumSelect
                  label="Status"
                  value={status}
                  options={INTEGRATION_MESSAGE_STATUSES}
                  onChange={(value) => setStatus(value as IntegrationMessageStatus | '')}
                  allowEmpty
                />
                <TextInput
                  label="Event type"
                  value={eventType}
                  onChange={setEventType}
                  helperText="For example, vehicle.location."
                />
              </FilterBar>

              <DataState
                loading={messages.initialising}
                error={messages.error}
                empty={(messages.data?.length ?? 0) === 0}
                emptyTitle={filtered ? 'No messages match these filters' : 'No inbound messages'}
                emptyHint={
                  filtered
                    ? 'Adjust the filters, or reset them to see the whole inbox.'
                    : 'Nothing has arrived through the signed intake endpoint yet.'
                }
                onRetry={messages.refetch}
                minHeight={200}
              >
                <DataTable
                  rows={messages.data ?? []}
                  columns={columns}
                  getRowId={(row) => row.id}
                  loading={messages.loading}
                  dense
                />
                {(messages.data?.length ?? 0) >= SEARCH_LIMIT && (
                  <p className="px-5 pt-3 pb-1 text-theme-xs text-gray-500">
                    The most recent {SEARCH_LIMIT} messages. Filter by status or source system to see
                    further back.
                  </p>
                )}
              </DataState>
            </SectionCard>

            <SectionCard
              title="Replay by message identifier"
              subtitle="For an identifier that came from a log or an incident note rather than the list above"
            >
              <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
                <TextInput
                  label="Integration message ID"
                  value={replayId}
                  onChange={setReplayId}
                  className="sm:max-w-[420px] sm:flex-1"
                  helperText="Privileged and idempotent — replaying the same message twice is safe."
                />
                <Button
                  variant="primary"
                  startIcon="refresh"
                  loading={replaying === replayId.trim()}
                  disabled={!replayId.trim()}
                  onClick={() => void replay(replayId.trim(), () => setReplayId(''))}
                >
                  Replay
                </Button>
              </div>
            </SectionCard>
          </div>
        )}
      </DataState>
    </div>
  );
};

export default IntegrationHealthPage;
