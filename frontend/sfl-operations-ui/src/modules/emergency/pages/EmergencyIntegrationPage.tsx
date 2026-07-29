import { useMemo, useState } from 'react';
import type { EmergencyInboxMessage, EmergencyOutboxEntry } from 'modules/emergency/api/dto';
import { emergencyIntegrationsApi } from 'modules/emergency/api/emergencyApi';
import { shortId } from 'modules/fuel/components/fuelFormat';
import Alert from 'shared/components/Alert';
import Button from 'shared/components/Button';
import DataState from 'shared/components/DataState';
import DataTable, { CellStack, Column } from 'shared/components/DataTable';
import { useNotifier } from 'shared/components/Notifier';
import PageHeader from 'shared/components/PageHeader';
import SectionCard from 'shared/components/SectionCard';
import StatCard from 'shared/components/StatCard';
import StatusChip from 'shared/components/StatusChip';
import { formatDateTime, formatNumber } from 'shared/components/format';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { emergencyPaths } from 'shared/layout/navigation';

/**
 * Provider and downstream integration health for S174.
 *
 * Two feeds meet in this service and both are readable now. Outbound is the transactional outbox —
 * every activation, approval and closure this service publishes — with a health read and a
 * privileged replay. Inbound is the provider callbacks carrying delivery status and
 * acknowledgements, and until gap 3 was closed it had **no read at all**: no inbox endpoint, no
 * rejection count, no message list.
 *
 * That mattered more here than anywhere else on the platform, because the inbound feed is the only
 * thing that ever writes `delivered`, `failed` and `acknowledged`. An activation showing 480 sent
 * and 0 delivered could not be told apart from one whose provider was posting and being rejected on
 * every callback. Both counts are on this screen now.
 *
 * Replay stays outbound-only, and deliberately: a rejected inbound message failed signature
 * verification or schema validation, so the sending system has to correct and re-send it.
 */
