import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router';
import { Box, Button, Stack, Typography } from '@mui/material';
import { DataGrid, GridColDef, GridPaginationModel } from '@mui/x-data-grid';
import { VehicleResponse } from 'modules/fleet/api/dto';
import {
  VEHICLE_AVAILABILITY_STATUSES,
  VEHICLE_CATEGORIES,
  VEHICLE_LIFECYCLE_STATUSES,
  VEHICLE_SERVICE_STATUSES,
  VehicleAvailabilityStatus,
  VehicleCategory,
  VehicleLifecycleStatus,
  VehicleServiceStatus,
  humanise,
} from 'modules/fleet/api/enums';
import { vehiclesApi } from 'modules/fleet/api/fleetApi';
import { RegisterVehicleDialog } from 'modules/fleet/dialogs/vehicleDialogs';
import { defaultPageSize, sflActor } from 'shared/api/config';
import DataState from 'shared/components/DataState';
import FilterBar from 'shared/components/FilterBar';
import { useNotifier } from 'shared/components/Notifier';
import PageHeader from 'shared/components/PageHeader';
import SectionCard from 'shared/components/SectionCard';
import StatusChip from 'shared/components/StatusChip';
import { EnumSelect, TextInput } from 'shared/components/fields';
import { formatOdometer } from 'shared/components/format';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { fleetPaths } from 'shared/layout/navigation';
import IconifyIcon from 'components/base/IconifyIcon';

const firstSite = sflActor.sites.split(',')[0]?.trim() ?? '';

interface Filters {
  siteCode: string;
  registrationNumber: string;
  status: VehicleLifecycleStatus | '';
  serviceStatus: VehicleServiceStatus | '';
  availability: VehicleAvailabilityStatus | '';
  category: VehicleCategory | '';
  responsibleUnit: string;
}

const emptyFilters: Filters = {
  siteCode: firstSite,
  registrationNumber: '',
  status: '',
  serviceStatus: '',
  availability: '',
  category: '',
  responsibleUnit: '',
};

/**
 * The vehicle register.
 *
 * Filtering, sorting and paging all run server-side — the service owns site scoping, so a
 * client-side filter over one page would quietly show the wrong denominator.
 */
