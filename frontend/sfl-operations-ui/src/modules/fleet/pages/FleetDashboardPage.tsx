import { ReactNode, useMemo, useState } from 'react';
import { useNavigate } from 'react-router';
import dayjs from 'dayjs';
import { DashboardDrilldownRow, TripResponse, WorkflowItemResponse } from 'modules/fleet/api/dto';
import { OPERATING_MODES, OperatingMode, humanise } from 'modules/fleet/api/enums';
import {
  DRILLDOWN_INDICATORS,
  DrilldownIndicator,
  dashboardApi,
  tripsApi,
  workflowApi,
} from 'modules/fleet/api/fleetApi';
import ActivityChart, { ActivityPoint } from 'modules/fleet/charts/ActivityChart';
import ExceptionsChart from 'modules/fleet/charts/ExceptionsChart';
import ReadinessChart from 'modules/fleet/charts/ReadinessChart';
import DrilldownDrawer from 'modules/fleet/components/DrilldownDrawer';

import Button from 'shared/components/Button';
import DataState from 'shared/components/DataState';
import DataTable, { CellStack, Column } from 'shared/components/DataTable';
import FilterBar from 'shared/components/FilterBar';
import Icon from 'shared/components/Icon';
import { cn } from 'shared/components/cn';
import PageHeader from 'shared/components/PageHeader';
import SectionCard from 'shared/components/SectionCard';
import SiteSelect, { defaultSite } from 'shared/components/SiteSelect';
import StatCard from 'shared/components/StatCard';
import StatusChip from 'shared/components/StatusChip';
import { EnumSelect } from 'shared/components/fields';
import { formatDateTime } from 'shared/components/format';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { fleetPaths } from 'shared/layout/navigation';

const ACTIVITY_DAYS = 14;

/** Buckets records into one entry per day so the chart window is fixed even where data is sparse. */
const bucketByDay = (
  trips: TripResponse[],
  workflow: WorkflowItemResponse[],
  days: number,
): ActivityPoint[] => {
  const start = dayjs()
    .startOf('day')
    .subtract(days - 1, 'day');
  const buckets = new Map<string, ActivityPoint>();

  for (let offset = 0; offset < days; offset += 1) {
    const day = start.add(offset, 'day');
    buckets.set(day.format('YYYY-MM-DD'), { label: day.format('D MMM'), trips: 0, workflow: 0 });
  }

  const add = (iso: string | null | undefined, key: 'trips' | 'workflow') => {
    if (!iso) {
      return;
    }
    const bucket = buckets.get(dayjs(iso).format('YYYY-MM-DD'));
    if (bucket) {
      bucket[key] += 1;
    }
  };

  trips.forEach((trip) => add(trip.plannedStart, 'trips'));
  workflow.forEach((item) => add(item.createdAt, 'workflow'));

  return [...buckets.values()];
};

/**
 * Page-header metadata: when the snapshot was taken, what it covers, what it reconciled.
 *
 * These are facts about the query rather than statuses, so they carry no tone — three coloured chips
 * directly above a KPI row is the loudest thing the header can do, and it spends attention on
 * provenance instead of on the numbers. Staleness is the one thing here that is a status, and it is
 * marked with an icon and a name assistive technology can read: the previous build said it by
 * turning the chip amber, which makes colour the only carrier of the meaning (SC 1.4.1).
 */
const MetaChip = ({ children, stale }: { children: ReactNode; stale?: boolean }) => (
  <span className="inline-flex items-center gap-1.5 rounded-full bg-gray-100 px-2.5 py-1 text-theme-xs font-medium text-gray-700">
    {stale && (
      <>
        <Icon name="alert-triangle" size={13} className="shrink-0 text-warning-700" />
        <span className="sr-only">May be out of date.</span>
      </>
    )}
    {children}
  </span>
);

/**
 * The Fleet operations workspace.
 *
 * Indicators come from the service's own dashboard snapshot; the trend and the sparklines are
 * bucketed from the trip and workflow records themselves, because the service exposes no
 * time-series endpoint. Nothing on this page is synthetic — where there is no history to show, no
 * trend is drawn.
 */
