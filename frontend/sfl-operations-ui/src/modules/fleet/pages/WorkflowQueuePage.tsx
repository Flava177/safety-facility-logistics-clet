import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router';
import { Box, Button, FormControlLabel, Stack, Switch, Typography } from '@mui/material';
import { DataGrid, GridColDef, GridPaginationModel } from '@mui/x-data-grid';
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
import { defaultPageSize, sflActor } from 'shared/api/config';
import DataState from 'shared/components/DataState';
import FilterBar from 'shared/components/FilterBar';
import { useNotifier } from 'shared/components/Notifier';
import PageHeader from 'shared/components/PageHeader';
import SectionCard from 'shared/components/SectionCard';
import StatusChip from 'shared/components/StatusChip';
import { EnumSelect, TextInput } from 'shared/components/fields';
import { formatDateTime } from 'shared/components/format';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { fleetPaths } from 'shared/layout/navigation';
import IconifyIcon from 'components/base/IconifyIcon';

const firstSite = sflActor.sites.split(',')[0]?.trim() ?? '';

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
  siteCode: firstSite,
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
  const [pagination, setPagination] = useState<GridPaginationModel>({
    page: 0,
    pageSize: defaultPageSize,
  });
  const [raiseOpen, setRaiseOpen] = useState(false);

  const setFilter = <K extends keyof Filters>(key: K, value: Filters[K]) => {
    setFilters((current) => ({ ...current, [key]: value }));
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

  const columns = useMemo<GridColDef<WorkflowItemResponse>[]>(
    () => [
      {
        field: 'workflowNumber',
        headerName: 'Item',
        minWidth: 230,
        flex: 1.2,
        renderCell: ({ row }) => (
          <Stack sx={{ py: 0.75 }}>
            <Typography variant="body2" fontWeight={700} noWrap>
              {row.workflowNumber}
            </Typography>
            <Typography variant="caption" color="text.secondary" noWrap>
              {row.title}
            </Typography>
          </Stack>
        ),
      },
      {
        field: 'workflowType',
        headerName: 'Type',
        minWidth: 170,
        valueFormatter: (value: FleetWorkflowType) => humanise(value),
      },
      {
        field: 'status',
        headerName: 'Status',
        minWidth: 130,
        renderCell: ({ row }) => <StatusChip value={row.status} />,
      },
      {
        field: 'priority',
        headerName: 'Priority',
        minWidth: 110,
        renderCell: ({ row }) => <StatusChip value={row.priority} />,
      },
      {
        field: 'severity',
        headerName: 'Severity',
        minWidth: 110,
        renderCell: ({ row }) => <StatusChip value={row.severity} />,
      },
      {
        field: 'slaDueAt',
        headerName: 'SLA',
        minWidth: 200,
        renderCell: ({ row }) => (
          <Stack sx={{ py: 0.75 }}>
            <Typography variant="body2" color={row.slaBreached ? 'error.main' : 'text.primary'}>
              {formatDateTime(row.slaDueAt)}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              {row.slaBreached ? 'Breached' : 'Within target'} · level {row.escalationLevel}
            </Typography>
          </Stack>
        ),
      },
      {
        field: 'assignee',
        headerName: 'Assignee',
        minWidth: 150,
        renderCell: ({ row }) =>
          row.assignee ? (
            <Typography variant="body2">{row.assignee}</Typography>
          ) : (
            <Typography variant="caption" color="text.disabled">
              Unassigned
            </Typography>
          ),
      },
      { field: 'siteCode', headerName: 'Site', minWidth: 100 },
    ],
    [],
  );

  const filtersActive = JSON.stringify(filters) !== JSON.stringify(emptyFilters);

  return (
    <Box>
      <PageHeader
        title="Workflow queue"
        subtitle="Defects, compliance renewals, trip exceptions and integration failures with their SLA standing."
        crumbs={[{ label: 'Fleet', to: fleetPaths.dashboard }, { label: 'Workflow queue' }]}
        actions={
          <>
            <Button
              variant="soft"
              color="neutral"
              onClick={query.refetch}
              startIcon={<IconifyIcon icon="material-symbols:refresh-rounded" />}
            >
              Refresh
            </Button>
            <Button
              variant="contained"
              color="secondary"
              onClick={() => setRaiseOpen(true)}
              startIcon={<IconifyIcon icon="material-symbols:add-rounded" />}
            >
              Raise item
            </Button>
          </>
        }
      />

      <SectionCard flush>
        <FilterBar
          onReset={() => setFilters(emptyFilters)}
          resetDisabled={!filtersActive}
          trailing={
            <Stack direction="row" spacing={0.5} alignItems="center">
              <FormControlLabel
                control={
                  <Switch
                    size="small"
                    checked={filters.overdueOnly}
                    onChange={(event) => setFilter('overdueOnly', event.target.checked)}
                  />
                }
                label={<Typography variant="body2">Overdue</Typography>}
              />
              <FormControlLabel
                control={
                  <Switch
                    size="small"
                    checked={filters.escalatedOnly}
                    onChange={(event) => setFilter('escalatedOnly', event.target.checked)}
                  />
                }
                label={<Typography variant="body2">Escalated</Typography>}
              />
            </Stack>
          }
        >
          <TextInput
            label="Site code"
            value={filters.siteCode}
            onChange={(value) => setFilter('siteCode', value)}
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

        <Box sx={{ p: 2 }}>
          <DataState
            loading={query.initialising}
            error={query.error}
            empty={(query.data?.content.length ?? 0) === 0 && !query.loading}
            emptyTitle="No workflow items match these filters"
            emptyHint="A clear queue is a good sign — or widen the filters to check."
            onRetry={query.refetch}
            minHeight={280}
          >
            <DataGrid
              rows={query.data?.content ?? []}
              columns={columns}
              getRowId={(row) => row.id}
              rowHeight={56}
              disableColumnMenu
              loading={query.loading}
              paginationMode="server"
              rowCount={query.data?.totalElements ?? 0}
              paginationModel={pagination}
              onPaginationModelChange={setPagination}
              pageSizeOptions={[10, 25, 50, 100]}
              onRowClick={(params) => navigate(fleetPaths.workflowDetail(String(params.id)))}
              sx={{ border: 0, '& .MuiDataGrid-row': { cursor: 'pointer' } }}
            />
          </DataState>
        </Box>
      </SectionCard>

      <RaiseWorkflowItemDialog
        open={raiseOpen}
        defaultSiteCode={filters.siteCode || firstSite}
        onClose={() => setRaiseOpen(false)}
        onSaved={() => {
          notifySuccess('Workflow item raised.');
          query.refetch();
        }}
      />
    </Box>
  );
};

export default WorkflowQueuePage;
