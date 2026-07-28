import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router';
import { Box, Button, Stack, Typography } from '@mui/material';
import { DataGrid, GridColDef, GridPaginationModel } from '@mui/x-data-grid';
import { DriverResponse } from 'modules/fleet/api/dto';
import {
  DRIVER_ELIGIBILITY_STATUSES,
  DRIVER_LIFECYCLE_STATUSES,
  DriverEligibilityStatus,
  DriverLifecycleStatus,
} from 'modules/fleet/api/enums';
import { driversApi } from 'modules/fleet/api/fleetApi';
import { RegisterDriverDialog } from 'modules/fleet/dialogs/driverDialogs';
import { defaultPageSize, sflActor } from 'shared/api/config';
import DataState from 'shared/components/DataState';
import FilterBar from 'shared/components/FilterBar';
import { useNotifier } from 'shared/components/Notifier';
import PageHeader from 'shared/components/PageHeader';
import SectionCard from 'shared/components/SectionCard';
import StatusChip from 'shared/components/StatusChip';
import { DateInput, EnumSelect, TextInput } from 'shared/components/fields';
import { formatDate, formatDaysRemaining } from 'shared/components/format';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { fleetPaths } from 'shared/layout/navigation';
import IconifyIcon from 'components/base/IconifyIcon';

const firstSite = sflActor.sites.split(',')[0]?.trim() ?? '';

interface Filters {
  siteCode: string;
  search: string;
  status: DriverLifecycleStatus | '';
  eligibility: DriverEligibilityStatus | '';
  responsibleUnit: string;
  licenceExpiringBefore: string;
}

const emptyFilters: Filters = {
  siteCode: firstSite,
  search: '',
  status: '',
  eligibility: '',
  responsibleUnit: '',
  licenceExpiringBefore: '',
};

/** The driver register: licence standing, lifecycle and eligibility in one scan. */
const DriverRegisterPage = () => {
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
      driversApi.search(
        {
          siteCode: filters.siteCode || undefined,
          search: filters.search || undefined,
          status: filters.status || undefined,
          eligibility: filters.eligibility || undefined,
          responsibleUnit: filters.responsibleUnit || undefined,
          licenceExpiringBefore: filters.licenceExpiringBefore || undefined,
          page: pagination.page,
          size: pagination.pageSize,
        },
        signal,
      ),
    [filters, pagination.page, pagination.pageSize],
  );

  const columns = useMemo<GridColDef<DriverResponse>[]>(
    () => [
      {
        field: 'displayName',
        headerName: 'Driver',
        minWidth: 200,
        flex: 1,
        renderCell: ({ row }) => (
          <Stack sx={{ py: 0.75 }}>
            <Typography variant="body2" fontWeight={700}>
              {row.displayName}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              {row.staffReference} · {row.siteCode}
            </Typography>
          </Stack>
        ),
      },
      {
        field: 'licenceClass',
        headerName: 'Licence',
        minWidth: 150,
        renderCell: ({ row }) => (
          <Stack sx={{ py: 0.75 }}>
            <Typography variant="body2" fontWeight={600}>
              Class {row.licenceClass}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              {row.licenceNumberMasked ? 'Number masked' : (row.licenceNumber ?? '—')}
            </Typography>
          </Stack>
        ),
      },
      {
        field: 'licenceExpiresOn',
        headerName: 'Licence expiry',
        minWidth: 170,
        renderCell: ({ row }) => (
          <Stack sx={{ py: 0.75 }}>
            <Typography variant="body2">{formatDate(row.licenceExpiresOn)}</Typography>
            <Typography
              variant="caption"
              color={
                row.daysUntilLicenceExpiry < 0
                  ? 'error.main'
                  : row.daysUntilLicenceExpiry < 30
                    ? 'warning.main'
                    : 'text.secondary'
              }
            >
              {formatDaysRemaining(row.daysUntilLicenceExpiry)}
            </Typography>
          </Stack>
        ),
      },
      {
        field: 'medicalClearanceExpiresOn',
        headerName: 'Medical clearance',
        minWidth: 150,
        valueFormatter: (value: string | null) => formatDate(value),
      },
      {
        field: 'lifecycleStatus',
        headerName: 'Lifecycle',
        minWidth: 130,
        renderCell: ({ row }) => <StatusChip value={row.lifecycleStatus} />,
      },
      {
        field: 'eligibilityStatus',
        headerName: 'Eligibility',
        minWidth: 140,
        renderCell: ({ row }) => <StatusChip value={row.eligibilityStatus} />,
      },
      { field: 'responsibleUnit', headerName: 'Responsible unit', minWidth: 180, flex: 1 },
    ],
    [],
  );

  const filtersActive = JSON.stringify(filters) !== JSON.stringify(emptyFilters);

  return (
    <Box>
      <PageHeader
        title="Driver register"
        subtitle="Licence standing, lifecycle and eligibility for every driver in your site scope."
        crumbs={[{ label: 'Fleet', to: fleetPaths.dashboard }, { label: 'Driver register' }]}
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
              startIcon={<IconifyIcon icon="material-symbols:person-add-outline-rounded" />}
            >
              Register driver
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
            label="Search name or reference"
            value={filters.search}
            onChange={(value) => setFilter('search', value)}
          />
          <EnumSelect
            label="Lifecycle"
            value={filters.status}
            options={DRIVER_LIFECYCLE_STATUSES}
            onChange={(value) => setFilter('status', value)}
            allowEmpty
          />
          <EnumSelect
            label="Eligibility"
            value={filters.eligibility}
            options={DRIVER_ELIGIBILITY_STATUSES}
            onChange={(value) => setFilter('eligibility', value)}
            allowEmpty
          />
          <TextInput
            label="Responsible unit"
            value={filters.responsibleUnit}
            onChange={(value) => setFilter('responsibleUnit', value)}
          />
          <DateInput
            label="Licence expiring before"
            value={filters.licenceExpiringBefore}
            onChange={(value) => setFilter('licenceExpiringBefore', value)}
          />
        </FilterBar>

        <Box sx={{ p: 2 }}>
          <DataState
            loading={query.initialising}
            error={query.error}
            empty={(query.data?.content.length ?? 0) === 0 && !query.loading}
            emptyTitle="No drivers match these filters"
            emptyHint="Adjust the filters, or register the first driver for this site."
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
              onRowClick={(params) => navigate(fleetPaths.driverDetail(String(params.id)))}
              sx={{ border: 0, '& .MuiDataGrid-row': { cursor: 'pointer' } }}
            />
          </DataState>
        </Box>
      </SectionCard>

      <RegisterDriverDialog
        open={registerOpen}
        defaultSiteCode={filters.siteCode || firstSite}
        onClose={() => setRegisterOpen(false)}
        onSaved={() => {
          notifySuccess('Driver registered.');
          query.refetch();
        }}
      />
    </Box>
  );
};

export default DriverRegisterPage;