const FleetDashboardPage = () => {
  const navigate = useNavigate();
  const [siteCode, setSiteCode] = useState(defaultSite);
  const [operatingMode, setOperatingMode] = useState<OperatingMode | ''>('');
  const [drilldown, setDrilldown] = useState<DrilldownIndicator | null>(null);

  const windowStart = useMemo(
    () =>
      dayjs()
        .startOf('day')
        .subtract(ACTIVITY_DAYS - 1, 'day')
        .toISOString(),
    [],
  );
  const windowEnd = useMemo(() => dayjs().endOf('day').toISOString(), []);

  const snapshot = useApiQuery(
    (signal) =>
      dashboardApi.operations(
        { siteCode: siteCode || undefined, operatingMode: operatingMode || undefined },
        signal,
      ),
    [siteCode, operatingMode],
  );

  const recentTrips = useApiQuery(
    (signal) =>
      tripsApi.search(
        {
          siteCode: siteCode || undefined,
          operatingMode: operatingMode || undefined,
          from: windowStart,
          to: windowEnd,
          size: 200,
        },
        signal,
      ),
    [siteCode, operatingMode, windowStart, windowEnd],
  );

  const recentWorkflow = useApiQuery(
    (signal) =>
      workflowApi.search(
        { siteCode: siteCode || undefined, from: windowStart, to: windowEnd, size: 200 },
        signal,
      ),
    [siteCode, windowStart, windowEnd],
  );

  const activeTrips = useApiQuery(
    (signal) =>
      tripsApi.search({ siteCode: siteCode || undefined, status: 'IN_PROGRESS', size: 6 }, signal),
    [siteCode],
  );

  const escalated = useApiQuery(
    (signal) =>
      workflowApi.search({ siteCode: siteCode || undefined, escalatedOnly: true, size: 6 }, signal),
    [siteCode],
  );

  /**
   * The documents behind the expired-compliance indicator.
   *
   * There is no compliance-document search endpoint — documents are only readable per vehicle or
   * through this indicator drilldown — so the panel lists what the service itself counts, and never
   * an expiry horizon the service was not asked about.
   */
  const expiredCompliance = useApiQuery(
    (signal) =>
      dashboardApi.drilldown(
        DRILLDOWN_INDICATORS.EXPIRED_COMPLIANCE,
        { siteCode: siteCode || undefined },
        signal,
      ),
    [siteCode],
  );

  const indicators = snapshot.data?.indicators;

  const activity = useMemo(
    () =>
      bucketByDay(
        recentTrips.data?.content ?? [],
        recentWorkflow.data?.content ?? [],
        ACTIVITY_DAYS,
      ),
    [recentTrips.data, recentWorkflow.data],
  );

  const tripSeries = useMemo(() => activity.map((point) => point.trips), [activity]);
  const workflowSeries = useMemo(() => activity.map((point) => point.workflow), [activity]);

  const readinessSlices = useMemo(() => {
    if (!indicators || !snapshot.data) {
      return [];
    }
    const total = snapshot.data.reconciliation.vehicles;
    const available = indicators.vehiclesAvailable;
    const blocked = indicators.readinessBlockers;
    return [
      { name: 'Available', value: available, tone: 'ready' as const },
      {
        name: 'Committed',
        value: Math.max(total - available - blocked, 0),
        tone: 'caution' as const,
      },
      { name: 'Readiness blocked', value: blocked, tone: 'blocked' as const },
    ];
  }, [indicators, snapshot.data]);

  const exceptionBars = useMemo(
    () =>
      indicators
        ? [
            { label: 'Expired compliance', value: indicators.expiredCompliance, critical: true },
            { label: 'Service due', value: indicators.serviceDue },
            {
              label: 'Assignment conflicts',
              value: indicators.assignmentConflicts,
              critical: true,
            },
            { label: 'Readiness blockers', value: indicators.readinessBlockers, critical: true },
            { label: 'Open workflow', value: indicators.openWorkflowItems },
            { label: 'Escalated', value: indicators.escalatedWorkflowItems, critical: true },
            { label: 'Dead letters', value: indicators.integrationDeadLetters, critical: true },
          ]
        : [],
    [indicators],
  );

  const tripColumns = useMemo<Column<TripResponse>[]>(
    () => [
      {
        key: 'trip',
        header: 'Trip',
        width: 260,
        cell: (row) => (
          <CellStack
            primary={`${row.tripNumber} · ${row.origin} → ${row.destination}`}
            secondary={`Started ${formatDateTime(row.actualStart ?? row.plannedStart)} · ${humanise(
              row.operatingMode,
            )}`}
          />
        ),
      },
      {
        key: 'status',
        header: 'Status',
        width: 130,
        align: 'right',
        cell: (row) => <StatusChip value={row.status} />,
      },
    ],
    [],
  );

  const workflowColumns = useMemo<Column<WorkflowItemResponse>[]>(
    () => [
      {
        key: 'item',
        header: 'Workflow item',
        width: 260,
        cell: (row) => (
          <CellStack
            primary={`${row.workflowNumber} · ${row.title}`}
            secondary={`SLA due ${formatDateTime(row.slaDueAt)} · level ${row.escalationLevel}`}
          />
        ),
      },
      {
        key: 'priority',
        header: 'Priority',
        width: 120,
        align: 'right',
        cell: (row) => <StatusChip value={row.priority} />,
      },
    ],
    [],
  );

  const complianceColumns = useMemo<Column<DashboardDrilldownRow>[]>(
    () => [
      {
        key: 'record',
        header: 'Document',
        width: 260,
        cell: (row) => <CellStack primary={row.summary} secondary={row.resourceType} />,
      },
      {
        key: 'site',
        header: 'Site',
        width: 110,
        align: 'right',
        hideBelowLg: true,
        cell: (row) => row.siteCode,
      },
    ],
    [],
  );

  const refreshAll = () => {
    snapshot.refetch();
    recentTrips.refetch();
    recentWorkflow.refetch();
    activeTrips.refetch();
    escalated.refetch();
    expiredCompliance.refetch();
  };

  return (
    <div>
      <PageHeader
        title="Fleet operations"
        subtitle="Readiness, movement and open exceptions across your site scope."
        crumbs={[{ label: 'Fleet' }]}
        actions={
          /*
           * Refresh only. A "Plan a trip" button here could not open the create dialog — it lives
           * on the trip queue — so it merely carried the operator to that page to press the same
           * button again. The action belongs where it works.
           */
          <Button variant="outline" startIcon="refresh" onClick={refreshAll}>
            Refresh
          </Button>
        }
        meta={
          snapshot.data && (
            <div className="flex flex-wrap items-center gap-2">
              <MetaChip stale={snapshot.data.stale}>
                {`Snapshot ${formatDateTime(snapshot.data.generatedAt)}`}
              </MetaChip>
              <MetaChip>{`Scope ${snapshot.data.scopeKey}`}</MetaChip>
              <MetaChip>
                {`${snapshot.data.reconciliation.vehicles} vehicles · ${snapshot.data.reconciliation.trips} trips`}
              </MetaChip>
            </div>
          )
        }
      />

      <SectionCard flush>
        <FilterBar>
          <SiteSelect
            value={siteCode}
            onChange={setSiteCode}
            allowEmpty
            emptyLabel="All sites in scope"
          />
          <EnumSelect
            label="Operating mode"
            value={operatingMode}
            options={OPERATING_MODES}
            onChange={(value) => setOperatingMode(value)}
            allowEmpty
          />
        </FilterBar>
      </SectionCard>

      <div className="mt-5">
        <DataState
          loading={snapshot.initialising}
          error={snapshot.error}
          onRetry={snapshot.refetch}
          minHeight={360}
        >
          {snapshot.data && indicators && (
            <div className="space-y-5">
              {snapshot.data.warnings.length > 0 && (
                <div className="flex items-start gap-2.5 rounded-lg border border-gray-200 bg-white px-4 py-3">
                  <Icon
                    name={snapshot.data.stale ? 'clock' : 'info'}
                    size={16}
                    className={cn(
                      'mt-0.5 shrink-0',
                      snapshot.data.stale ? 'text-warning-700' : 'text-teal-700',
                    )}
                    aria-hidden="true"
                  />
                  <div className="min-w-0 text-theme-sm text-gray-700">
                    {snapshot.data.warnings.map((warning) => (
                      <p key={warning}>{warning}</p>
                    ))}
                  </div>
                </div>
              )}

              {/*
               * Eight indicators, one grid. Tone is spent only where the measure is an exception
               * class in its own right — a blocker, an escalation, something overdue — and only
               * while the count is non-zero, so a clean fleet reads as eight quiet cards rather
               * than eight green ones. The figures themselves are navy throughout.
               */}
              <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
                <StatCard
                  label="Vehicles available"
                  value={indicators.vehiclesAvailable}
                  icon="truck"
                  caption={`of ${snapshot.data.reconciliation.vehicles} in the register`}
                />
                <StatCard
                  label="Readiness blocked"
                  value={indicators.readinessBlockers}
                  icon="alert-circle"
                  tone={indicators.readinessBlockers > 0 ? 'critical' : 'neutral'}
                  caption="Cannot be assigned"
                  onClick={() => setDrilldown('READINESS_BLOCKERS')}
                />
                <StatCard
                  label="Trips planned"
                  value={tripSeries.reduce((total, count) => total + count, 0)}
                  icon="route"
                  caption={`Last ${ACTIVITY_DAYS} days`}
                />
                <StatCard
                  label="Exceptions raised"
                  value={workflowSeries.reduce((total, count) => total + count, 0)}
                  icon="workflow"
                  tone={indicators.escalatedWorkflowItems > 0 ? 'caution' : 'neutral'}
                  caption={`${indicators.openWorkflowItems} open right now`}
                />
                <StatCard
                  label="Expired compliance"
                  value={indicators.expiredCompliance}
                  icon="shield-check"
                  tone={indicators.expiredCompliance > 0 ? 'critical' : 'neutral'}
                  caption="Documents past expiry"
                  onClick={() => setDrilldown('EXPIRED_COMPLIANCE')}
                />
                <StatCard
                  label="Service due"
                  value={indicators.serviceDue}
                  icon="wrench"
                  tone={indicators.serviceDue > 0 ? 'caution' : 'neutral'}
                  caption="Due or overdue"
                  onClick={() => setDrilldown('SERVICE_DUE')}
                />
                <StatCard
                  label="Assignment conflicts"
                  value={indicators.assignmentConflicts}
                  icon="alert-triangle"
                  tone={indicators.assignmentConflicts > 0 ? 'critical' : 'neutral'}
                  caption="Double-booked vehicle or driver"
                  onClick={() => setDrilldown('ASSIGNMENT_CONFLICTS')}
                />
                <StatCard
                  label="Integration dead letters"
                  value={indicators.integrationDeadLetters}
                  icon="cloud"
                  tone={indicators.integrationDeadLetters > 0 ? 'critical' : 'neutral'}
                  caption="Awaiting replay"
                />
              </div>

              <div className="grid gap-5 xl:grid-cols-3">
                <SectionCard
                  className="xl:col-span-2"
                  title="Operational activity"
                  subtitle={`Trips planned and exceptions raised, by day · last ${ACTIVITY_DAYS} days`}
                >
                  <DataState
                    loading={recentTrips.initialising || recentWorkflow.initialising}
                    error={recentTrips.error ?? recentWorkflow.error}
                    onRetry={() => {
                      recentTrips.refetch();
                      recentWorkflow.refetch();
                    }}
                    minHeight={280}
                  >
                    <ActivityChart points={activity} />
                    <p className="mt-3 text-theme-xs text-gray-600">
                      Bucketed by day from the trip and workflow records returned for this{' '}
                      {ACTIVITY_DAYS}-day window; the fleet service exposes no time-series endpoint.
                    </p>
                  </DataState>
                </SectionCard>

                <SectionCard title="Fleet availability" subtitle="Vehicles in the current scope">
                  <ReadinessChart slices={readinessSlices} centreLabel="Vehicles" height={280} />
                  <p className="mt-2 text-theme-xs text-gray-600">
                    Available and blocked are snapshot indicators; committed is the remainder of the{' '}
                    {snapshot.data.reconciliation.vehicles} vehicles reconciled in this scope.
                  </p>
                </SectionCard>
              </div>

              <div className="grid gap-5 xl:grid-cols-3">
                <SectionCard
                  className="xl:col-span-2"
                  title="Active trips"
                  subtitle="Currently on the road"
                  actions={
                    <Button
                      size="sm"
                      variant="ghost"
                      endIcon="chevron-right"
                      onClick={() => navigate(fleetPaths.trips)}
                    >
                      View queue
                    </Button>
                  }
                  flush
                >
                  <DataState
                    loading={activeTrips.initialising}
                    error={activeTrips.error}
                    empty={(activeTrips.data?.content.length ?? 0) === 0}
                    emptyTitle="No active trips"
                    emptyHint="Nothing is on the road in this scope right now."
                    onRetry={activeTrips.refetch}
                    minHeight={160}
                  >
                    <DataTable
                      rows={activeTrips.data?.content ?? []}
                      columns={tripColumns}
                      getRowId={(row) => row.id}
                      loading={activeTrips.loading}
                      onRowClick={(row) => navigate(fleetPaths.tripDetail(row.id))}
                      caption="Trips in progress in the current site scope, with their status."
                      dense
                    />
                  </DataState>
                </SectionCard>

                <SectionCard title="Open exceptions" subtitle="What needs attention today">
                  <ExceptionsChart bars={exceptionBars} height={270} />
                </SectionCard>
              </div>

              <div className="grid gap-5 xl:grid-cols-2">
                <SectionCard
                  title="Escalated workflow"
                  subtitle="Past SLA or manually escalated"
                  actions={
                    <Button
                      size="sm"
                      variant="ghost"
                      endIcon="chevron-right"
                      onClick={() => navigate(fleetPaths.workflow)}
                    >
                      View queue
                    </Button>
                  }
                  flush
                >
                  <DataState
                    loading={escalated.initialising}
                    error={escalated.error}
                    empty={(escalated.data?.content.length ?? 0) === 0}
                    emptyTitle="Nothing escalated"
                    emptyHint="No workflow item has breached its SLA in this scope."
                    onRetry={escalated.refetch}
                    minHeight={160}
                  >
                    <DataTable
                      rows={escalated.data?.content ?? []}
                      columns={workflowColumns}
                      getRowId={(row) => row.id}
                      loading={escalated.loading}
                      onRowClick={(row) => navigate(fleetPaths.workflowDetail(row.id))}
                      caption="Escalated workflow items in the current site scope, with their priority."
                      dense
                    />
                  </DataState>
                </SectionCard>

                <SectionCard
                  title="Compliance exceptions"
                  subtitle="Records behind the expired-compliance indicator"
                  actions={
                    <Button
                      size="sm"
                      variant="ghost"
                      endIcon="chevron-right"
                      onClick={() => navigate(fleetPaths.compliance)}
                    >
                      Compliance
                    </Button>
                  }
                  flush
                >
                  <DataState
                    loading={expiredCompliance.initialising}
                    error={expiredCompliance.error}
                    empty={(expiredCompliance.data?.length ?? 0) === 0}
                    emptyTitle="No expired documents"
                    emptyHint="Nothing in this scope is past its expiry date."
                    onRetry={expiredCompliance.refetch}
                    minHeight={160}
                  >
                    <DataTable
                      rows={expiredCompliance.data ?? []}
                      columns={complianceColumns}
                      getRowId={(row) => `${row.resourceType}-${row.resourceId}`}
                      loading={expiredCompliance.loading}
                      caption="Compliance documents past their expiry date, with the site that holds them."
                      dense
                    />
                  </DataState>
                </SectionCard>
              </div>
            </div>
          )}
        </DataState>
      </div>

      <DrilldownDrawer
        indicator={drilldown}
        siteCode={siteCode || undefined}
        onClose={() => setDrilldown(null)}
      />
    </div>
  );
};

export default FleetDashboardPage;
