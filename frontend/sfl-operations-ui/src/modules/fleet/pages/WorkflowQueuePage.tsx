import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router';
import { WorkflowItemResponse } from 'modules/fleet/api/dto';
import {
  FLEET_WORKFLOW_STATUSES,
  FLEET_WORKFLOW_TYPES,
  FleetWorkflowStatus,
  FleetWorkflowType,
  WORKFLOW_PRIORITIES,
  WorkflowPriority,
  humanise,
} from 'modules/fleet/api/enums';
import { workflowApi } from 'modules/fleet/api/fleetApi';
import { RaiseWorkflowItemDialog } from 'modules/fleet/dialogs/workflowDialogs';
import { defaultPageSize } from 'shared/api/config';
import Button from 'shared/components/Button';
import DataState from 'shared/components/DataState';
import DataTable, { CellStack, Column } from 'shared/components/DataTable';
import FilterBar from 'shared/components/FilterBar';
import { useNotifier } from 'shared/components/Notifier';
import PageHeader from 'shared/components/PageHeader';
import SectionCard from 'shared/components/SectionCard';
import SiteSelect, { defaultSite } from 'shared/components/SiteSelect';
import StatusChip from 'shared/components/StatusChip';
import { Checkbox, EnumSelect, TextInput } from 'shared/components/fields';
import { formatDateTime } from 'shared/components/format';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { fleetPaths } from 'shared/layout/navigation';

interface Filters {
  siteCode: string;
  status: FleetWorkflowStatus | '';
  type: FleetWorkflowType | '';
  priority: WorkflowPriority | '';
  assignee: string;
  overdueOnly: boolean;
  escalatedOnly: boolean;
}

const emptyFilters: Filters = {
  siteCode: defaultSite,
  status: '',
  type: '',
  priority: '',
  assignee: '',
  overdueOnly: false,
  escalatedOnly: false,
};

