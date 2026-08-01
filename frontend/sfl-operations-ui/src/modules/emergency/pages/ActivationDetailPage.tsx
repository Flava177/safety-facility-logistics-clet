import { useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import type { NotificationChannel } from 'modules/emergency/api/dto';
import { CHANNEL_DESCRIPTIONS, PRIORITY_DESCRIPTIONS } from 'modules/emergency/api/enums';
import { activationsApi } from 'modules/emergency/api/emergencyApi';
import {
  ACTIVATION_RULES,
  activationLive,
  afterActionOutstanding,
  canRecordAfterAction,
  canTransition,
  whyUnavailable,
} from 'modules/emergency/api/workflow';
import {
  ActivationStatusChip,
  ConsequenceLine,
  ConsequencePanel,
  listChannels,
} from 'modules/emergency/components/EmergencyFields';
import { formatElapsed, percentOf, totalsFor } from 'modules/emergency/components/emergencyFormat';
import { useSiteRecords } from 'modules/emergency/components/useSiteRecords';
import {
  AfterActionApprovalDialog,
  AllClearDialog,
  CancelActivationDialog,
  CloseActivationDialog,
  DegradedFallbackDialog,
  ReopenActivationDialog,
  RejectActivationDialog,
  SendActivationDialog,
} from 'modules/emergency/dialogs/activationDialogs';
import { shortId, siteOf } from 'modules/fuel/components/fuelFormat';
import { humanise } from 'modules/fleet/api/enums';
import Alert from 'shared/components/Alert';
import Button from 'shared/components/Button';
import DataState from 'shared/components/DataState';
import DataTable, { CellStack, Column } from 'shared/components/DataTable';
import Icon from 'shared/components/Icon';
import KeyValueGrid from 'shared/components/KeyValueGrid';
import { useNotifier } from 'shared/components/Notifier';
import PageHeader from 'shared/components/PageHeader';
import SectionCard from 'shared/components/SectionCard';
import StatCard from 'shared/components/StatCard';
import StatusChip from 'shared/components/StatusChip';
import WorkflowTimeline, { TimelineEntry } from 'shared/components/WorkflowTimeline';
import { formatDateTime, formatNumber } from 'shared/components/format';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { emergencyPaths } from 'shared/layout/navigation';

/**
 * One activation, end to end.
 *
 * Read through `GET /activations/{id}/status`, which returns the activation, its per-channel
 * fan-out and the acknowledgement count in a single request — so the record and its counters can
 * never be a refresh apart from each other.
 *
 * The history is the service's own record of every transition, read from `activation_history`. It
 * used to be reconstructed from whatever timestamps the activation still carried, which silently
 * omitted any transition that left no field behind — that was gap 4, and it is closed.
 */
const ActivationDetailPage = () => {
  const { activationId = '' } = useParams();
  const navigate = useNavigate();
  const { notifySuccess, notifyError } = useNotifier();

  const [working, setWorking] = useState<string | null>(null);
  const [rejecting, setRejecting] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [sending, setSending] = useState(false);
  const [degrading, setDegrading] = useState(false);
  const [clearing, setClearing] = useState(false);
  const [approvingAfterAction, setApprovingAfterAction] = useState(false);
  const [closing, setClosing] = useState(false);
  const [reopening, setReopening] = useState(false);

  const query = useApiQuery(
    (signal) => activationsApi.status(activationId, signal),
    [activationId],
  );

  const activation = query.data?.activation;
  const channels = useMemo(() => query.data?.channels ?? [], [query.data]);
  const records = useSiteRecords(activation ? siteOf(activation.siteCode) : '');

  const totals = useMemo(() => totalsFor(channels), [channels]);

  const run = async (label: string, action: () => Promise<unknown>, success: string) => {
    setWorking(label);
    try {
      await action();
      notifySuccess(success);
      query.refetch();
    } catch (error) {
      notifyError(error);
    } finally {
      setWorking(null);
    }
  };

  /** The service's own transition record. Written on every change since day one; readable now. */
  const history = useApiQuery(
    (signal) => activationsApi.history(activationId, signal),
    [activationId],
  );

  const timeline = useMemo<TimelineEntry[]>(
    () =>
      (history.data ?? []).map((entry) => ({
        id: entry.id,
        title: humanise(entry.action),
        detail: entry.fromStatus
          ? `${humanise(entry.fromStatus)} → ${humanise(entry.toStatus)}${
              entry.comment ? ` — ${entry.comment}` : ''
            }`
          : `${humanise(entry.toStatus)}${entry.comment ? ` — ${entry.comment}` : ''}`,
        actor: entry.actor,
        occurredAt: entry.occurredAt,
        // Break-glass and escalation are the two an operator scanning the column must not miss.
        tone:
          entry.toStatus === 'BREAK_GLASS_ACTIVE' || entry.toStatus === 'ESCALATED'
            ? ('danger' as const)
            : entry.toStatus === 'APPROVED' || entry.action === 'after-action-approve'
              ? ('accent' as const)
              : ('default' as const),
      })),
    [history.data],
  );

  const channelColumns = useMemo<Column<NotificationChannel>[]>(
    () => [
      {
        key: 'channel',
        header: 'Channel',
        width: 220,
        cell: (row) => (
          <CellStack
            primary={humanise(row.channelType)}
            secondary={CHANNEL_DESCRIPTIONS[row.channelType]}
          />
        ),
      },
      {
        key: 'target',
        header: 'Target',
        width: 100,
        align: 'right',
        cell: (row) => formatNumber(row.targetCount),
      },
      {
        key: 'sent',
        header: 'Sent',
        width: 100,
        align: 'right',
        cell: (row) => formatNumber(row.sentCount),
      },
      {
        key: 'delivered',
        header: 'Delivered',
        width: 110,
        align: 'right',
        cell: (row) => (
          <span className={row.deliveredCount === 0 ? 'text-gray-500' : undefined}>
            {formatNumber(row.deliveredCount)}
          </span>
        ),
      },
      {
        key: 'failed',
        header: 'Failed',
        width: 100,
        align: 'right',
        cell: (row) => (
          <span className={row.failedCount > 0 ? 'font-semibold text-error-800' : 'text-gray-500'}>
            {formatNumber(row.failedCount)}
          </span>
        ),
      },
      {
        key: 'acknowledged',
        header: 'Acknowledged',
        width: 130,
        align: 'right',
        hideBelowLg: true,
        cell: (row) => formatNumber(row.acknowledgedCount),
      },
      {
        key: 'status',
        header: 'Status',
        width: 150,
        align: 'right',
        cell: (row) => <StatusChip value={row.status} />,
      },
    ],
    [],
  );

  return (
    <div>
      <DataState
        loading={query.initialising}
        error={query.error}
        onRetry={query.refetch}
        minHeight={360}
      >
        {activation && (
          <>
            <PageHeader
              title={activation.activationNumber}
              subtitle={records.templateName(activation.templateId)}
              crumbs={[
                { label: 'Emergency', to: emergencyPaths.dashboard },
                { label: 'Activations', to: emergencyPaths.activations },
                { label: activation.activationNumber },
              ]}
              meta={
                <span className="flex flex-wrap items-center gap-2">
                  <ActivationStatusChip status={activation.status} size="md" />
                  <StatusChip value={activation.mode} size="md" />
                  <StatusChip value={activation.priority} size="md" />
                  <span className="text-gray-600">
                    {siteOf(activation.siteCode)} ·{' '}
                    {activation.incidentReference
                      ? `incident ${activation.incidentReference}`
                      : 'no incident reference'}
                  </span>
                </span>
              }
              actions={
                <>
                  {canTransition(activation, 'submit') && (
                    <Button
                      variant="primary"
                      startIcon="workflow"
                      loading={working === 'submit'}
                      disabled={working !== null}
                      onClick={() =>
                        run(
                          'submit',
                          () => activationsApi.submit(activation.id),
                          'Submitted for approval.',
                        )
                      }
                    >
                      Submit for approval
                    </Button>
                  )}
                  {canTransition(activation, 'approve') && (
                    <Button
                      variant="primary"
                      startIcon="check-circle"
                      loading={working === 'approve'}
                      disabled={working !== null}
                      onClick={() =>
                        run(
                          'approve',
                          () => activationsApi.approve(activation.id),
                          'Approved and cleared to send.',
                        )
                      }
                    >
                      Approve
                    </Button>
                  )}
                  {canTransition(activation, 'reject') && (
                    <Button variant="outline" startIcon="close" onClick={() => setRejecting(true)}>
                      Reject
                    </Button>
                  )}
                  {canTransition(activation, 'cancel') && (
                    <Button variant="outline" startIcon="close" onClick={() => setCancelling(true)}>
                      Cancel
                    </Button>
                  )}
                  {canTransition(activation, 'activate') && (
                    <Button variant="danger" startIcon="megaphone" onClick={() => setSending(true)}>
                      Send broadcast
                    </Button>
                  )}
                  {canTransition(activation, 'degradedFallback') && (
                    <Button
                      variant="outline"
                      startIcon="alert-circle"
                      onClick={() => setDegrading(true)}
                    >
                      Record degraded fallback
                    </Button>
                  )}
                  {canTransition(activation, 'allClear') && (
                    <Button
                      variant="primary"
                      startIcon="check-circle"
                      onClick={() => setClearing(true)}
                    >
                      Send all-clear
                    </Button>
                  )}
                  {afterActionOutstanding(activation) && canRecordAfterAction(activation) && (
                    <Button
                      variant="accent"
                      startIcon="shield-check"
                      onClick={() => setApprovingAfterAction(true)}
                    >
                      Record after-action approval
                    </Button>
                  )}
                  {canTransition(activation, 'close') && (
                    <Button variant="outline" startIcon="document" onClick={() => setClosing(true)}>
                      Close activation
                    </Button>
                  )}
                  {canTransition(activation, 'reopen') && (
                    <Button variant="outline" startIcon="refresh" onClick={() => setReopening(true)}>
                      Reopen
                    </Button>
                  )}
                  <Button variant="outline" startIcon="refresh" onClick={query.refetch}>
                    Refresh
                  </Button>
                </>
              }
            />

            <div className="space-y-5">
              {activationLive(activation) && (
                <Alert variant="error" title="This broadcast is live">
                  It has gone out and has not been stood down. Send the all-clear when the emergency
                  is over — that is what tells the record it is finished, and it is not the same as
                  closing it.
                </Alert>
              )}

              {activation.degradedMode && (
                <Alert variant="warning" title="Degraded fallback has been recorded">
                  The recorded fallback path is{' '}
                  <span className="font-mono text-theme-xs">
                    {activation.fallbackPath ?? 'not specified'}
                  </span>
                  . Release 1 keeps this on the recorded adapter until the later CLET Comms
                  integration provides real delivery.
                </Alert>
              )}

              {activation.status === 'CANCELLED' && activation.closureReason && (
                <Alert variant="info" title="This activation was cancelled before send">
                  {activation.closureReason}
                </Alert>
              )}

              {activation.status === 'REOPENED' && activation.closureReason && (
                <Alert variant="warning" title="This activation was reopened">
                  {activation.closureReason} Close it again with updated evidence when the follow-up
                  is complete.
                </Alert>
              )}

              {afterActionOutstanding(activation) && (
                <Alert variant="warning" title="Sent without approval and not yet accounted for">
                  This was a break-glass broadcast. Closure is blocked until somebody holding{' '}
                  <span className="font-mono text-theme-xs">EMERGENCY_AFTER_ACTION_APPROVE</span>{' '}
                  records a justification against it.
                </Alert>
              )}

              {activation.status === 'REJECTED' && activation.rejectionReason && (
                <Alert variant="info" title="This activation was rejected">
                  {activation.rejectionReason} A rejected activation cannot be re-submitted; compose
                  a new one if the broadcast is still wanted.
                </Alert>
              )}

              <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
                <StatCard
                  label="Recipients targeted"
                  value={formatNumber(totals.target || records.audienceReach(activation.audienceGroupIds))}
                  icon="users"
                  caption={`${activation.audienceGroupIds.length} audience group${activation.audienceGroupIds.length === 1 ? '' : 's'}`}
                />
                <StatCard
                  label="Messages handed to gateways"
                  value={formatNumber(totals.sent)}
                  icon="megaphone"
                  caption={`Across ${channels.length || activation.channels.length} channel${(channels.length || activation.channels.length) === 1 ? '' : 's'}`}
                />
                <StatCard
                  label="Acknowledged"
                  value={formatNumber(query.data?.acknowledgements ?? 0)}
                  icon="check-circle"
                  tone={
                    totals.target > 0 && (query.data?.acknowledgements ?? 0) < totals.target
                      ? 'caution'
                      : 'neutral'
                  }
                  caption={`${percentOf(query.data?.acknowledgements ?? 0, totals.target)} of those targeted`}
                />
                <StatCard
                  label="Time to send"
                  value={formatElapsed(activation.fastLaneMillis)}
                  icon="clock"
                  caption="Command to last gateway hand-off"
                />
              </div>

              <SectionCard title="What was sent, and to whom">
                <KeyValueGrid
                  items={[
                    { label: 'Scenario', value: records.scenarioName(activation.scenarioId) },
                    { label: 'Template', value: records.templateName(activation.templateId) },
                    { label: 'Priority', value: PRIORITY_DESCRIPTIONS[activation.priority] },
                    { label: 'Channels', value: listChannels(activation.channels) },
                    {
                      label: 'Audience groups',
                      value:
                        activation.audienceGroupIds.map(records.audienceName).join(', ') || 'None',
                      span: 2,
                    },
                    {
                      label: 'Recipient zones',
                      value: activation.recipientZoneIds.map(records.zoneName).join(', ') || 'None',
                      span: 2,
                    },
                    { label: 'Incident reference', value: activation.incidentReference },
                    { label: 'Approved by', value: activation.approvedBy },
                    { label: 'Approved at', value: formatDateTime(activation.approvedAt) },
                    {
                      label: 'Escalation level',
                      value:
                        activation.escalationLevel > 0
                          ? `${activation.escalationLevel} — the acknowledgement SLA was breached`
                          : 'None',
                    },
                    {
                      label: 'Degraded mode',
                      value: activation.degradedMode
                        ? `Yes — fallback path ${activation.fallbackPath ?? 'not recorded'}`
                        : 'No',
                    },
                  ]}
                />
                {records.template(activation.templateId) && (
                  <div className="mt-4 rounded-md border border-gray-200 bg-gray-50 px-4 py-3">
                    <p className="text-theme-xs font-semibold tracking-wide text-gray-600 uppercase">
                      Message text
                    </p>
                    <p className="mt-1.5 whitespace-pre-wrap text-theme-sm text-gray-900">
                      {records.template(activation.templateId)!.body}
                    </p>
                  </div>
                )}
              </SectionCard>

              <SectionCard
                title="Channel fan-out"
                subtitle="One record per channel, with the counters the closure summary is built from"
                flush
              >
                <DataState
                  loading={false}
                  empty={channels.length === 0}
                  emptyTitle="Nothing has been sent"
                  emptyHint="Channel records are created when the activation is sent. This one has not been."
                  minHeight={160}
                >
                  <DataTable
                    rows={channels}
                    columns={channelColumns}
                    getRowId={(row) => row.id}
                    caption="Per-channel fan-out for this activation, with target, sent, delivered, failed and acknowledged counts and the channel's status."
                    dense
                  />
                </DataState>
                {channels.length > 0 && totals.delivered === 0 && totals.sent > 0 && (
                  <div className="px-5 pb-4">
                    <Alert variant="info" title="Delivered is zero because no provider has replied">
                      Sent means the message was handed to the gateway. Delivered, failed and
                      acknowledged are only written when a provider posts a signed callback to this
                      service — so on a system with no live provider they stay at zero, and that is
                      not a failed broadcast.
                    </Alert>
                  </div>
                )}
              </SectionCard>

              <SectionCard
                title="What happened"
                subtitle="Every transition the service recorded, oldest first"
              >
                <DataState
                  loading={history.initialising}
                  error={history.error}
                  onRetry={history.refetch}
                  minHeight={120}
                >
                  <WorkflowTimeline
                    entries={timeline}
                    emptyMessage="No transition has been recorded against this activation."
                  />
                </DataState>
              </SectionCard>

              {activation.status === 'CLOSED' && (
                <SectionCard title="Closure record">
                  <ConsequencePanel title="What the service wrote at closure">
                    <ConsequenceLine label="Reason" value={activation.closureReason} />
                    <ConsequenceLine label="Delivery" value={activation.deliverySummary} />
                    <ConsequenceLine
                      label="Acknowledgements"
                      value={activation.acknowledgementSummary}
                    />
                    <ConsequenceLine
                      label="Evidence"
                      value={
                        activation.closureEvidenceId ? (
                          <span className="font-mono text-theme-xs">
                            {shortId(activation.closureEvidenceId)}
                          </span>
                        ) : (
                          'Not recorded'
                        )
                      }
                    />
                  </ConsequencePanel>
                  {activation.afterActionApprovedBy && (
                    <div className="mt-4 flex items-start gap-2 text-theme-sm">
                      <Icon
                        name="shield-check"
                        size={16}
                        className="mt-0.5 shrink-0 text-success-700"
                      />
                      <span>
                        <span className="font-medium text-gray-900">
                          After-the-fact approval by {activation.afterActionApprovedBy}
                        </span>
                        <span className="text-gray-600">
                          {' '}
                          on {formatDateTime(activation.afterActionApprovedAt)} —{' '}
                          {activation.afterActionJustification}
                        </span>
                      </span>
                    </div>
                  )}
                </SectionCard>
              )}

              <SectionCard title="Provenance">
                <KeyValueGrid
                  items={[
                    { label: 'Created by', value: activation.metadata.createdBy },
                    { label: 'Created at', value: formatDateTime(activation.metadata.createdAt) },
                    { label: 'Last changed by', value: activation.metadata.lastModifiedBy },
                    {
                      label: 'Last changed at',
                      value: formatDateTime(activation.metadata.lastModifiedAt),
                    },
                    { label: 'Source channel', value: humanise(activation.metadata.sourceChannel) },
                    { label: 'Version', value: activation.metadata.version },
                    {
                      label: 'Correlation ID',
                      value: (
                        <span className="font-mono text-theme-xs">
                          {activation.metadata.correlationId ?? '—'}
                        </span>
                      ),
                      span: 2,
                    },
                  ]}
                />
              </SectionCard>

              {!canTransition(activation, 'close') && activation.status !== 'CLOSED' && (
                <p className="text-theme-sm text-gray-600">
                  {whyUnavailable('close')} Allowed from{' '}
                  {ACTIVATION_RULES.close.from.map((state) => humanise(state)).join(', ')}.
                </p>
              )}
            </div>

            {rejecting && (
              <RejectActivationDialog
                open
                activation={activation}
                onClose={() => setRejecting(false)}
                onDone={() => {
                  notifySuccess('Activation rejected.');
                  query.refetch();
                }}
              />
            )}

            {cancelling && (
              <CancelActivationDialog
                open
                activation={activation}
                onClose={() => setCancelling(false)}
                onDone={() => {
                  notifySuccess('Activation cancelled before send.');
                  query.refetch();
                }}
              />
            )}

            {sending && (
              <SendActivationDialog
                open
                activation={activation}
                records={records}
                onClose={() => setSending(false)}
                onDone={(sent) => {
                  notifySuccess(
                    'Broadcast sent.',
                    `Handed to ${sent.channels.length} channel${sent.channels.length === 1 ? '' : 's'} in ${formatElapsed(sent.fastLaneMillis)}.`,
                  );
                  query.refetch();
                }}
              />
            )}

            {degrading && (
              <DegradedFallbackDialog
                open
                activation={activation}
                onClose={() => setDegrading(false)}
                onDone={() => {
                  notifySuccess('Degraded fallback recorded.');
                  query.refetch();
                }}
              />
            )}

            {clearing && (
              <AllClearDialog
                open
                activation={activation}
                onClose={() => setClearing(false)}
                onDone={() => {
                  notifySuccess(
                    'All-clear sent.',
                    'The activation still needs a closure reason and evidence.',
                  );
                  query.refetch();
                }}
              />
            )}

            {approvingAfterAction && (
              <AfterActionApprovalDialog
                open
                activation={activation}
                onClose={() => setApprovingAfterAction(false)}
                onDone={() => {
                  notifySuccess(
                    'After-the-fact approval recorded.',
                    'This activation can now be closed.',
                  );
                  query.refetch();
                }}
              />
            )}

            {reopening && (
              <ReopenActivationDialog
                open
                activation={activation}
                onClose={() => setReopening(false)}
                onDone={() => {
                  notifySuccess('Activation reopened.');
                  query.refetch();
                }}
              />
            )}

            {closing && (
              <CloseActivationDialog
                open
                activation={activation}
                onClose={() => setClosing(false)}
                onDone={() => {
                  notifySuccess('Activation closed with evidence filed.');
                  query.refetch();
                  navigate(emergencyPaths.activations);
                }}
              />
            )}
          </>
        )}
      </DataState>
    </div>
  );
};

export default ActivationDetailPage;
