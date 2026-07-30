import { ReactNode, useMemo, useState } from 'react';
import { useNavigate } from 'react-router';
import { CourierItem, DispatchExceptionCase, DispatchManifest } from 'modules/dispatch/api/dto';
import { humanise } from 'modules/fleet/api/enums';
import {
  courierItemsApi,
  dispatchDashboardApi,
  dispatchExceptionsApi,
  manifestsApi,
} from 'modules/dispatch/api/dispatchApi';
import { exceptionSlaBreached } from 'modules/dispatch/api/workflow';
import ExceptionMixChart, { ExceptionBar } from 'modules/dispatch/charts/ExceptionMixChart';
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
import { dispatchPaths } from 'shared/layout/navigation';

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
 * The Courier & Dispatch workspace.
 *
 * The dispatch dashboard endpoint is **entirely exception-shaped** — eight counts, every one of them
 * a thing going wrong. That is the right emphasis for a custody system and it decides this page's
 * layout: what needs attention comes first, and the volume figures underneath are counted from the
 * registers because the snapshot does not carry them.
 *
 * `siteCode` is required by every dispatch endpoint, so this page is single-site by construction.
 */
const DispatchDashboardPage = () => {
  const navigate = useNavigate();
  const [siteCode, setSiteCode] = useState(defaultSite);

  const snapshot = useApiQuery(
    (signal) => dispatchDashboardApi.snapshot(siteCode, signal),
    [siteCode],
  );

  const exceptions = useApiQuery(
    (signal) => dispatchExceptionsApi.search({ siteCode }, signal),
    [siteCode],
  );

  const manifests = useApiQuery(
    (signal) => manifestsApi.search({ siteCode }, signal),
    [siteCode],
  );

  const items = useApiQuery(
    (signal) => courierItemsApi.search({ siteCode, undelivered: true, size: 25 }, signal),
    [siteCode],
  );

  const data = snapshot.data;

  /**
   * Each list is now the service's own answer to its own question, not a sieve over one window.
   *
   * The exception queue asks for open cases, the manifest list for the ones in flight, the item
   * list for what is undelivered. Each is capped at what a dashboard panel can usefully show, and
   * the counts beside them come from the service's `totalElements` rather than from the rows.
   */
  const openCases = useMemo(() => exceptions.data?.content ?? [], [exceptions.data]);
  const activeManifests = useMemo(() => manifests.data?.content ?? [], [manifests.data]);
  const undeliveredItems = useMemo(() => items.data?.content ?? [], [items.data]);

  const exceptionBars = useMemo<ExceptionBar[]>(() => {
    const counts = new Map<string, { total: number; urgent: number }>();
    openCases.forEach((exceptionCase) => {
      const entry = counts.get(exceptionCase.type) ?? { total: 0, urgent: 0 };
      entry.total += 1;
      if (exceptionCase.securityRelevant || exceptionSlaBreached(exceptionCase)) {
        entry.urgent += 1;
      }
      counts.set(exceptionCase.type, entry);
    });
    return [...counts.entries()]
      .sort((left, right) => right[1].total - left[1].total)
      .flatMap(([type, entry]) => {
        const bars: ExceptionBar[] = [];
        if (entry.urgent > 0) {
          bars.push({ label: humanise(type), value: entry.urgent, urgent: true });
        }
        if (entry.total - entry.urgent > 0) {
          bars.push({ label: humanise(type), value: entry.total - entry.urgent });
        }
        return bars;
      });
  }, [openCases]);

  const caseColumns = useMemo<Column<DispatchExceptionCase>[]>(
    () => [
      {
        key: 'case',
        header: 'Case',
        width: 250,
        cell: (row) => (
          <CellStack
            primary={`${row.exceptionNumber} · ${humanise(row.type)}`}
            secondary={`SLA ${formatDateTime(row.slaDueAt)} · ${row.assignee ?? 'unassigned'}`}
          />
        ),
      },
      {
        key: 'severity',
        header: 'Severity',
        width: 130,
        align: 'right',
        cell: (row) => (
          <div className="flex items-center justify-end gap-1.5">
            {row.securityRelevant && <StatusChip value="SECRET" label="Security" tone="blocked" />}
            <StatusChip value={row.severity} />
          </div>
        ),
      },
    ],
    [],
  );

  const manifestColumns = useMemo<Column<DispatchManifest>[]>(
    () => [
      {
        key: 'manifest',
        header: 'Manifest',
        width: 250,
        cell: (row) => (
          <CellStack
            primary={`${row.manifestNumber} · ${row.route}`}
            secondary={`${row.itemCount} item${row.itemCount === 1 ? '' : 's'} · ${row.assignedHandler}`}
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

  const itemColumns = useMemo<Column<CourierItem>[]>(
    () => [
      {
        key: 'item',
        header: 'Item',
        width: 250,
        cell: (row) => (
          <CellStack
            primary={`${row.itemNumber} · ${row.destination}`}
            secondary={row.exceptionReason ?? row.misrouteReason ?? humanise(row.itemType)}
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

  const refreshAll = () => {
    snapshot.refetch();
    exceptions.refetch();
    manifests.refetch();
    items.refetch();
  };

  return (
    <div>
      <PageHeader
        title="Courier and dispatch"
        subtitle="Consignments in flight, the custody chain behind them, and what is going wrong."
        crumbs={[{ label: 'Dispatch' }]}
        actions={
          <Button variant="outline" startIcon="refresh" onClick={refreshAll}>
            Refresh
          </Button>
        }
        meta={
          data && (
            <div className="flex flex-wrap items-center gap-2">
              <MetaChip stale={data.stale}>{`Snapshot ${formatDateTime(data.generatedAt)}`}</MetaChip>
              <MetaChip>{`Site ${siteCode}`}</MetaChip>
              <MetaChip>
                {data.sourceUpdatedAt
                  ? `Records last changed ${formatDateTime(data.sourceUpdatedAt)}`
                  : 'No dispatch records at this site'}
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
            required
            helperText="Every dispatch endpoint is scoped to one site."
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
                  The service marks the dispatch dashboard stale when nothing has changed within its
                  freshness threshold.
                  {data.sourceUpdatedAt
                    ? ` The most recent change was ${formatDateTime(data.sourceUpdatedAt)}.`
                    : ' No dispatch record has been created at this site.'}
                </Alert>
              )}

              {/*
               * Every figure in this row is published by the service, and every one of them counts
               * something that has gone wrong — which is what a custody system's dashboard should
               * lead with. Tone is spent only while a count is non-zero.
               */}
              <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
                <StatCard
                  label="In transit"
                  value={formatNumber(data.inTransitCount)}
                  icon="route"
                  caption="Consignments on the move"
                  onClick={() => navigate(`${dispatchPaths.manifests}?status=IN_TRANSIT`)}
                />
                <StatCard
                  label="Open exceptions"
                  value={formatNumber(data.openExceptionCount)}
                  icon="alert-triangle"
                  tone={data.openExceptionCount > 0 ? 'caution' : 'neutral'}
                  caption="Blocking the manifests they belong to"
                  onClick={() => navigate(dispatchPaths.exceptions)}
                />
                <StatCard
                  label="Custody gaps"
                  value={formatNumber(data.custodyGapCount)}
                  icon="shield-lock"
                  tone={data.custodyGapCount > 0 ? 'critical' : 'neutral'}
                  caption="Broken seals, count mismatches, missing hops"
                  onClick={() => navigate(`${dispatchPaths.exceptions}?type=CUSTODY_GAP`)}
                />
                <StatCard
                  label="Receipt variances"
                  value={formatNumber(data.receiptVarianceCount)}
                  icon="alert-circle"
                  tone={data.receiptVarianceCount > 0 ? 'critical' : 'neutral'}
                  caption="Seal, count or recipient disagreed"
                  onClick={() => navigate(`${dispatchPaths.exceptions}?type=RECEIPT_VARIANCE`)}
                />
                <StatCard
                  label="Outstanding returns"
                  value={formatNumber(data.outstandingReturnCount)}
                  icon="refresh"
                  tone={data.outstandingReturnCount > 0 ? 'caution' : 'neutral'}
                  caption="Went out and has not come back"
                  onClick={() => navigate(`${dispatchPaths.manifests}?status=RETURNED`)}
                />
                <StatCard
                  label="Undelivered items"
                  value={formatNumber(data.undeliveredCount)}
                  icon="package"
                  tone={data.undeliveredCount > 0 ? 'critical' : 'neutral'}
                  caption="Dispatched, never confirmed delivered"
                  onClick={() => navigate(dispatchPaths.items)}
                />
                <StatCard
                  label="Overdue receipts"
                  value={formatNumber(data.overdueReceiptCount)}
                  icon="clock"
                  tone={data.overdueReceiptCount > 0 ? 'caution' : 'neutral'}
                  caption="Arrived without a confirmation"
                  onClick={() => navigate(`${dispatchPaths.manifests}?status=DISPATCHED`)}
                />
                <StatCard
                  label="SLA breaches"
                  value={formatNumber(data.slaBreachCount)}
                  icon="alert-circle"
                  tone={data.slaBreachCount > 0 ? 'critical' : 'neutral'}
                  caption="Cases past their resolution target"
                  onClick={() => navigate(dispatchPaths.exceptions)}
                />
              </div>

              <div className="grid gap-5 xl:grid-cols-2">
                <SectionCard
                  title="Open cases by type"
                  subtitle="Where the exceptions are coming from"
                >
                  <DataState
                    loading={exceptions.initialising}
                    error={exceptions.error}
                    empty={exceptionBars.length === 0}
                    emptyTitle="No open cases"
                    emptyHint="Nothing at this site is waiting on an explanation or a decision."
                    onRetry={exceptions.refetch}
                    minHeight={260}
                  >
                    <ExceptionMixChart bars={exceptionBars} />
                    <p className="mt-3 text-theme-xs text-gray-600">
                      Counted from the {openCases.length} open cases returned for this site. A case
                      counts as urgent when it is security relevant or past its SLA.
                    </p>
                  </DataState>
                </SectionCard>

                <SectionCard
                  title="Open exception cases"
                  subtitle="Oldest SLA first"
                  actions={
                    <Button
                      size="sm"
                      variant="ghost"
                      endIcon="chevron-right"
                      onClick={() => navigate(dispatchPaths.exceptions)}
                    >
                      View queue
                    </Button>
                  }
                  flush
                >
                  <DataState
                    loading={exceptions.initialising}
                    error={exceptions.error}
                    empty={openCases.length === 0}
                    emptyTitle="No open exception cases"
                    emptyHint="Every consignment at this site is clean."
                    onRetry={exceptions.refetch}
                    minHeight={160}
                  >
                    <DataTable
                      rows={[...openCases]
                        .sort((left, right) => left.slaDueAt.localeCompare(right.slaDueAt))
                        .slice(0, 6)}
                      columns={caseColumns}
                      getRowId={(row) => row.id}
                      loading={exceptions.loading}
                      onRowClick={(row) => navigate(dispatchPaths.exceptionDetail(row.id))}
                      caption="Open dispatch exception cases at this site, ordered by SLA due time, with severity and security relevance."
                      dense
                    />
                  </DataState>
                </SectionCard>
              </div>

              <div className="grid gap-5 xl:grid-cols-2">
                <SectionCard
                  title="Consignments in flight"
                  subtitle="Sealed through to reconciled"
                  actions={
                    <Button
                      size="sm"
                      variant="ghost"
                      endIcon="chevron-right"
                      onClick={() => navigate(dispatchPaths.manifests)}
                    >
                      View manifests
                    </Button>
                  }
                  flush
                >
                  <DataState
                    loading={manifests.initialising}
                    error={manifests.error}
                    empty={activeManifests.length === 0}
                    emptyTitle="Nothing in flight"
                    emptyHint="No consignment at this site is between sealing and reconciliation."
                    onRetry={manifests.refetch}
                    minHeight={160}
                  >
                    <DataTable
                      rows={activeManifests.slice(0, 6)}
                      columns={manifestColumns}
                      getRowId={(row) => row.id}
                      loading={manifests.loading}
                      onRowClick={(row) => navigate(dispatchPaths.manifestDetail(row.id))}
                      caption="Dispatch manifests at this site that are in flight, with their item count, handler and status."
                      dense
                    />
                  </DataState>
                </SectionCard>

                <SectionCard
                  title="Items needing attention"
                  subtitle="Undelivered or in exception"
                  actions={
                    <Button
                      size="sm"
                      variant="ghost"
                      endIcon="chevron-right"
                      onClick={() => navigate(dispatchPaths.items)}
                    >
                      View register
                    </Button>
                  }
                  flush
                >
                  <DataState
                    loading={items.initialising}
                    error={items.error}
                    empty={undeliveredItems.length === 0}
                    emptyTitle="Nothing outstanding"
                    emptyHint="No item at this site is undelivered or in exception."
                    onRetry={items.refetch}
                    minHeight={160}
                  >
                    <DataTable
                      rows={undeliveredItems.slice(0, 6)}
                      columns={itemColumns}
                      getRowId={(row) => row.id}
                      loading={items.loading}
                      onRowClick={(row) => navigate(dispatchPaths.itemDetail(row.id))}
                      caption="Courier items at this site that are undelivered or in exception, with the reason recorded against each."
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

export default DispatchDashboardPage;