const EmergencyIntegrationPage = () => {
  const { notifySuccess, notifyError } = useNotifier();
  const [replaying, setReplaying] = useState<string | null>(null);

  const health = useApiQuery((signal) => emergencyIntegrationsApi.health(signal), []);
  const inbox = useApiQuery((signal) => emergencyIntegrationsApi.inbox(20, signal), []);

  const replay = async (messageId: string) => {
    setReplaying(messageId);
    try {
      const result = await emergencyIntegrationsApi.replay(messageId);
      if (result.requeued) {
        notifySuccess('Message requeued for publication.');
      } else {
        notifySuccess(
          'Nothing was requeued.',
          'The service reports this message is not dead lettered — it may already have been replayed.',
        );
      }
      health.refetch();
    } catch (error) {
      notifyError(error);
    } finally {
      setReplaying(null);
    }
  };

  const columns = useMemo<Column<EmergencyOutboxEntry>[]>(
    () => [
      {
        key: 'event',
        header: 'Event',
        width: 280,
        cell: (row) => (
          <CellStack
            primary={row.eventType}
            secondary={`${row.aggregateType} ${shortId(row.aggregateId)}`}
          />
        ),
      },
      {
        key: 'failure',
        header: 'Failure',
        width: 300,
        cell: (row) => row.failureReason ?? <span className="text-gray-500">Not recorded</span>,
      },
      {
        key: 'attempts',
        header: 'Attempts',
        width: 100,
        align: 'right',
        hideBelowLg: true,
        cell: (row) => row.attemptCount,
      },
      {
        key: 'status',
        header: 'Status',
        width: 130,
        hideBelowLg: true,
        cell: (row) => <StatusChip value={row.status} />,
      },
      {
        key: 'created',
        header: 'Created',
        width: 170,
        hideBelowLg: true,
        cell: (row) => formatDateTime(row.createdAt),
      },
      {
        key: 'replay',
        header: '',
        width: 120,
        align: 'right',
        cell: (row) => (
          <Button
            size="sm"
            variant="outline"
            startIcon="refresh"
            loading={replaying === row.id}
            disabled={replaying !== null}
            onClick={() => replay(row.id)}
          >
            Replay
          </Button>
        ),
      },
    ],
    // Rebuilt when a replay starts or finishes so the row's own button reflects it.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [replaying],
  );

  const inboxColumns = useMemo<Column<EmergencyInboxMessage>[]>(
    () => [
      {
        key: 'message',
        header: 'Message',
        width: 250,
        cell: (row) => (
          <CellStack
            primary={`${row.sourceSystem} · ${row.eventType}`}
            secondary={row.idempotencyKey ? `key ${shortId(row.idempotencyKey)}` : 'no key'}
          />
        ),
      },
      {
        key: 'site',
        header: 'Site',
        width: 110,
        hideBelowLg: true,
        cell: (row) => row.siteScope ?? '—',
      },
      {
        key: 'received',
        header: 'Received',
        width: 170,
        cell: (row) => formatDateTime(row.receivedAt),
      },
      {
        key: 'failure',
        header: 'Failure',
        width: 240,
        cell: (row) => row.failureReason ?? <span className="text-gray-500">—</span>,
      },
      {
        key: 'attempts',
        header: 'Attempts',
        width: 100,
        align: 'right',
        hideBelowLg: true,
        cell: (row) => row.attempts,
      },
      {
        key: 'status',
        header: 'Status',
        width: 140,
        align: 'right',
        cell: (row) => <StatusChip value={row.status} />,
      },
    ],
    [],
  );

  const outbox = health.data;

  return (
    <div>
      <PageHeader
        title="Provider integration"
        subtitle="What this service publishes, and what providers have posted back to it."
        crumbs={[
          { label: 'Emergency', to: emergencyPaths.dashboard },
          { label: 'Provider integration' },
        ]}
        actions={
          <Button variant="outline" startIcon="refresh" onClick={health.refetch}>
            Refresh
          </Button>
        }
      />

      <DataState
        loading={health.initialising}
        error={health.error}
        onRetry={health.refetch}
        minHeight={300}
      >
        {outbox && (
          <div className="space-y-5">
            {outbox.deadLettered > 0 && (
              <Alert
                variant="error"
                title={`${outbox.deadLettered} outbound messages are dead lettered`}
              >
                Downstream systems have not been told about these activations. Replay them below
                once the cause has been dealt with — a replay returns the message to the pending
                queue for another attempt.
              </Alert>
            )}

            <div className="grid gap-4 sm:grid-cols-3">
              <StatCard
                label="Published"
                value={formatNumber(outbox.published)}
                icon="cloud"
                caption="Delivered to downstream systems"
              />
              <StatCard
                label="Pending"
                value={formatNumber(outbox.pending)}
                icon="inbox"
                tone={outbox.pending > 0 ? 'caution' : 'neutral'}
                caption="Awaiting their next attempt"
              />
              <StatCard
                label="Dead lettered"
                value={formatNumber(outbox.deadLettered)}
                icon="alert-triangle"
                tone={outbox.deadLettered > 0 ? 'critical' : 'neutral'}
                caption="Exhausted their attempts"
              />
            </div>

            <SectionCard
              title="Outbound dead letters"
              subtitle="Emergency events downstream systems did not receive"
              flush
            >
              <DataState
                loading={false}
                empty={outbox.recentDeadLetters.length === 0}
                emptyTitle="Nothing dead lettered"
                emptyHint="Every emergency event this service raised has been published."
                minHeight={180}
              >
                <DataTable
                  rows={outbox.recentDeadLetters}
                  columns={columns}
                  getRowId={(row) => row.id}
                  caption="Emergency outbound messages that could not be published, with the recorded failure, attempt count, status and a control to replay each."
                  dense
                />
              </DataState>
            </SectionCard>

            <SectionCard
              title="Inbound provider callbacks"
              subtitle="Delivery status and acknowledgements, as the secure inbox recorded them"
              flush
            >
              <DataState
                loading={inbox.initialising}
                error={inbox.error}
                onRetry={inbox.refetch}
                minHeight={200}
              >
                {inbox.data && (
                  <>
                    {inbox.data.rejected > 0 && (
                      <div className="px-5 pt-4">
                        <Alert
                          variant="warning"
                          title={`${inbox.data.rejected} inbound messages were rejected`}
                        >
                          A rejected message failed signature verification or schema validation and
                          was never applied, so nothing it carried reached an activation. There is no
                          inbound replay by design — the sending system has to correct and re-send it.
                        </Alert>
                      </div>
                    )}

                    <div className="grid gap-4 px-5 py-4 sm:grid-cols-3">
                      <StatCard
                        label="Processed"
                        value={formatNumber(inbox.data.processed)}
                        icon="check-circle"
                        caption="Accepted and applied to an activation"
                      />
                      <StatCard
                        label="Rejected"
                        value={formatNumber(inbox.data.rejected)}
                        icon="alert-circle"
                        tone={inbox.data.rejected > 0 ? 'caution' : 'neutral'}
                        caption="Signature or payload refused"
                      />
                      <StatCard
                        label="Dead lettered"
                        value={formatNumber(inbox.data.deadLettered)}
                        icon="alert-triangle"
                        tone={inbox.data.deadLettered > 0 ? 'critical' : 'neutral'}
                        caption="Accepted, then failed to process"
                      />
                    </div>

                    <DataState
                      loading={false}
                      empty={inbox.data.recentMessages.length === 0}
                      emptyTitle="No provider has posted to this service"
                      emptyHint="Which is why delivered and acknowledged read zero: nothing else writes them."
                      minHeight={160}
                    >
                      <DataTable
                        rows={inbox.data.recentMessages}
                        columns={inboxColumns}
                        getRowId={(row) => row.id}
                        caption="Recent inbound provider callbacks, with source, event type, site scope, receipt time, recorded failure, attempt count and status."
                        dense
                      />
                    </DataState>

                    <div className="px-5 pt-2 pb-4">
                      <p className="text-theme-xs text-gray-600">
                        Checked {formatDateTime(inbox.data.checkedAt)}. Payloads are deliberately not
                        shown: a callback names recipients, and an integration-health screen has no
                        business displaying contact detail.
                      </p>
                    </div>
                  </>
                )}
              </DataState>
            </SectionCard>

            <SectionCard title="How the callback path behaves">
              <ul className="space-y-3 text-theme-sm text-gray-700">
                <li>
                  <strong>Delivery status</strong> is idempotent on{' '}
                  <span className="font-mono text-theme-xs">
                    (activation, provider, providerMessageId)
                  </span>
                  , so a provider that re-sends the same receipt is applied once. It is what moves a
                  channel from sending to delivered, partially delivered or failed.
                </li>
                <li>
                  <strong>Acknowledgements</strong> are idempotent on{' '}
                  <span className="font-mono text-theme-xs">(activation, recipientRef)</span>, so a
                  recipient who replies twice counts once. Outstanding acknowledgements are what the
                  scheduled sweep escalates on.
                </li>
                <li>
                  <strong>This dashboard never posts either.</strong> Both require an HMAC signature
                  over the raw body from a registered shared secret, and a browser cannot hold one. A
                  dashboard that could post delivery facts would be fabricating them.
                </li>
              </ul>
            </SectionCard>
          </div>
        )}
      </DataState>
    </div>
  );
};

export default EmergencyIntegrationPage;
