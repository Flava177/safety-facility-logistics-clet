import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router';
import type { NotificationActivation } from 'modules/emergency/api/dto';
import {
  activationsApi,
  emergencyDashboardApi,
  drillsApi,
} from 'modules/emergency/api/emergencyApi';
import {
  activationLive,
  afterActionOutstanding,
  awaitingApproval,
} from 'modules/emergency/api/workflow';
import { ActivationStatusChip } from 'modules/emergency/components/EmergencyFields';
import { formatElapsed, percentOf } from 'modules/emergency/components/emergencyFormat';
import { DerivedNote } from 'modules/fuel/components/Provenance';
import { humanise } from 'modules/fleet/api/enums';
import Alert from 'shared/components/Alert';
import Button from 'shared/components/Button';
import DataState from 'shared/components/DataState';
import DataTable, { CellStack, Column } from 'shared/components/DataTable';
import PageHeader from 'shared/components/PageHeader';
import SectionCard from 'shared/components/SectionCard';
import SiteSelect, { defaultSite } from 'shared/components/SiteSelect';
import StatCard from 'shared/components/StatCard';
import StatusChip from 'shared/components/StatusChip';
import { formatDateTime, formatNumber } from 'shared/components/format';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { emergencyPaths } from 'shared/layout/navigation';

/**
 * The emergency notification dashboard.
 *
 * Seven counts come from the service, and they are seven exception counts — active broadcasts,
 * break-glass sends, failed recipients, outstanding acknowledgements, escalations, all-clears
 * pending closure and completed drills. That is the right emphasis for a mass notification system:
 * in normal operation every figure here is zero, and any figure that is not is something somebody
 * has to act on.
 *
 * `stale` is the service's own verdict against a per-site freshness threshold, not this screen's,
 * so it is reported rather than recomputed. Everything under the counts is derived from the
 * activation register and says so.
 */
