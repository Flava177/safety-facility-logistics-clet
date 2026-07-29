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
  CloseActivationDialog,
  RejectActivationDialog,
  SendActivationDialog,
} from 'modules/emergency/dialogs/activationDialogs';
import { DerivedNote } from 'modules/fuel/components/Provenance';
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
 * The history is reconstructed from the activation's own timestamps rather than fetched. The
 * service writes a transition history on every change and exposes no endpoint to read it, so what
 * is shown here is what the record itself still remembers: approval, the send, the all-clear,
 * after-action approval and closure. Intermediate transitions that left no field behind are simply
 * not there, and the caption says so rather than letting the timeline read as complete.
 */
const ActivationDetailPage = () => {
  const { activationId = '' } = useParams();
  const navigate = useNavigate();
  const { notifySuccess, notifyError } = useNotifier();

  const [working, setWorking] = useState<string | null>(null);
  const [rejecting, setRejecting] = useState(false);
  const [sending, setSending] = useState(false);
  const [clearing, setClearing] = useState(false);
  const [approvingAfterAction, setApprovingAfterAction] = useState(false);
  const [closing, setClosing] = useState(false);

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

  const timeline = useMemo<TimelineEntry[]>(() => {
    if (!activation) {
      return [];
    }
    const entries: TimelineEntry[] = [
      {
        id: 'created',
        title: 'Composed',
        detail: `${humanise(activation.mode)} activation for ${listChannels(activation.channels)}`,
        actor: activation.metadata.createdBy,
        occurredAt: activation.metadata.createdAt,
      },
    ];
    if (activation.approvedBy && activation.approvedAt) {
      entries.push({
        id: 'approved',
        title: 'Approved',
        detail: 'Cleared to send',
        actor: activation.approvedBy,
        occurredAt: activation.approvedAt,
        tone: 'accent',
      });
    }
    if (activation.rejectionReason) {
      entries.push({
        id: 'rejected',
        title: 'Rejected',
        detail: activation.rejectionReason,
        actor: activation.metadata.lastModifiedBy,
        occurredAt: activation.metadata.lastModifiedAt,
        tone: 'danger',
      });
    }
    if (activation.fastLaneMillis !== null) {
      entries.push({
        id: 'sent',
        title: activation.mode === 'BREAK_GLASS' ? 'Broadcast without approval' : 'Broadcast sent',
        detail: `Handed to ${channels.length || activation.channels.length} channel${
          (channels.length || activation.channels.length) === 1 ? '' : 's'
        } in ${formatElapsed(activation.fastLaneMillis)}`,
        actor: activation.metadata.lastModifiedBy,
        // The service does not record the send time as a field of its own; the elapsed measure is
        // what it keeps. Anchoring to the record's own creation is the closest honest point.
        occurredAt: activation.metadata.createdAt,
        tone: 'danger',
      });
    }
    if (activation.afterActionApprovedBy && activation.afterActionApprovedAt) {
      entries.push({
        id: 'after-action',
        title: 'After-the-fact approval recorded',
        detail: activation.afterActionJustification,
        actor: activation.afterActionApprovedBy,
        occurredAt: activation.afterActionApprovedAt,
        tone: 'accent',
      });
    }
    if (activation.allClearAt) {
      entries.push({
        id: 'all-clear',
        title: 'All-clear sent',
        detail: 'Emergency stood down',
        actor: activation.metadata.lastModifiedBy,
        occurredAt: activation.allClearAt,
      });
    }
    if (activation.status === 'CLOSED') {
      entries.push({
        id: 'closed',
        title: 'Closed',
        detail: activation.closureReason,
        actor: activation.metadata.lastModifiedBy,
        occurredAt: activation.metadata.lastModifiedAt,
      });
    }
    return entries.sort((left, right) => left.occurredAt.localeCompare(right.occurredAt));
  }, [activation, channels.length]);

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
                  {canTransition(activation, 'activate') && (
                    <Button variant="danger" startIcon="megaphone" onClick={() => setSending(true)}>
                      Send broadcast
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

              <SectionCard title="What happened" subtitle="Reconstructed from the record itself">
                <WorkflowTimeline
                  entries={timeline}
                  emptyMessage="Nothing has happened to this activation beyond its composition."
                />
                <DerivedNote>
                  Built from the timestamps the activation still carries — approval, the send, the
                  all-clear, after-action approval and closure. The service writes a full transition
                  history and publishes no endpoint to read it, so a transition that left no field
                  behind does not appear here.
                </DerivedNote>
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
