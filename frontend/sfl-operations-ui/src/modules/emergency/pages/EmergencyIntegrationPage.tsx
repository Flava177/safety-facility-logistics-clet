import { useMemo, useState } from 'react';
import type { EmergencyOutboxEntry } from 'modules/emergency/api/dto';
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
 * Two feeds meet in this service and only one of them is readable. Outbound is the transactional
 * outbox — every activation, approval and closure this service publishes — and it has a health read
 * and a privileged replay. Inbound is the provider callbacks that carry delivery status and
 * acknowledgements, and the service publishes **no read of them at all**: there is no inbox
 * endpoint, no rejection count and no recent-message list, which is what the fleet, fuel and
 * dispatch modules all have.
 *
 * That asymmetry matters enough to say on the screen rather than only in the gap register. Every
 * "delivered" and "acknowledged" figure in this module arrives through a channel nobody here can
 * see the state of — so when they stay at zero, this is the page that cannot tell you why.
 */
const EmergencyIntegrationPage = () => {
  const { notifySuccess, notifyError } = useNotifier();
  const [replaying, setReplaying] = useState<string | null>(null);

  const health = useApiQuery((signal) => emergencyIntegrationsApi.health(signal), []);

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

  const outbox = health.data;

  return (
    <div>
      <PageHeader
        title="Provider integration"
        subtitle="What this service publishes, and the callback path it cannot show you."
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

            <SectionCard title="The inbound callback path">
              <Alert variant="info" title="This service publishes no read of its inbound feed">
                Delivery status and acknowledgements arrive on{' '}
                <span className="font-mono text-theme-xs">
                  /provider-callbacks/{'{provider}'}/delivery-status
                </span>{' '}
                and{' '}
                <span className="font-mono text-theme-xs">
                  /provider-callbacks/{'{provider}'}/acknowledgements
                </span>
                . Both pass a secure inbox — HMAC signature, source allowlist, schema check and
                idempotency — before any domain effect. None of that is readable: there is no inbox
                endpoint, no rejection count and no recent-message list, so a provider whose
                signature is failing cannot be diagnosed from this dashboard. Recorded as gap 3.
              </Alert>

              <ul className="mt-4 space-y-3 text-theme-sm text-gray-700">
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
                  over the raw body from a registered shared secret, and a browser cannot hold one.
                  A dashboard that could post delivery facts would be fabricating them.
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