const EmergencyDashboardPage = () => {
  const navigate = useNavigate();
  const [siteCode, setSiteCode] = useState(defaultSite);

  const dashboard = useApiQuery(
    (signal) => emergencyDashboardApi.dashboard(siteCode, signal),
    [siteCode],
  );
  const activations = useApiQuery(
    (signal) => activationsApi.search({ siteCode }, signal),
    [siteCode],
  );
  const drills = useApiQuery((signal) => drillsApi.search(siteCode, signal), [siteCode]);

  const all = useMemo(() => activations.data ?? [], [activations.data]);
  const live = useMemo(() => all.filter(activationLive), [all]);
  const pending = useMemo(() => all.filter(awaitingApproval), [all]);
  const outstandingAfterAction = useMemo(() => all.filter(afterActionOutstanding), [all]);
  const allClearPending = useMemo(
    () => all.filter((activation) => activation.status === 'ALL_CLEAR_PENDING'),
    [all],
  );

  const completedDrills = useMemo(
    () => (drills.data ?? []).filter((drill) => drill.status === 'COMPLETED'),
    [drills.data],
  );
  const lastDrill = completedDrills[0];

  const counts = dashboard.data;

  const columns = useMemo<Column<NotificationActivation>[]>(
    () => [
      {
        key: 'activation',
        header: 'Activation',
        width: 260,
        cell: (row) => (
          <CellStack
            primary={row.activationNumber}
            secondary={row.incidentReference ?? 'No incident reference'}
          />
        ),
      },
      {
        key: 'mode',
        header: 'Mode',
        width: 130,
        cell: (row) => <StatusChip value={row.mode} />,
      },
      {
        key: 'priority',
        header: 'Priority',
        width: 110,
        hideBelowLg: true,
        cell: (row) => <StatusChip value={row.priority} />,
      },
      {
        key: 'channels',
        header: 'Channels',
        width: 110,
        align: 'right',
        hideBelowLg: true,
        cell: (row) => formatNumber(row.channels.length),
      },
      {
        key: 'sent',
        header: 'Time to send',
        width: 130,
        align: 'right',
        hideBelowLg: true,
        cell: (row) => formatElapsed(row.fastLaneMillis),
      },
      {
        key: 'status',
        header: 'Status',
        width: 170,
        align: 'right',
        cell: (row) => <ActivationStatusChip status={row.status} />,
      },
    ],
    [],
  );

  return (
    <div>
      <PageHeader
        title="Emergency notifications"
        subtitle="Live broadcasts, approvals outstanding and the obligations each one left behind."
        crumbs={[{ label: 'Emergency' }, { label: 'Dashboard' }]}
        meta={
          counts ? (
            <span>
              Service counts generated {formatDateTime(counts.generatedAt)}
              {counts.sourceUpdatedAt
                ? ` · source last changed ${formatDateTime(counts.sourceUpdatedAt)}`
                : ' · no source activity recorded'}
            </span>
          ) : undefined
        }
        actions={
          <>
            <Button
              variant="danger"
              startIcon="zap"
              onClick={() => navigate(emergencyPaths.breakGlass)}
            >
              Break glass
            </Button>
            <Button
              variant="outline"
              startIcon="refresh"
              onClick={() => {
                dashboard.refetch();
                activations.refetch();
                drills.refetch();
              }}
            >
              Refresh
            </Button>
          </>
        }
      />

      <div className="mb-5">
        <SectionCard>
          <div className="max-w-xs">
            <SiteSelect value={siteCode} onChange={setSiteCode} required />
          </div>
        </SectionCard>
      </div>

      <DataState
        loading={dashboard.initialising}
        error={dashboard.error}
        onRetry={dashboard.refetch}
        minHeight={320}
      >
        {counts && (
          <div className="space-y-5">
            {counts.stale && (
              <Alert variant="warning" title="These counts are older than the freshness threshold">
                The service reports its own source data as stale for this site. Figures below may
                lag what has actually happened — check the activation register directly before
                acting on a zero.
              </Alert>
            )}

            {live.length > 0 && (
              <Alert
                variant="error"
                title={`${live.length} broadcast${live.length === 1 ? ' is' : 's are'} live at this site`}
                action={
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => navigate(`${emergencyPaths.activations}?status=ACTIVE`)}
                  >
                    Open the register
                  </Button>
                }
              >
                A live activation has gone out and has not been stood down. Send the all-clear once
                the emergency is over — closure needs evidence and cannot be done from here.
              </Alert>
            )}

            {outstandingAfterAction.length > 0 && (
              <Alert
                variant="warning"
                title={`${outstandingAfterAction.length} break-glass broadcast${outstandingAfterAction.length === 1 ? '' : 's'} not yet accounted for`}
              >
                Each of these went out without approval and cannot be closed until somebody with the
                after-action approval permission records a justification against it.
              </Alert>
            )}

            <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
              <StatCard
                label="Active activations"
                value={formatNumber(counts.activeActivationCount)}
                icon="siren"
                tone={counts.activeActivationCount > 0 ? 'critical' : 'neutral'}
                caption="Live, escalated or awaiting closure"
                onClick={() => navigate(emergencyPaths.activations)}
              />
              <StatCard
                label="Break-glass sends"
                value={formatNumber(counts.breakGlassCount)}
                icon="zap"
                tone={counts.breakGlassCount > 0 ? 'caution' : 'neutral'}
                caption="Sent without prior approval, all time"
              />
              <StatCard
                label="Failed recipients"
                value={formatNumber(counts.failedRecipientCount)}
                icon="alert-triangle"
                tone={counts.failedRecipientCount > 0 ? 'critical' : 'neutral'}
                caption="Provider reported a failure or expiry"
              />
              <StatCard
                label="Acknowledgements outstanding"
                value={formatNumber(counts.ackPendingCount)}
                icon="clock"
                tone={counts.ackPendingCount > 0 ? 'caution' : 'neutral'}
                caption="Recipients targeted who have not replied"
              />
            </div>

            <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
              <StatCard
                label="Escalated"
                value={formatNumber(counts.escalatedCount)}
                icon="alert-circle"
                tone={counts.escalatedCount > 0 ? 'critical' : 'neutral'}
                caption="Acknowledgement SLA breached"
              />
              <StatCard
                label="All-clear pending closure"
                value={formatNumber(counts.allClearPendingCount)}
                icon="check-circle"
                tone={counts.allClearPendingCount > 0 ? 'caution' : 'neutral'}
                caption="Stood down, evidence not yet filed"
              />
              <StatCard
                label="Drills completed"
                value={formatNumber(counts.drillCount)}
                icon="target"
                caption="Rehearsals with figures recorded"
                onClick={() => navigate(emergencyPaths.drills)}
              />
              <StatCard
                label="Awaiting approval"
                value={formatNumber(pending.length)}
                icon="user-plus"
                tone={pending.length > 0 ? 'caution' : 'neutral'}
                caption="Submitted, nobody has decided yet"
                onClick={() => navigate(`${emergencyPaths.activations}?status=PENDING_APPROVAL`)}
              />
            </div>

            <SectionCard
              title="Live and pending activations"
              subtitle="Everything at this site that is not yet closed"
              flush
            >
              <DataState
                loading={activations.initialising}
                error={activations.error}
                onRetry={activations.refetch}
                empty={live.length + pending.length + allClearPending.length === 0}
                emptyTitle="Nothing outstanding"
                emptyHint="No activation at this site is live, awaiting approval or awaiting closure."
                minHeight={200}
              >
                <DataTable
                  rows={[...live, ...pending, ...allClearPending]}
                  columns={columns}
                  getRowId={(row) => row.id}
                  onRowClick={(row) => navigate(emergencyPaths.activationDetail(row.id))}
                  caption="Activations at this site that are live, awaiting approval or awaiting closure, with mode, priority, channel count, time to send and status."
                  dense
                />
              </DataState>
              <div className="px-5 pb-4">
                <DerivedNote>
                  Counted from the activation register rather than published by the service — the
                  dashboard endpoint returns totals, not the records behind them.
                </DerivedNote>
              </div>
            </SectionCard>

            <SectionCard
              title="Last completed drill"
              subtitle="What the notification path achieved when it was last rehearsed"
            >
              <DataState
                loading={drills.initialising}
                error={drills.error}
                onRetry={drills.refetch}
                empty={!lastDrill}
                emptyTitle="No drill has been completed"
                emptyHint="Start one from the drills screen — an untested notification path is an assumption."
                minHeight={140}
              >
                {lastDrill && (
                  <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
                    <StatCard
                      label="Reached"
                      value={percentOf(lastDrill.reachedRecipients, lastDrill.targetRecipients)}
                      icon="users"
                      caption={`${formatNumber(lastDrill.reachedRecipients)} of ${formatNumber(lastDrill.targetRecipients)}`}
                    />
                    <StatCard
                      label="Acknowledged"
                      value={percentOf(
                        lastDrill.acknowledgedRecipients,
                        lastDrill.targetRecipients,
                      )}
                      icon="check-circle"
                      tone={
                        lastDrill.acknowledgedRecipients / Math.max(lastDrill.targetRecipients, 1) <
                        0.8
                          ? 'caution'
                          : 'good'
                      }
                      caption={`${formatNumber(lastDrill.acknowledgedRecipients)} replied`}
                    />
                    <StatCard
                      label="Elapsed"
                      value={formatElapsed(lastDrill.activationMillis)}
                      icon="clock"
                      caption="Start to last recipient"
                    />
                    <StatCard
                      label="Drill"
                      value={lastDrill.drillNumber}
                      icon="target"
                      caption={
                        lastDrill.completedAt
                          ? `Completed ${formatDateTime(lastDrill.completedAt)}`
                          : humanise(lastDrill.status)
                      }
                      onClick={() => navigate(emergencyPaths.drills)}
                    />
                  </div>
                )}
              </DataState>
            </SectionCard>
          </div>
        )}
      </DataState>
    </div>
  );
};

export default EmergencyDashboardPage;
