import { useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { DispatchExceptionCase } from 'modules/dispatch/api/dto';
import {
  EXCEPTION_SEVERITIES,
  EXCEPTION_STATUSES,
  EXCEPTION_TYPES,
  ExceptionSeverity,
  ExceptionStatus,
  ExceptionType,
} from 'modules/dispatch/api/enums';
import {
  DEFAULT_WINDOW,
  dispatchExceptionsApi,
  dispatchReportsApi,
} from 'modules/dispatch/api/dispatchApi';
import { exceptionOpen, exceptionSlaBreached } from 'modules/dispatch/api/workflow';
import WindowNotice from 'modules/dispatch/components/WindowNotice';
import { useClientWindow } from 'modules/dispatch/components/useClientWindow';
import { formatDueIn } from 'modules/fuel/components/fuelFormat';
import { humanise } from 'modules/fleet/api/enums';
import Button from 'shared/components/Button';
import DataState from 'shared/components/DataState';
import DataTable, { CellStack, Column } from 'shared/components/DataTable';
import FilterBar from 'shared/components/FilterBar';
import Icon from 'shared/components/Icon';
import { useNotifier } from 'shared/components/Notifier';
import PageHeader from 'shared/components/PageHeader';
import SectionCard from 'shared/components/SectionCard';
import SiteSelect, { defaultSite } from 'shared/components/SiteSelect';
import StatCard from 'shared/components/StatCard';
import StatusChip from 'shared/components/StatusChip';
import { EnumSelect, SelectInput, TextInput } from 'shared/components/fields';
import { formatNumber } from 'shared/components/format';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { dispatchPaths } from 'shared/layout/navigation';

const QUEUE_VIEWS = [
  { value: 'OPEN', label: 'Open cases' },
  { value: 'BREACHED', label: 'Breaching SLA' },
  { value: 'SECURITY', label: 'Security relevant' },
  { value: 'UNASSIGNED', label: 'Unassigned' },
];

/**
 * The dispatch exception queue.
 *
 * `GET /exceptions` accepts a site, a type and a status; severity, assignee, security relevance and
 * SLA standing are filtered here over the returned window, and each control says so. With more open
 * cases than the window holds, the "breaching SLA" view is the breaches *in the window* rather than
 * at the site — `WindowNotice` makes that visible instead of leaving it to be discovered.
 *
 * The stakes are higher here than on a normal queue: an open case blocks the manifest it belongs to
 * from closing, so a case nobody can see is a consignment nobody can close.
 */
const DispatchExceptionsPage = () => {
  const navigate = useNavigate();
  const { notifySuccess, notifyError } = useNotifier();
  const [searchParams] = useSearchParams();

  const [siteCode, setSiteCode] = useState(defaultSite);
  const [type, setType] = useState<ExceptionType | ''>(
    (searchParams.get('type') as ExceptionType | null) ?? '',
  );
  const [status, setStatus] = useState<ExceptionStatus | ''>(
    (searchParams.get('status') as ExceptionStatus | null) ?? '',
  );
  const [view, setView] = useState('OPEN');
  const [severity, setSeverity] = useState<ExceptionSeverity | ''>('');
  const [assignee, setAssignee] = useState('');
  const [exporting, setExporting] = useState(false);

  const query = useApiQuery(
    (signal) =>
      dispatchExceptionsApi.search(
        { siteCode, type: type || undefined, status: status || undefined },
        signal,
      ),
    [siteCode, type, status],
  );

  const all = useMemo(() => query.data ?? [], [query.data]);

  const filtered = useMemo(() => {
    let rows = all;
    if (view === 'OPEN') {
      rows = rows.filter(exceptionOpen);
    } else if (view === 'BREACHED') {
      rows = rows.filter((row) => exceptionSlaBreached(row));
    } else if (view === 'SECURITY') {
      rows = rows.filter((row) => row.securityRelevant && exceptionOpen(row));
    } else if (view === 'UNASSIGNED') {
      rows = rows.filter((row) => !row.assignee && exceptionOpen(row));
    }
    if (severity) {
      rows = rows.filter((row) => row.severity === severity);
    }
    if (assignee.trim()) {
      const needle = assignee.trim().toLowerCase();
      rows = rows.filter((row) => (row.assignee ?? '').toLowerCase().includes(needle));
    }
    // Oldest SLA first: a queue is ordered by what is most overdue.
    return [...rows].sort((left, right) => left.slaDueAt.localeCompare(right.slaDueAt));
  }, [all, view, severity, assignee]);

  const windowed = useClientWindow(
    filtered,
    `${siteCode}|${type}|${status}|${view}|${severity}|${assignee}`,
    all.length,
  );

  const openCases = useMemo(() => all.filter(exceptionOpen), [all]);
  const breached = useMemo(
    () => openCases.filter((row) => exceptionSlaBreached(row)),
    [openCases],
  );
  const security = useMemo(() => openCases.filter((row) => row.securityRelevant), [openCases]);
  const unassigned = useMemo(() => openCases.filter((row) => !row.assignee), [openCases]);

  const exportReport = async () => {
    setExporting(true);
    try {
      const fileName = await dispatchReportsApi.exceptions(siteCode);
      notifySuccess(
        `Downloaded ${fileName}.`,
        'The service exports the site’s exception cases, not the filtered view.',
      );
    } catch (error) {
      notifyError(error);
    } finally {
      setExporting(false);
    }
  };

  const columns = useMemo<Column<DispatchExceptionCase>[]>(
    () => [
      {
        key: 'case',
        header: 'Case',
        width: 260,
        cell: (row) => (
          <CellStack
            primary={`${row.exceptionNumber} · ${humanise(row.type)}`}
            secondary={
              row.detectedRules.map((rule) => humanise(rule)).join(', ') || 'no rule recorded'
            }
          />
        ),
      },
      {
        key: 'sla',
        header: 'SLA',
        width: 150,
        cell: (row) => (
          <span
            className={exceptionSlaBreached(row) ? 'font-semibold text-error-800' : 'text-gray-700'}
          >
            {exceptionSlaBreached(row) && (
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
        width: 140,
        cell: (row) => (
          <div className="flex items-center gap-1.5">
            <StatusChip value={row.severity} />
            {row.securityRelevant && (
              <Icon
                name="shield-lock"
                size={14}
                className="shrink-0 text-error-800"
                aria-label="Security relevant"
              />
            )}
          </div>
        ),
      },
      {
        key: 'blocks',
        header: 'Blocks',
        width: 110,
        align: 'center',
        hideBelowLg: true,
        cell: (row) =>
          row.dispatchId && exceptionOpen(row) ? (
            <StatusChip value="BLOCKED" label="Manifest" tone="blocked" />
          ) : (
            <span className="text-gray-500">—</span>
          ),
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

  const filtersApplied = Boolean(type || status || severity || assignee || view !== 'OPEN');

  return (
    <div>
      <PageHeader
        title="Dispatch exception cases"
        subtitle="Custody gaps, receipt variances, scan mismatches and return discrepancies."
        crumbs={[{ label: 'Dispatch', to: dispatchPaths.dashboard }, { label: 'Exception cases' }]}
        actions={
          <>
            <Button
              variant="outline"
              startIcon="download"
              loading={exporting}
              onClick={exportReport}
            >
              Export CSV
            </Button>
            <Button variant="outline" startIcon="refresh" onClick={query.refetch}>
              Refresh
            </Button>
          </>
        }
      />

      <div className="mb-5 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard
          label="Open cases"
          value={formatNumber(openCases.length)}
          icon="alert-triangle"
          tone={openCases.length > 0 ? 'caution' : 'neutral'}
          caption="Each blocks its manifest from closing"
          onClick={() => setView('OPEN')}
        />
        <StatCard
          label="Breaching SLA"
          value={formatNumber(breached.length)}
          icon="clock"
          tone={breached.length > 0 ? 'critical' : 'neutral'}
          caption="Past the resolution target"
          onClick={() => setView('BREACHED')}
        />
        <StatCard
          label="Security relevant"
          value={formatNumber(security.length)}
          icon="shield-lock"
          tone={security.length > 0 ? 'critical' : 'neutral'}
          caption="Surfaced to the security function"
          onClick={() => setView('SECURITY')}
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
            setType('');
            setStatus('');
            setView('OPEN');
            setSeverity('');
            setAssignee('');
          }}
          resetDisabled={!filtersApplied}
        >
          <SiteSelect value={siteCode} onChange={setSiteCode} required />
          <EnumSelect
            label="Type"
            value={type}
            options={EXCEPTION_TYPES}
            onChange={(value) => setType(value)}
            allowEmpty
          />
          <EnumSelect
            label="Status"
            value={status}
            options={EXCEPTION_STATUSES}
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
            label="Severity"
            value={severity}
            options={EXCEPTION_SEVERITIES}
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
              onRowClick={(row) => navigate(dispatchPaths.exceptionDetail(row.id))}
              caption="Dispatch exception cases matching the current filters, ordered by SLA due time, with assignee, severity, security relevance, whether they block a manifest, and status."
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
          noun="exception cases"
        />
      </div>
    </div>
  );
};

export default DispatchExceptionsPage;
