import { useMemo, useState } from 'react';
import { DispatchIntegrationHealth } from 'modules/dispatch/api/dto';
import { dispatchIntegrationsApi } from 'modules/dispatch/api/dispatchApi';
import { shortId } from 'modules/fuel/components/fuelFormat';
import { humanise } from 'modules/fleet/api/enums';
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
import { dispatchPaths } from 'shared/layout/navigation';

type InboxMessage = DispatchIntegrationHealth['inbox']['recentMessages'][number];
type OutboxEntry = DispatchIntegrationHealth['outbox']['recentDeadLetters'][number];

/**
 * Scanner and carrier integration health.
 *
 * The dispatch service returns inbound and outbound health in **one** payload — unlike fuel, which
 * splits them across two endpoints — so this screen is a single fetch. Both halves are service-wide
 * rather than site-scoped: the inbox is shared, and a message here may belong to another module.
 *
 * Replay is offered for dead-lettered outbound messages only. Inbound scanner events are idempotent
 * on their own signature, so a provider that re-sends is safe without an operator doing anything.
 */
const DispatchIntegrationPage = () => {
  const { notifySuccess, notifyError } = useNotifier();
  const [replaying, setReplaying] = useState<string | null>(null);

  const health = useApiQuery((signal) => dispatchIntegrationsApi.health(signal), []);

  const replay = async (messageId: string) => {
    setReplaying(messageId);
    try {
      await dispatchIntegrationsApi.replay(messageId);
      notifySuccess('Message requeued for publication.');
      health.refetch();
    } catch (error) {
      notifyError(error);
    } finally {
      setReplaying(null);
    }
  };

  const inbox = health.data?.inbox;
  const outbox = health.data?.outbox;

  const messageColumns = useMemo<Column<InboxMessage>[]>(
    () => [
      {
        key: 'message',
        header: 'Message',
        width: 260,
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
        cell: (row) => row.siteCode ?? '—',
      },
      {
        key: 'received',
        header: 'Received',
        width: 170,
        cell: (row) => formatDateTime(row.receivedAt),
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

  const outboxColumns = useMemo<Column<OutboxEntry>[]>(
    () => [
      {
        key: 'event',
        header: 'Event',
        width: 260,
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
        width: 280,
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

  return (
    <div>
      <PageHeader
        title="Scanner integration"
        subtitle="Inbound scanner and carrier feeds, and the events this module publishes."
        crumbs={[
          { label: 'Dispatch', to: dispatchPaths.dashboard },
          { label: 'Scanner integration' },
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
        {health.data && (
          <div className="space-y-5">
            {outbox && outbox.deadLettered > 0 && (
              <Alert
                variant="error"
                title={`${outbox.deadLettered} outbound messages are dead lettered`}
              >
                Downstream systems have not received these dispatch events. Replay them below once
                the cause has been dealt with; a replay returns the message to the pending queue.
              </Alert>
            )}

            {inbox && inbox.rejectedMessages > 0 && (
              <Alert
                variant="warning"
                title={`${inbox.rejectedMessages} inbound messages were rejected`}
              >
                A rejected message failed signature verification or schema validation and was not
                applied. The sending system has to correct and re-send it — there is no replay for
                inbound.
              </Alert>
            )}

            <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
              <StatCard
                label="Inbound processed"
                value={formatNumber(inbox?.processedMessages ?? 0)}
                icon="cloud"
                caption="Accepted and applied"
              />
              <StatCard
                label="Inbound rejected"
                value={formatNumber(inbox?.rejectedMessages ?? 0)}
                icon="alert-circle"
                tone={(inbox?.rejectedMessages ?? 0) > 0 ? 'caution' : 'neutral'}
                caption="Signature or payload refused"
              />
              <StatCard
                label="Outbound pending"
                value={formatNumber(outbox?.pending ?? 0)}
                icon="inbox"
                caption={`${formatNumber(outbox?.published ?? 0)} published`}
              />
              <StatCard
                label="Dead lettered"
                value={formatNumber(outbox?.deadLettered ?? 0)}
                icon="alert-triangle"
                tone={(outbox?.deadLettered ?? 0) > 0 ? 'critical' : 'neutral'}
                caption="Awaiting replay"
              />
            </div>

            <SectionCard
              title="Outbound dead letters"
              subtitle="Dispatch events downstream systems did not receive"
              flush
            >
              <DataState
                loading={false}
                empty={(outbox?.recentDeadLetters.length ?? 0) === 0}
                emptyTitle="Nothing dead lettered"
                emptyHint="Every dispatch event has been published."
                minHeight={180}
              >
                <DataTable
                  rows={outbox?.recentDeadLetters ?? []}
                  columns={outboxColumns}
                  getRowId={(row) => row.id}
                  caption="Dispatch outbound messages that could not be published, with the recorded failure, attempt count and a control to replay each."
                  dense
                />
              </DataState>
            </SectionCard>

            <SectionCard
              title="Recent inbound messages"
              subtitle="Signed scanner and carrier callbacks across the service"
              flush
            >
              <DataState
                loading={false}
                empty={(inbox?.recentMessages.length ?? 0) === 0}
                emptyTitle="No inbound messages"
                emptyHint="No scanner or carrier has posted to this service."
                minHeight={180}
              >
                <DataTable
                  rows={inbox?.recentMessages ?? []}
                  columns={messageColumns}
                  getRowId={(row) => row.id}
                  caption="Recent inbound integration messages, with their source, site, receipt time, attempt count and status."
                  dense
                />
              </DataState>
              <div className="px-5 pt-2 pb-4">
                <p className="text-theme-xs text-gray-600">
                  Checked {formatDateTime(inbox?.checkedAt)}. The inbox is shared across the service
                  and is not filtered to dispatch or to a site — a message here may belong to another
                  module.
                </p>
              </div>
            </SectionCard>

            <SectionCard title="How the feeds behave">
              <ul className="space-y-3 text-theme-sm text-gray-700">
                <li>
                  <strong>Scanner events</strong> arrive signed, at{' '}
                  <span className="font-mono text-theme-xs">
                    /integrations/scanners/{'{provider}'}/events
                  </span>
                  , and are idempotent on their own signature — a provider that re-sends the same
                  event is safe without anyone intervening.
                </li>
                <li>
                  <strong>Carrier status</strong> callbacks arrive at{' '}
                  <span className="font-mono text-theme-xs">
                    /integrations/carriers/{'{carrier}'}/status
                  </span>{' '}
                  and update the consignment they name.
                </li>
                <li>
                  <strong>Outbound</strong> publication is what tells the rest of the platform a
                  consignment moved. A dead letter means a downstream system is out of step with what
                  this module recorded — {humanise('REPLAY').toLowerCase()} is the fix, once the cause
                  is dealt with.
                </li>
              </ul>
            </SectionCard>
          </div>
        )}
      </DataState>
    </div>
  );
};

export default DispatchIntegrationPage;
