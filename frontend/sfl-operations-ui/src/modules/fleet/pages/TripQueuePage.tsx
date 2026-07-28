import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router';
import { Box, Button, Stack, Typography } from '@mui/material';
import { DataGrid, GridColDef, GridPaginationModel } from '@mui/x-data-grid';
import { TripResponse, TripStatusValue } from 'modules/fleet/api/dto';
import { OPERATING_MODES, OperatingMode, TRIP_STATUSES, humanise } from 'modules/fleet/api/enums';
import { tripsApi } from 'modules/fleet/api/fleetApi';
import { CreateTripDialog } from 'modules/fleet/dialogs/tripDialogs';
import { defaultPageSize, sflActor } from 'shared/api/config';
import DataState from 'shared/components/DataState';
import FilterBar from 'shared/components/FilterBar';
import { useNotifier } from 'shared/components/Notifier';
import PageHeader from 'shared/components/PageHeader';
import SectionCard from 'shared/components/SectionCard';
import StatusChip from 'shared/components/StatusChip';
import { DateInput, EnumSelect, TextInput } from 'shared/components/fields';
import { formatDateTime, fromLocalInputValue } from 'shared/components/format';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { fleetPaths } from 'shared/layout/navigation';
import IconifyIcon from 'components/base/IconifyIcon';

const firstSite = sflActor.sites.split(',')[0]?.trim() ?? '';

interface Filters {
  siteCode: string;
  status: TripStatusValue | '';
  operatingMode: OperatingMode | '';
  from: string;
  to: string;
}

const emptyFilters: Filters = {
  siteCode: firstSite,
  status: '',
  operatingMode: '',
  from: '',
  to: '',
};

/** The trip queue: plan, then work each trip through assignment, start and closure. */
const TripQueuePage = () => {
  const navigate = useNavigate();
  const { notifySuccess } = useNotifier();
  const [filters, setFilters] = useState<Filters>(emptyFilters);
  const [pagination, setPagination] = useState<GridPaginationModel>({
    page: 0,
    pageSize: defaultPageSize,
  });
  const [createOpen, setCreateOpen] = useState(false);

  const setFilter = <K extends keyof Filters>(key: K, value: Filters[K]) => {
    setFilters((current) => ({ ...current, [key]: value }));
    setPagination((current) => ({ ...current, page: 0 }));
  };

  const query = useApiQuery(
    (signal) =>
      tripsApi.search(
        {
          siteCode: filters.siteCode || undefined,
          status: filters.status || undefined,
          operatingMode: filters.operatingMode || undefined,
          from: filters.from ? fromLocalInputValue(filters.from) : undefined,
          to: filters.to ? fromLocalInputValue(filters.to) : undefined,
          page: pagination.page,
          size: pagination.pageSize,
        },
        signal,
      ),
    [filters, pagination.page, pagination.pageSize],
  );

  const columns = useMemo<GridColDef<TripResponse>[]>(
    () => [
      {
        field: 'tripNumber',
        headerName: 'Trip',
        minWidth: 200,
        flex: 1,
        renderCell: ({ row }) => (
          <Stack sx={{ py: 0.75 }}>
            <Typography variant="body2" fontWeight={700}>
              {row.tripNumber}
            </Typography>
            <Typography variant="caption" color="text.secondary" noWrap>
              {row.origin} → {row.destination}
            </Typography>
          </Stack>
        ),
      },
      { field: 'purpose', headerName: 'Purpose', minWidth: 200, flex: 1 },
      {
        field: 'plannedStart',
        headerName: 'Planned window',
        minWidth: 210,
        renderCell: ({ row }) => (
          <Stack sx={{ py: 0.75 }}>
            <Typography variant="body2">{formatDateTime(row.plannedStart)}</Typography>
            <Typography variant="caption" color="text.secondary">
              to {formatDateTime(row.plannedEnd)}
            </Typography>
          </Stack>
        ),
      },
      {
        field: 'operatingMode',
        headerName: 'Mode',
        minWidth: 130,
        valueFormatter: (value: OperatingMode) => humanise(value),
      },
      {
        field: 'status',
        headerName: 'Status',
        minWidth: 140,
        renderCell: ({ row }) => <StatusChip value={row.status} />,
      },
      {
        field: 'vehicleId',
        headerName: 'Assignment',
        minWidth: 140,
        renderCell: ({ row }) =>
          row.vehicleId && row.driverId ? (
            <StatusChip value="ASSIGNED" label="Vehicle & driver" tone="active" />
          ) : (
            <StatusChip value="PLANNED" label="Unassigned" tone="caution" />
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
        title="Trips & assignments"
        subtitle="Plan a movement, assign a vehicle and driver, then start and close it against evidence."
        crumbs={[{ label: 'Fleet', to: fleetPaths.dashboard }, { label: 'Trips' }]}
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
              onClick={() => setCreateOpen(true)}
              startIcon={<IconifyIcon icon="material-symbols:add-rounded" />}
            >
              Plan a trip
            </Button>
          </>
        }
      />

      <SectionCard flush>
        <FilterBar onReset={() => setFilters(emptyFilters)} resetDisabled={!filtersActive}>
          <TextInput
            label="Site code"
            value={filters.siteCode}
            onChange={(value) => setFilter('siteCode', value)}
          />
          <EnumSelect
            label="Status"
            value={filters.status}
            options={TRIP_STATUSES}
            onChange={(value) => setFilter('status', value)}
            allowEmpty
          />
          <EnumSelect
            label="Operating mode"
            value={filters.operatingMode}
            options={OPERATING_MODES}
            onChange={(value) => setFilter('operatingMode', value)}
            allowEmpty
          />
          <DateInput
            label="From"
            withTime
            value={filters.from}
            onChange={(value) => setFilter('from', value)}
          />
          <DateInput
            label="To"
            withTime
            value={filters.to}
            onChange={(value) => setFilter('to', value)}
          />
        </FilterBar>

        <Box sx={{ p: 2 }}>
          <DataState
            loading={query.initialising}
            error={query.error}
            empty={(query.data?.content.length ?? 0) === 0 && !query.loading}
            emptyTitle="No trips match these filters"
            emptyHint="Plan a trip to get a movement into the queue."
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
              onRowClick={(params) => navigate(fleetPaths.tripDetail(String(params.id)))}
              sx={{ border: 0, '& .MuiDataGrid-row': { cursor: 'pointer' } }}
            />
          </DataState>
        </Box>
      </SectionCard>

      <CreateTripDialog
        open={createOpen}
        defaultSiteCode={filters.siteCode || firstSite}
        onClose={() => setCreateOpen(false)}
        onSaved={() => {
          notifySuccess('Trip created.');
          query.refetch();
        }}
      />
    </Box>
  );
};

export default TripQueuePage;
