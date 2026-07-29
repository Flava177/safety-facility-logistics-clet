import { ReactNode, useMemo, useState } from 'react';
import { useNavigate } from 'react-router';
import dayjs from 'dayjs';
import { DriverLogbook, FuelAnomalyCase, FuelTransaction } from 'modules/fuel/api/dto';
import { humanise } from 'modules/fleet/api/enums';
import {
  MAX_PAGE_SIZE,
  driverLogbooksApi,
  fuelAnomaliesApi,
  fuelDashboardApi,
  fuelTransactionsApi,
} from 'modules/fuel/api/fuelApi';
import { anomalySlaBreached } from 'modules/fuel/api/workflow';
import AnomalyMixChart, { AnomalyBar } from 'modules/fuel/charts/AnomalyMixChart';
import ReconciliationChart from 'modules/fuel/charts/ReconciliationChart';
import SpendChart, { SpendPoint } from 'modules/fuel/charts/SpendChart';
import { DerivedNote } from 'modules/fuel/components/Provenance';
import {
  currencyCodeOf,
  formatDueIn,
  formatMoney,
  formatQuantity,
} from 'modules/fuel/components/fuelFormat';
import Alert from 'shared/components/Alert';
import Button from 'shared/components/Button';
import DataState from 'shared/components/DataState';
import DataTable, { CellStack, Column } from 'shared/components/DataTable';
import FilterBar from 'shared/components/FilterBar';
import Icon from 'shared/components/Icon';
import PageHeader from 'shared/components/PageHeader';
import SectionCard from 'shared/components/SectionCard';
import SiteSelect, { defaultSite } from 'shared/components/SiteSelect';
import StatCard from 'shared/components/StatCard';
import StatusChip from 'shared/components/StatusChip';
import { formatDateTime, formatNumber } from 'shared/components/format';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { fuelPaths } from 'shared/layout/navigation';

const SPEND_DAYS = 14;

/**
 * Buckets transactions into one point per day so the chart window is fixed even where a day has no
 * fuel at all — a gap in the line would otherwise read as missing data rather than a quiet day.
 */
const bucketByDay = (transactions: FuelTransaction[], days: number): SpendPoint[] => {
  const start = dayjs()
    .startOf('day')
    .subtract(days - 1, 'day');
  const buckets = new Map<string, SpendPoint>();

  for (let offset = 0; offset < days; offset += 1) {
    const day = start.add(offset, 'day');
    buckets.set(day.format('YYYY-MM-DD'), { label: day.format('D MMM'), spend: 0, volume: 0 });
  }

  transactions.forEach((transaction) => {
    const bucket = buckets.get(dayjs(transaction.occurredAt).format('YYYY-MM-DD'));
    if (bucket) {
      bucket.spend = Math.round((bucket.spend + (transaction.totalCost ?? 0)) * 100) / 100;
      bucket.volume = Math.round((bucket.volume + (transaction.quantity ?? 0)) * 1000) / 1000;
    }
  });

  return [...buckets.values()];
};

/** Header metadata: when the snapshot was taken and what it covers. Facts, so no tone. */
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
 * The Fuel & Driver Logbooks workspace.
 *
 * Every indicator here is published by the service and counted across the whole site. That was not
 * true when this screen was first built: the dashboard endpoint returned five transaction figures,
 * so the anomaly, logbook and reconciliation counts had to be derived from whatever list the console
 * could fetch, and were captioned to say so. The endpoint counts them itself now, so the derived
 * section is gone and the only remaining caption is on the spend trend, which really is bucketed
 * here — there is still no time-series endpoint.
 *
 * `siteCode` is required by every fuel endpoint, so this page is single-site by construction. There
 * is no "all sites" option, because there is no query that would answer it.
 */
