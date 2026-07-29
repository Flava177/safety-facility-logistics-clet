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
import { dispatchExceptionsApi, dispatchReportsApi } from 'modules/dispatch/api/dispatchApi';
import { exceptionOpen, exceptionSlaBreached } from 'modules/dispatch/api/workflow';
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
import { useClampPage, useServerPage } from 'shared/hooks/useServerPage';
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
 * at the site. Every one of them is a server-side predicate now, so the two are the same thing.
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

  /**
   * Each view is a set of server-side predicates, not a pass over whatever came back.
   *
   * This was gap 2. "Breaching SLA" used to mean the breaches *in the loaded window*, which at a
   * busy site is a different and much smaller number than the breaches at the site — and the
   * difference only showed up when somebody went looking for a case nobody had seen.
   */
  const viewParams =
    view === 'OPEN'
      ? { openOnly: true }
      : view === 'BREACHED'
        ? { openOnly: true, dueBefore: new Date().toISOString() }
        : view === 'SECURITY'
          ? { openOnly: true, securityRelevant: true }
          : view === 'UNASSIGNED'
            ? { openOnly: true, unassigned: true }
            : {};

  const filterKey = `${siteCode}|${type}|${status}|${view}|${severity}|${assignee}`;
  const paging = useServerPage(filterKey);

  const query = useApiQuery(
    (signal) =>
      dispatchExceptionsApi.search(
        {
          siteCode,
          type: type || undefined,
          status: status || undefined,
          severity: severity || undefined,
          assignee: assignee.trim() || undefined,
          ...viewParams,
          page: paging.page,
          size: paging.size,
        },
        signal,
      ),
    [siteCode, type, status, severity, assignee, view, paging.page, paging.size],
  );

  useClampPage(paging.page, query.data?.totalPages, paging.setPage);

  /**
   * The four queue counts, each its own site-wide query.
   *
   * Counted by the service rather than from the page on screen: a page of twenty-five records
   * cannot tell an operator how many open cases the site has, and a header figure that silently
   * meant "on this page" is exactly the kind of number somebody plans around.
   */
  const counts = useApiQuery(
    (signal) =>
      Promise.all([
        dispatchExceptionsApi.search({ siteCode, openOnly: true, size: 1 }, signal),
        dispatchExceptionsApi.search(
          { siteCode, openOnly: true, dueBefore: new Date().toISOString(), size: 1 },
          signal,
        ),
        dispatchExceptionsApi.search({ siteCode, openOnly: true, securityRelevant: true, size: 1 }, signal),
        dispatchExceptionsApi.search({ siteCode, openOnly: true, unassigned: true, size: 1 }, signal),
      ]).then(([open, breaching, secure, unassignedCases]) => ({
        open: open.totalElements,
        breached: breaching.totalElements,
        security: secure.totalElements,
        unassigned: unassignedCases.totalElements,
      })),
    [siteCode],
  );

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
          value={formatNumber(counts.data?.open ?? 0)}
          icon="alert-triangle"
          tone={(counts.data?.open ?? 0) > 0 ? 'caution' : 'neutral'}
          caption="Each blocks its manifest from closing"
          onClick={() => setView('OPEN')}
        />
        <StatCard
          label="Breaching SLA"
          value={formatNumber(counts.data?.breached ?? 0)}
          icon="clock"
          tone={(counts.data?.breached ?? 0) > 0 ? 'critical' : 'neutral'}
          caption="Past the resolution target"
          onClick={() => setView('BREACHED')}
        />
        <StatCard
          label="Security relevant"
          value={formatNumber(counts.data?.security ?? 0)}
          icon="shield-lock"
          tone={(counts.data?.security ?? 0) > 0 ? 'critical' : 'neutral'}
          caption="Surfaced to the security function"
          onClick={() => setView('SECURITY')}
        />
        <StatCard
          label="Unassigned"
          value={formatNumber(counts.data?.unassigned ?? 0)}
          icon="user-plus"
          tone={(counts.data?.unassigned ?? 0) > 0 ? 'caution' : 'neutral'}
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
          />
          <EnumSelect
            label="Severity"
            value={severity}
            options={EXCEPTION_SEVERITIES}
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
              onRowClick={(row) => navigate(dispatchPaths.exceptionDetail(row.id))}
              caption="Dispatch exception cases matching the current filters, ordered by SLA due time, with assignee, severity, security relevance, whether they block a manifest, and status."
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

export default DispatchExceptionsPage;
