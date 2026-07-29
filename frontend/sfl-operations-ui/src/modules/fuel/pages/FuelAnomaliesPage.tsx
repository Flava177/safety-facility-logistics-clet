import { useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { FuelAnomalyCase } from 'modules/fuel/api/dto';
import {
  ANOMALY_SEVERITIES,
  ANOMALY_STATUSES,
  ANOMALY_TYPES,
  AnomalySeverity,
  AnomalyStatus,
  AnomalyType,
} from 'modules/fuel/api/enums';
import { fuelAnomaliesApi, fuelDashboardApi } from 'modules/fuel/api/fuelApi';
import { anomalySlaBreached } from 'modules/fuel/api/workflow';
import { useClampPage, useServerPage } from 'modules/fuel/components/useServerPage';
import { formatDueIn } from 'modules/fuel/components/fuelFormat';
import { humanise } from 'modules/fleet/api/enums';
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
import { EnumSelect, SelectInput, TextInput } from 'shared/components/fields';
import { formatNumber } from 'shared/components/format';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { fuelPaths } from 'shared/layout/navigation';

const QUEUE_VIEWS = [
  { value: 'OPEN', label: 'Open cases' },
  { value: 'BREACHED', label: 'Breaching SLA' },
  { value: 'MATERIAL', label: 'Material only' },
  { value: 'UNASSIGNED', label: 'Unassigned' },
];

/**
 * The fuel anomaly queue.
 *
 * Every filter here reaches the service, including the four queue views. That matters more here
 * than anywhere else in the module: these filters were once applied in the browser over a capped
 * window, so "breaching SLA" meant "breaches among the first two hundred cases" — precisely the
 * queue an operator must not be handed. The service's default ordering is oldest SLA first, which
 * is what a queue wants.
 *
 * The four counters above the table come from the dashboard endpoint, which counts them across the
 * whole site rather than across a page.
 */
const FuelAnomaliesPage = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  const [siteCode, setSiteCode] = useState(defaultSite);
  const [status, setStatus] = useState<AnomalyStatus | ''>(
    (searchParams.get('status') as AnomalyStatus | null) ?? '',
  );
  const [view, setView] = useState('OPEN');
  const [type, setType] = useState<AnomalyType | ''>('');
  const [severity, setSeverity] = useState<AnomalySeverity | ''>('');
  const [assignee, setAssignee] = useState('');

  /** The four views, expressed as the query parameters the service accepts. */
  const viewParams = useMemo(() => {
    switch (view) {
      case 'OPEN':
        return { openOnly: true };
      case 'BREACHED':
        return { openOnly: true, dueBefore: new Date().toISOString() };
      case 'MATERIAL':
        return { openOnly: true, material: true };
      case 'UNASSIGNED':
        return { openOnly: true, unassigned: true };
      default:
        return {};
    }
  }, [view]);

  const filterKey = `${siteCode}|${status}|${view}|${type}|${severity}|${assignee}`;
  const paging = useServerPage(filterKey);

  const query = useApiQuery(
    (signal) =>
      fuelAnomaliesApi.search(
        {
          siteCode,
          status: status || undefined,
          type: type || undefined,
          severity: severity || undefined,
          assignee: assignee.trim() || undefined,
          ...viewParams,
          page: paging.page,
          size: paging.size,
        },
        signal,
      ),
    [filterKey, paging.page, paging.size],
  );

  useClampPage(paging.page, query.data?.totalPages, paging.setPage);

  /** Site-wide counters, published by the service rather than counted from a page. */
  const indicators = useApiQuery(
    (signal) => fuelDashboardApi.snapshot(siteCode, signal),
    [siteCode],
  );

  const columns = useMemo<Column<FuelAnomalyCase>[]>(
    () => [
      {
        key: 'case',
        header: 'Case',
        width: 260,
        cell: (row) => (
          <CellStack
            primary={`${row.anomalyNumber} · ${humanise(row.type)}`}
            secondary={row.detectedRules.map((rule) => humanise(rule)).join(', ') || 'no rule recorded'}
          />
        ),
      },
      {
        key: 'sla',
        header: 'SLA',
        width: 150,
        cell: (row) => (
          <span
            className={
              anomalySlaBreached(row) ? 'font-semibold text-error-800' : 'text-gray-700'
            }
          >
            {anomalySlaBreached(row) && (
              <Icon name="clock" size={13} className="mr-1 inline align-[-2px]" />
            )}
            {formatDueIn(row.slaDueAt)}
          </span>
        ),
      },
      {
        key: 'assignee',
        header: 'Assignee',
        width: 150,
        hideBelowLg: true,
        cell: (row) => row.assignee ?? <span className="text-gray-500">Unassigned</span>,
      },
      {
        key: 'severity',
        header: 'Severity',
        width: 110,
        cell: (row) => <StatusChip value={row.severity} />,
      },
      {
        key: 'material',
        header: 'Material',
        width: 100,
        align: 'center',
        hideBelowLg: true,
        cell: (row) =>
          row.material ? (
            <StatusChip value="HIGH" label="Material" tone="caution" />
          ) : (
            <span className="text-gray-500">—</span>
          ),
      },
      {
        key: 'escalation',
        header: 'Level',
        width: 80,
        align: 'right',
        hideBelowLg: true,
        cell: (row) => row.escalationLevel,
      },
      {
        key: 'status',
        header: 'Status',
        width: 160,
        align: 'right',
        cell: (row) => <StatusChip value={row.status} />,
      },
    ],
    [],
  );

  const filtersApplied = Boolean(status || type || severity || assignee || view !== 'OPEN');

  return (
    <div>
      <PageHeader
        title="Fuel anomaly cases"
        subtitle="The exception queue: assign, explain, decide and close."
        crumbs={[{ label: 'Fuel', to: fuelPaths.dashboard }, { label: 'Anomaly cases' }]}
        actions={
          <Button
            variant="outline"
            startIcon="refresh"
            onClick={() => {
              query.refetch();
              indicators.refetch();
            }}
          >
            Refresh
          </Button>
        }
      />

      <div className="mb-5 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard
          label="Open cases"
          value={formatNumber(indicators.data?.openAnomalies ?? 0)}
          icon="alert-triangle"
          tone={(indicators.data?.openAnomalies ?? 0) > 0 ? 'caution' : 'neutral'}
          caption="Neither closed nor cancelled"
          onClick={() => setView('OPEN')}
        />
        <StatCard
          label="Breaching SLA"
          value={formatNumber(indicators.data?.anomaliesBreachingSla ?? 0)}
          icon="clock"
          tone={(indicators.data?.anomaliesBreachingSla ?? 0) > 0 ? 'critical' : 'neutral'}
          caption="Past the policy’s target"
          onClick={() => setView('BREACHED')}
        />
        <StatCard
          label="Material"
          value={formatNumber(indicators.data?.materialOpenAnomalies ?? 0)}
          icon="coins"
          tone={(indicators.data?.materialOpenAnomalies ?? 0) > 0 ? 'critical' : 'neutral'}
          caption="Surfaced to finance and audit"
          onClick={() => setView('MATERIAL')}
        />
        <StatCard
          label="Unassigned"
          value={formatNumber(indicators.data?.unassignedAnomalies ?? 0)}
          icon="user-plus"
          tone={(indicators.data?.unassignedAnomalies ?? 0) > 0 ? 'caution' : 'neutral'}
          caption="Nobody is accountable yet"
          onClick={() => setView('UNASSIGNED')}
        />
      </div>

      <SectionCard flush>
        <FilterBar
          onReset={() => {
            setStatus('');
            setView('OPEN');
            setType('');
            setSeverity('');
            setAssignee('');
          }}
          resetDisabled={!filtersApplied}
        >
          <SiteSelect value={siteCode} onChange={setSiteCode} required />
          <EnumSelect
            label="Status"
            value={status}
            options={ANOMALY_STATUSES}
            onChange={(value) => setStatus(value)}
            allowEmpty
          />
          <SelectInput
            label="View"
            value={view}
            onChange={setView}
            options={QUEUE_VIEWS}
            allowEmpty
            emptyLabel="Every case"
          />
          <EnumSelect
            label="Type"
            value={type}
            options={ANOMALY_TYPES}
            onChange={(value) => setType(value)}
            allowEmpty
          />
          <EnumSelect
            label="Severity"
            value={severity}
            options={ANOMALY_SEVERITIES}
            onChange={(value) => setSeverity(value)}
            allowEmpty
          />
          <TextInput
            label="Assignee"
            value={assignee}
            onChange={setAssignee}
            placeholder="Part of a name"
          />
        </FilterBar>
      </SectionCard>

      <div className="mt-5">
        <SectionCard flush>
          <DataState
            loading={query.initialising}
            error={query.error}
            onRetry={query.refetch}
            minHeight={300}
          >
            <DataTable
              rows={query.data?.content ?? []}
              columns={columns}
              getRowId={(row) => row.id}
              loading={query.loading}
              onRowClick={(row) => navigate(fuelPaths.anomalyDetail(row.id))}
              caption="Fuel anomaly cases matching the current filters, ordered by SLA due time, with assignee, severity, materiality, escalation level and status."
              emptyMessage="No case matches these filters."
              page={query.data?.page ?? paging.page}
              pageSize={query.data?.size ?? paging.size}
              totalElements={query.data?.totalElements ?? 0}
              onPageChange={paging.setPage}
              onPageSizeChange={paging.setSize}
            />
          </DataState>
        </SectionCard>
      </div>
    </div>
  );
};

export default FuelAnomaliesPage;