/** The fleet workflow queue — defects, renewals, exceptions and their SLA standing. */
const WorkflowQueuePage = () => {
  const navigate = useNavigate();
  const { notifySuccess } = useNotifier();
  const [filters, setFilters] = useState<Filters>(emptyFilters);
  const [pagination, setPagination] = useState({ page: 0, pageSize: defaultPageSize });
  const [raiseOpen, setRaiseOpen] = useState(false);

  const setFilter = <K extends keyof Filters>(key: K, value: Filters[K]) => {
    setFilters((current) => ({ ...current, [key]: value }));
    setPagination((current) => ({ ...current, page: 0 }));
  };

  // Reset is a filter change like any other: leaving the page index behind asks the server for a
  // page the narrowed result set no longer has, and the table comes back empty.
  const resetFilters = () => {
    setFilters(emptyFilters);
    setPagination((current) => ({ ...current, page: 0 }));
  };

  const query = useApiQuery(
    (signal) =>
      workflowApi.search(
        {
          siteCode: filters.siteCode || undefined,
          status: filters.status || undefined,
          type: filters.type || undefined,
          priority: filters.priority || undefined,
          assignee: filters.assignee || undefined,
          overdueOnly: filters.overdueOnly || undefined,
          escalatedOnly: filters.escalatedOnly || undefined,
          page: pagination.page,
          size: pagination.pageSize,
        },
        signal,
      ),
    [filters, pagination.page, pagination.pageSize],
  );

  const columns = useMemo<Column<WorkflowItemResponse>[]>(
    () => [
      {
        key: 'workflowNumber',
        header: 'Item',
        width: 230,
        cell: (row) => <CellStack primary={row.workflowNumber} secondary={row.title} />,
      },
      {
        key: 'workflowType',
        header: 'Type',
        width: 170,
        cell: (row) => humanise(row.workflowType),
      },
      {
        key: 'status',
        header: 'Status',
        width: 130,
        cell: (row) => <StatusChip value={row.status} />,
      },
      {
        key: 'priority',
        header: 'Priority',
        width: 110,
        cell: (row) => <StatusChip value={row.priority} />,
      },
      {
        key: 'severity',
        header: 'Severity',
        width: 110,
        cell: (row) => <StatusChip value={row.severity} />,
      },
      {
        key: 'slaDueAt',
        header: 'SLA',
        width: 200,
        cell: (row) => (
          <div className="min-w-0">
            <div className={row.slaBreached ? 'font-semibold text-error-600' : 'text-gray-800'}>
              {formatDateTime(row.slaDueAt)}
            </div>
            <div className="text-theme-xs text-gray-500">
              {row.slaBreached ? 'Breached' : 'Within target'} · level {row.escalationLevel}
            </div>
          </div>
        ),
      },
      {
        key: 'assignee',
        header: 'Assignee',
        width: 150,
        cell: (row) =>
          row.assignee ?? <span className="text-theme-xs text-gray-600">Unassigned</span>,
      },
      {
        key: 'siteCode',
        header: 'Site',
        width: 100,
        hideBelowLg: true,
        cell: (row) => row.siteCode,
      },
    ],
    [],
  );

  const filtersActive = JSON.stringify(filters) !== JSON.stringify(emptyFilters);
  const rows = query.data?.content ?? [];

  return (
    <div>
      <PageHeader
        title="Workflow queue"
        subtitle="Defects, compliance renewals, trip exceptions and integration failures with their SLA standing."
        crumbs={[{ label: 'Fleet', to: fleetPaths.dashboard }, { label: 'Workflow queue' }]}
        actions={
          <>
            <Button variant="outline" startIcon="refresh" onClick={query.refetch}>
              Refresh
            </Button>
            <Button variant="accent" startIcon="plus" onClick={() => setRaiseOpen(true)}>
              Raise item
            </Button>
          </>
        }
      />

      <SectionCard flush>
        <FilterBar
          onReset={resetFilters}
          resetDisabled={!filtersActive}
          trailing={
            <div className="flex flex-col gap-1.5">
              <Checkbox
                checked={filters.overdueOnly}
                onChange={(checked) => setFilter('overdueOnly', checked)}
                label="Overdue"
              />
              <Checkbox
                checked={filters.escalatedOnly}
                onChange={(checked) => setFilter('escalatedOnly', checked)}
                label="Escalated"
              />
            </div>
          }
        >
          <SiteSelect
            value={filters.siteCode}
            onChange={(value) => setFilter('siteCode', value)}
            allowEmpty
          />
          <EnumSelect
            label="Status"
            value={filters.status}
            options={FLEET_WORKFLOW_STATUSES}
            onChange={(value) => setFilter('status', value)}
            allowEmpty
          />
          <EnumSelect
            label="Type"
            value={filters.type}
            options={FLEET_WORKFLOW_TYPES}
            onChange={(value) => setFilter('type', value)}
            allowEmpty
          />
          <EnumSelect
            label="Priority"
            value={filters.priority}
            options={WORKFLOW_PRIORITIES}
            onChange={(value) => setFilter('priority', value)}
            allowEmpty
          />
          <TextInput
            label="Assignee"
            value={filters.assignee}
            onChange={(value) => setFilter('assignee', value)}
          />
        </FilterBar>

        <DataState
          loading={query.initialising}
          error={query.error}
          empty={rows.length === 0 && !query.loading}
          emptyTitle="No workflow items match these filters"
          emptyHint="A clear queue is a good sign — or widen the filters to check."
          onRetry={query.refetch}
          minHeight={280}
        >
          <DataTable
            rows={rows}
            columns={columns}
            getRowId={(row) => row.id}
            loading={query.loading}
            onRowClick={(row) => navigate(fleetPaths.workflowDetail(row.id))}
            page={pagination.page}
            pageSize={pagination.pageSize}
            totalElements={query.data?.totalElements ?? 0}
            onPageChange={(page) => setPagination((current) => ({ ...current, page }))}
            onPageSizeChange={(pageSize) => setPagination({ page: 0, pageSize })}
            emptyMessage="No workflow items match these filters."
          />
        </DataState>
      </SectionCard>

      {/* Mounted only while open, so the dialog picks up the current site filter as its default
          and cannot reopen holding a half-typed item from a previous attempt. */}
      {raiseOpen && (
        <RaiseWorkflowItemDialog
          open
          defaultSiteCode={filters.siteCode || defaultSite}
          onClose={() => setRaiseOpen(false)}
          onSaved={() => {
            notifySuccess('Workflow item raised.');
            query.refetch();
          }}
        />
      )}
    </div>
  );
};

export default WorkflowQueuePage;