const FuelDashboardPage = () => {
  const navigate = useNavigate();
  const [siteCode, setSiteCode] = useState(defaultSite);

  const windowStart = useMemo(
    () =>
      dayjs()
        .startOf('day')
        .subtract(SPEND_DAYS - 1, 'day')
        .toISOString(),
    [],
  );
  const windowEnd = useMemo(() => dayjs().endOf('day').toISOString(), []);

  const snapshot = useApiQuery(
    (signal) => fuelDashboardApi.snapshot(siteCode, signal),
    [siteCode],
  );

  /** The spend trend still needs the records themselves — there is no time-series endpoint. */
  const recentTransactions = useApiQuery(
    (signal) =>
      fuelTransactionsApi.search(
        { siteCode, from: windowStart, to: windowEnd, size: MAX_PAGE_SIZE },
        signal,
      ),
    [siteCode, windowStart, windowEnd],
  );

  /**
   * Open cases, a page at a time.
   *
   * A page is enough for the exception list, which shows six, but the by-type chart wants the whole
   * set and there is no aggregation endpoint — so it asks for the largest page the service allows
   * and the chart says how many cases it covers. The *counts* above it come from the dashboard
   * snapshot, which counts across the site.
   */
  const anomalies = useApiQuery(
    (signal) =>
      fuelAnomaliesApi.search({ siteCode, openOnly: true, size: MAX_PAGE_SIZE }, signal),
    [siteCode],
  );

  const logbooks = useApiQuery(
    (signal) => driverLogbooksApi.search({ siteCode, status: 'SUBMITTED', size: 6 }, signal),
    [siteCode],
  );

  const unreconciled = useApiQuery(
    (signal) => fuelTransactionsApi.search({ siteCode, status: 'RECEIVED', size: 6 }, signal),
    [siteCode],
  );

  const data = snapshot.data;

  const openAnomalies = useMemo(() => anomalies.data?.content ?? [], [anomalies.data]);
  const pendingReviews = useMemo(() => logbooks.data?.content ?? [], [logbooks.data]);

  /** The currency and unit the site actually transacts in, taken from its own records. */
  const currencyCode = currencyCodeOf(recentTransactions.data?.content?.[0]?.currency);
  const quantityUnit = recentTransactions.data?.content?.[0]?.quantityUnit ?? '';

  const spendPoints = useMemo(
    () => bucketByDay(recentTransactions.data?.content ?? [], SPEND_DAYS),
    [recentTransactions.data],
  );

  const reconciliationSlices = useMemo(() => {
    if (!data) {
      return [];
    }
    const outstanding = Math.max(data.transactionCount - data.reconciledCount - data.exceptionCount, 0);
    return [
      { name: 'Reconciled', value: data.reconciledCount, tone: 'ready' as const },
      { name: 'In exception', value: data.exceptionCount, tone: 'blocked' as const },
      { name: 'Not yet reconciled', value: outstanding, tone: 'neutral' as const },
    ];
  }, [data]);

  const anomalyBars = useMemo<AnomalyBar[]>(() => {
    const counts = new Map<string, { total: number; urgent: number }>();
    openAnomalies.forEach((anomaly) => {
      const entry = counts.get(anomaly.type) ?? { total: 0, urgent: 0 };
      entry.total += 1;
      if (anomaly.material || anomalySlaBreached(anomaly)) {
        entry.urgent += 1;
      }
      counts.set(anomaly.type, entry);
    });
    return [...counts.entries()]
      .sort((left, right) => right[1].total - left[1].total)
      .slice(0, 8)
      .flatMap(([type, entry]) => {
        const bars: AnomalyBar[] = [];
        if (entry.urgent > 0) {
          bars.push({ label: humanise(type), value: entry.urgent, urgent: true });
        }
        if (entry.total - entry.urgent > 0) {
          bars.push({ label: humanise(type), value: entry.total - entry.urgent });
        }
        return bars;
      });
  }, [openAnomalies]);

  const anomalyColumns = useMemo<Column<FuelAnomalyCase>[]>(
    () => [
      {
        key: 'case',
        header: 'Case',
        width: 250,
        cell: (row) => (
          <CellStack
            primary={`${row.anomalyNumber} · ${humanise(row.type)}`}
            secondary={`SLA ${formatDueIn(row.slaDueAt)} · ${row.assignee ?? 'unassigned'}`}
          />
        ),
      },
      {
        key: 'severity',
        header: 'Severity',
        width: 120,
        align: 'right',
        cell: (row) => <StatusChip value={row.severity} />,
      },
    ],
    [],
  );

  const logbookColumns = useMemo<Column<DriverLogbook>[]>(
    () => [
      {
        key: 'logbook',
        header: 'Logbook',
        width: 250,
        cell: (row) => (
          <CellStack
            primary={`${row.logbookNumber} · ${row.origin} → ${row.destination}`}
            secondary={`Submitted ${formatDateTime(row.submittedAt)}`}
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

  const unreconciledColumns = useMemo<Column<FuelTransaction>[]>(
    () => [
      {
        key: 'transaction',
        header: 'Transaction',
        width: 250,
        cell: (row) => (
          <CellStack
            primary={`${row.vendorReference} · ${formatQuantity(row.quantity, row.quantityUnit)}`}
            secondary={`${formatDateTime(row.occurredAt)} · ${row.sourceSystem}`}
          />
        ),
      },
      {
        key: 'cost',
        header: 'Cost',
        width: 130,
        align: 'right',
        cell: (row) => formatMoney(row.totalCost, row.currency),
      },
    ],
    [],
  );

  const refreshAll = () => {
    snapshot.refetch();
    recentTransactions.refetch();
    anomalies.refetch();
    logbooks.refetch();
    unreconciled.refetch();
  };

  return (
    <div>
      <PageHeader
        title="Fuel and driver logbooks"
        subtitle="Spend, reconciliation standing and the exceptions waiting on somebody at this site."
        crumbs={[{ label: 'Fuel' }]}
        actions={
          <Button variant="outline" startIcon="refresh" onClick={refreshAll}>
            Refresh
          </Button>
        }
        meta={
          data && (
            <div className="flex flex-wrap items-center gap-2">
              <MetaChip stale={data.stale}>
                {data.sourceUpdatedAt
                  ? `Transactions last changed ${formatDateTime(data.sourceUpdatedAt)}`
                  : 'No transactions recorded at this site'}
              </MetaChip>
              <MetaChip>{`Site ${siteCode}`}</MetaChip>
              <MetaChip>{`${formatNumber(data.transactionCount)} transactions in scope`}</MetaChip>
            </div>
          )
        }
      />

      <SectionCard flush>
        <FilterBar>
          <SiteSelect
            value={siteCode}
            onChange={setSiteCode}
            required
            helperText="Every fuel endpoint is scoped to one site."
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
          {data && (
            <div className="space-y-5">
              {data.stale && (
                <Alert variant="warning" title="This snapshot may be out of date">
                  The service marks the fuel dashboard stale when no transaction has changed in the
                  last fifteen minutes.
                  {data.sourceUpdatedAt
                    ? ` The most recent change was ${formatDateTime(data.sourceUpdatedAt)}.`
                    : ' No transaction has ever been recorded at this site.'}
                </Alert>
              )}

              {/*
               * Nine indicators, all published by the service. The split into "from the snapshot"
               * and "counted by this console" that this row used to carry is gone: the dashboard
               * endpoint now counts the anomaly, logbook and import figures itself, across the whole
               * site rather than across whatever page the console happened to fetch.
               */}
              <div>
                <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-5">
                  <StatCard
                    label="Transactions"
                    value={formatNumber(data.transactionCount)}
                    icon="coins"
                    caption="All statuses, all time"
                    onClick={() => navigate(fuelPaths.transactions)}
                  />
                  <StatCard
                    label="Fuel spend"
                    value={formatMoney(data.fuelSpend, currencyCode)}
                    icon="fuel"
                    caption="Sum of recorded totals"
                  />
                  <StatCard
                    label="Fuel volume"
                    value={formatQuantity(data.fuelVolume, quantityUnit)}
                    icon="gauge"
                    caption="Sum of recorded quantities"
                  />
                  <StatCard
                    label="Reconciled"
                    value={formatNumber(data.reconciledCount)}
                    icon="check-circle"
                    tone={
                      data.transactionCount > 0 && data.reconciledCount === data.transactionCount
                        ? 'good'
                        : 'neutral'
                    }
                    caption="Passed every policy rule"
                    onClick={() => navigate(`${fuelPaths.transactions}?status=RECONCILED`)}
                  />
                  <StatCard
                    label="In exception"
                    value={formatNumber(data.exceptionCount)}
                    icon="alert-circle"
                    tone={data.exceptionCount > 0 ? 'critical' : 'neutral'}
                    caption="Failed at least one rule"
                    onClick={() => navigate(`${fuelPaths.transactions}?status=EXCEPTION`)}
                  />
                </div>
                <div className="mt-4 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
                  <StatCard
                    label="Open anomaly cases"
                    value={formatNumber(data.openAnomalies)}
                    icon="alert-triangle"
                    tone={data.openAnomalies > 0 ? 'caution' : 'neutral'}
                    caption={`${formatNumber(data.unassignedAnomalies)} unassigned`}
                    onClick={() => navigate(fuelPaths.anomalies)}
                  />
                  <StatCard
                    label="Breaching SLA"
                    value={formatNumber(data.anomaliesBreachingSla)}
                    icon="clock"
                    tone={data.anomaliesBreachingSla > 0 ? 'critical' : 'neutral'}
                    caption={`${formatNumber(data.materialOpenAnomalies)} material`}
                    onClick={() => navigate(fuelPaths.anomalies)}
                  />
                  <StatCard
                    label="Logbooks awaiting review"
                    value={formatNumber(data.pendingLogbookReviews)}
                    icon="book"
                    tone={data.pendingLogbookReviews > 0 ? 'caution' : 'neutral'}
                    caption={`${formatNumber(data.draftLogbooks)} still in draft`}
                    onClick={() => navigate(`${fuelPaths.logbooks}?status=SUBMITTED`)}
                  />
                  <StatCard
                    label="Awaiting reconciliation"
                    value={formatNumber(data.awaitingReconciliation)}
                    icon="scale"
                    tone={data.awaitingReconciliation > 0 ? 'caution' : 'neutral'}
                    caption="Received but not yet run"
                    onClick={() => navigate(fuelPaths.reconciliation)}
                  />
                </div>
              </div>

              <div className="grid gap-5 xl:grid-cols-3">
                <SectionCard
                  className="xl:col-span-2"
                  title="Fuel spend and volume"
                  subtitle={`By day · last ${SPEND_DAYS} days`}
                >
                  <DataState
                    loading={recentTransactions.initialising}
                    error={recentTransactions.error}
                    onRetry={recentTransactions.refetch}
                    minHeight={280}
                  >
                    <SpendChart
                      points={spendPoints}
                      currencyCode={currencyCode}
                      unit={quantityUnit}
                    />
                    <DerivedNote>
                      Bucketed by day from the {recentTransactions.data?.totalElements ?? 0} transactions
                      returned for this window. The fuel service exposes no time-series endpoint.
                    </DerivedNote>
                  </DataState>
                </SectionCard>

                <SectionCard
                  title="Reconciliation standing"
                  subtitle="Every transaction at this site"
                >
                  <ReconciliationChart slices={reconciliationSlices} />
                  <DerivedNote>
                    Reconciled and in-exception are snapshot figures. Not-yet-reconciled is the
                    remainder of the {formatNumber(data.transactionCount)} transactions the snapshot
                    counts.
                  </DerivedNote>
                </SectionCard>
              </div>

              <div className="grid gap-5 xl:grid-cols-2">
                <SectionCard
                  title="Open anomaly cases"
                  subtitle="Oldest SLA first"
                  actions={
                    <Button
                      size="sm"
                      variant="ghost"
                      endIcon="chevron-right"
                      onClick={() => navigate(fuelPaths.anomalies)}
                    >
                      View queue
                    </Button>
                  }
                  flush
                >
                  <DataState
                    loading={anomalies.initialising}
                    error={anomalies.error}
                    empty={openAnomalies.length === 0}
                    emptyTitle="No open anomaly cases"
                    emptyHint="Nothing at this site is waiting on an explanation or a decision."
                    onRetry={anomalies.refetch}
                    minHeight={160}
                  >
                    <DataTable
                      rows={openAnomalies.slice(0, 6)}
                      columns={anomalyColumns}
                      getRowId={(row) => row.id}
                      loading={anomalies.loading}
                      onRowClick={(row) => navigate(fuelPaths.anomalyDetail(row.id))}
                      caption="Open fuel anomaly cases at this site, ordered by SLA due time, with their severity."
                      dense
                    />
                  </DataState>
                </SectionCard>

                <SectionCard
                  title="Logbooks awaiting review"
                  subtitle="With a reviewer rather than a driver"
                  actions={
                    <Button
                      size="sm"
                      variant="ghost"
                      endIcon="chevron-right"
                      onClick={() => navigate(fuelPaths.logbooks)}
                    >
                      View register
                    </Button>
                  }
                  flush
                >
                  <DataState
                    loading={logbooks.initialising}
                    error={logbooks.error}
                    empty={pendingReviews.length === 0}
                    emptyTitle="Nothing awaiting review"
                    emptyHint="No logbook at this site is submitted or under review."
                    onRetry={logbooks.refetch}
                    minHeight={160}
                  >
                    <DataTable
                      rows={pendingReviews.slice(0, 6)}
                      columns={logbookColumns}
                      getRowId={(row) => row.id}
                      loading={logbooks.loading}
                      onRowClick={(row) => navigate(fuelPaths.logbookDetail(row.id))}
                      caption="Driver logbooks at this site that are with a reviewer, with their status."
                      dense
                    />
                  </DataState>
                </SectionCard>
              </div>

              <div className="grid gap-5 xl:grid-cols-2">
                <SectionCard title="Open cases by type" subtitle="Where the exceptions are coming from">
                  <DataState
                    loading={anomalies.initialising}
                    error={anomalies.error}
                    empty={anomalyBars.length === 0}
                    emptyTitle="No open cases"
                    emptyHint="There is nothing to break down."
                    onRetry={anomalies.refetch}
                    minHeight={260}
                  >
                    <AnomalyMixChart bars={anomalyBars} />
                    <DerivedNote>
                      Counted from the {openAnomalies.length} open cases on this page of the queue. A case
                      counts as urgent when it is material or past its SLA.
                    </DerivedNote>
                  </DataState>
                </SectionCard>

                <SectionCard
                  title="Awaiting reconciliation"
                  subtitle="Received, not yet judged against a policy"
                  actions={
                    <Button
                      size="sm"
                      variant="ghost"
                      endIcon="chevron-right"
                      onClick={() => navigate(fuelPaths.reconciliation)}
                    >
                      Reconcile
                    </Button>
                  }
                  flush
                >
                  <DataState
                    loading={unreconciled.initialising}
                    error={unreconciled.error}
                    empty={(unreconciled.data?.totalElements ?? 0) === 0}
                    emptyTitle="Everything has been reconciled"
                    emptyHint="No transaction at this site is still in the received state."
                    onRetry={unreconciled.refetch}
                    minHeight={160}
                  >
                    <DataTable
                      rows={unreconciled.data?.content ?? []}
                      columns={unreconciledColumns}
                      getRowId={(row) => row.id}
                      loading={unreconciled.loading}
                      onRowClick={(row) => navigate(fuelPaths.transactionDetail(row.id))}
                      caption="Fuel transactions at this site that have been received but not reconciled, with their cost."
                      dense
                    />
                  </DataState>
                </SectionCard>
              </div>
            </div>
          )}
        </DataState>
      </div>
    </div>
  );
};

export default FuelDashboardPage;