const VehicleRegisterPage = () => {
  const navigate = useNavigate();
  const { notifySuccess } = useNotifier();
  const [filters, setFilters] = useState<Filters>(emptyFilters);
  const [pagination, setPagination] = useState<GridPaginationModel>({
    page: 0,
    pageSize: defaultPageSize,
  });
  const [registerOpen, setRegisterOpen] = useState(false);

  const setFilter = <K extends keyof Filters>(key: K, value: Filters[K]) => {
    setFilters((current) => ({ ...current, [key]: value }));
    setPagination((current) => ({ ...current, page: 0 }));
  };

  const query = useApiQuery(
    (signal) =>
      vehiclesApi.search(
        {
          siteCode: filters.siteCode || undefined,
          registrationNumber: filters.registrationNumber || undefined,
          status: filters.status || undefined,
          serviceStatus: filters.serviceStatus || undefined,
          availability: filters.availability || undefined,
          category: filters.category || undefined,
          responsibleUnit: filters.responsibleUnit || undefined,
          page: pagination.page,
          size: pagination.pageSize,
        },
        signal,
      ),
    [filters, pagination.page, pagination.pageSize],
  );

  const columns = useMemo<GridColDef<VehicleResponse>[]>(
    () => [
      {
        field: 'registrationNumber',
        headerName: 'Registration',
        minWidth: 140,
        flex: 1,
        renderCell: ({ row }) => (
          <Stack sx={{ py: 0.75 }}>
            <Typography variant="body2" fontWeight={700}>
              {row.registrationNumber}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              {row.make} {row.model} · {row.manufactureYear}
            </Typography>
          </Stack>
        ),
      },
      {
        field: 'category',
        headerName: 'Category',
        minWidth: 150,
        valueFormatter: (value: VehicleCategory) => humanise(value),
      },
      { field: 'siteCode', headerName: 'Site', minWidth: 100 },
      {
        field: 'lifecycleStatus',
        headerName: 'Lifecycle',
        minWidth: 130,
        renderCell: ({ row }) => <StatusChip value={row.lifecycleStatus} />,
      },
      {
        field: 'serviceStatus',
        headerName: 'Service',
        minWidth: 140,
        renderCell: ({ row }) => <StatusChip value={row.serviceStatus} />,
      },
      {
        field: 'availabilityStatus',
        headerName: 'Availability',
        minWidth: 130,
        renderCell: ({ row }) => <StatusChip value={row.availabilityStatus} />,
      },
      {
        field: 'odometerValue',
        headerName: 'Odometer',
        minWidth: 130,
        align: 'right',
        headerAlign: 'right',
        renderCell: ({ row }) => (
          <Typography variant="body2">
            {formatOdometer(row.odometerValue, row.odometerUnit)}
          </Typography>
        ),
      },
      { field: 'responsibleUnit', headerName: 'Responsible unit', minWidth: 180, flex: 1 },
      {
        field: 'emergencyOnly',
        headerName: 'Restriction',
        minWidth: 130,
        renderCell: ({ row }) =>
          row.emergencyOnly ? (
            <StatusChip value="EMERGENCY_ONLY" label="Emergency only" tone="accent" />
          ) : (
            <Typography variant="caption" color="text.disabled">
              None
            </Typography>
          ),
      },
    ],
    [],
  );

  const filtersActive = JSON.stringify(filters) !== JSON.stringify(emptyFilters);

  return (
    <Box>
      <PageHeader
        title="Vehicle register"
        subtitle="Every vehicle in your site scope, with its lifecycle, service and availability standing."
        crumbs={[{ label: 'Fleet', to: fleetPaths.dashboard }, { label: 'Vehicle register' }]}
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
              onClick={() => setRegisterOpen(true)}
              startIcon={<IconifyIcon icon="material-symbols:add-rounded" />}
            >
              Register vehicle
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
          <TextInput
            label="Registration number"
            value={filters.registrationNumber}
            onChange={(value) => setFilter('registrationNumber', value)}
          />
          <EnumSelect
            label="Lifecycle"
            value={filters.status}
            options={VEHICLE_LIFECYCLE_STATUSES}
            onChange={(value) => setFilter('status', value)}
            allowEmpty
          />
          <EnumSelect
            label="Service status"
            value={filters.serviceStatus}
            options={VEHICLE_SERVICE_STATUSES}
            onChange={(value) => setFilter('serviceStatus', value)}
            allowEmpty
          />
          <EnumSelect
            label="Availability"
            value={filters.availability}
            options={VEHICLE_AVAILABILITY_STATUSES}
            onChange={(value) => setFilter('availability', value)}
            allowEmpty
          />
          <EnumSelect
            label="Category"
            value={filters.category}
            options={VEHICLE_CATEGORIES}
            onChange={(value) => setFilter('category', value)}
            allowEmpty
          />
          <TextInput
            label="Responsible unit"
            value={filters.responsibleUnit}
            onChange={(value) => setFilter('responsibleUnit', value)}
          />
        </FilterBar>

        <Box sx={{ p: 2 }}>
          <DataState
            loading={query.initialising}
            error={query.error}
            empty={(query.data?.content.length ?? 0) === 0 && !query.loading}
            emptyTitle="No vehicles match these filters"
            emptyHint="Adjust the filters, or register the first vehicle for this site."
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
              onRowClick={(params) => navigate(fleetPaths.vehicleDetail(String(params.id)))}
              sx={{ border: 0, '& .MuiDataGrid-row': { cursor: 'pointer' } }}
            />
          </DataState>
        </Box>
      </SectionCard>

      <RegisterVehicleDialog
        open={registerOpen}
        defaultSiteCode={filters.siteCode || firstSite}
        onClose={() => setRegisterOpen(false)}
        onSaved={() => {
          notifySuccess('Vehicle registered.');
          query.refetch();
        }}
      />
    </Box>
  );
};

export default VehicleRegisterPage;
