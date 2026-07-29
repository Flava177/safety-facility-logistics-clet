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
import { DEFAULT_WINDOW, fuelAnomaliesApi } from 'modules/fuel/api/fuelApi';
import { anomalyOpen, anomalySlaBreached } from 'modules/fuel/api/workflow';
import WindowNotice from 'modules/fuel/components/WindowNotice';
import { useClientWindow } from 'modules/fuel/components/useClientWindow';
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
 * `GET /anomalies` accepts a site and a status and nothing else (gap 5), so severity, type,
 * assignee, materiality and SLA standing are all filtered here over the window the service returned
 * — each control says so. That is a real limitation on a queue: with more open cases than the window
 * holds, the "breaching SLA" view is the breaches *in the window*, not at the site. `WindowNotice`
 * makes that visible rather than leaving it to be discovered.
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

  const query = useApiQuery(
    (signal) => fuelAnomaliesApi.search({ siteCode, status: status || undefined }, signal),
    [siteCode, status],
  );

  const all = useMemo(() => query.data ?? [], [query.data]);

  const filtered = useMemo(() => {
    let rows = all;
    if (view === 'OPEN') {
      rows = rows.filter(anomalyOpen);
    } else if (view === 'BREACHED') {
      rows = rows.filter((row) => anomalySlaBreached(row));
    } else if (view === 'MATERIAL') {
      rows = rows.filter((row) => row.material && anomalyOpen(row));
    } else if (view === 'UNASSIGNED') {
      rows = rows.filter((row) => !row.assignee && anomalyOpen(row));
    }
    if (type) {
      rows = rows.filter((row) => row.type === type);
    }
    if (severity) {
      rows = rows.filter((row) => row.severity === severity);
    }
    if (assignee.trim()) {
      const needle = assignee.trim().toLowerCase();
      rows = rows.filter((row) => (row.assignee ?? '').toLowerCase().includes(needle));
    }
    // Oldest SLA first: a queue is ordered by what is most overdue, not by when it was raised.
    return [...rows].sort((left, right) => left.slaDueAt.localeCompare(right.slaDueAt));
  }, [all, view, type, severity, assignee]);

  const windowed = useClientWindow(
    filtered,
    `${siteCode}|${status}|${view}|${type}|${severity}|${assignee}`,
    all.length,
  );

  const openCases = useMemo(() => all.filter(anomalyOpen), [all]);
  const breached = useMemo(
    () => openCases.filter((row) => anomalySlaBreached(row)),
    [openCases],
  );
  const material = useMemo(() => openCases.filter((row) => row.material), [openCases]);
  const unassigned = useMemo(() => openCases.filter((row) => !row.assignee), [openCases]);

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
          <Button variant="outline" startIcon="refresh" onClick={query.refetch}>
            Refresh
          </Button>
        }
      />

      <div className="mb-5 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard
          label="Open cases"
          value={formatNumber(openCases.length)}
          icon="alert-triangle"
          tone={openCases.length > 0 ? 'caution' : 'neutral'}
          caption="Neither closed nor cancelled"
          onClick={() => setView('OPEN')}
        />
        <StatCard
          label="Breaching SLA"
          value={formatNumber(breached.length)}
          icon="clock"
          tone={breached.length > 0 ? 'critical' : 'neutral'}
          caption="Past the policy’s target"
          onClick={() => setView('BREACHED')}
        />
        <StatCard
          label="Material"
          value={formatNumber(material.length)}
          icon="coins"
          tone={material.length > 0 ? 'critical' : 'neutral'}
          caption="Surfaced to finance and audit"
          onClick={() => setView('MATERIAL')}
        />
        <StatCard
          label="Unassigned"
          value={formatNumber(unassigned.length)}
          icon="user-plus"
          tone={unassigned.length > 0 ? 'caution' : 'neutral'}
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
            emptyLabel="Everything returned"
            helperText="Filters the loaded records."
          />
          <EnumSelect
            label="Type"
            value={type}
            options={ANOMALY_TYPES}
            onChange={(value) => setType(value)}
            allowEmpty
            helperText="Filters the loaded records."
          />
          <EnumSelect
            label="Severity"
            value={severity}
            options={ANOMALY_SEVERITIES}
            onChange={(value) => setSeverity(value)}
            allowEmpty
            helperText="Filters the loaded records."
          />
          <TextInput
            label="Assignee"
            value={assignee}
            onChange={setAssignee}
            placeholder="Part of a name"
            helperText="Filters the loaded records."
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
              rows={windowed.rows}
              columns={columns}
              getRowId={(row) => row.id}
              loading={query.loading}
              onRowClick={(row) => navigate(fuelPaths.anomalyDetail(row.id))}
              caption="Fuel anomaly cases matching the current filters, ordered by SLA due time, with assignee, severity, materiality, escalation level and status."
              emptyMessage="No case matches these filters."
              page={windowed.page}
              pageSize={windowed.pageSize}
              totalElements={windowed.total}
              onPageChange={windowed.setPage}
              onPageSizeChange={windowed.setPageSize}
            />
          </DataState>
        </SectionCard>

        <WindowNotice
          truncated={windowed.truncated}
          total={all.length}
          requestedSize={DEFAULT_WINDOW}
          noun="anomaly cases"
        />
      </div>
    </div>
  );
};

export default FuelAnomaliesPage;
