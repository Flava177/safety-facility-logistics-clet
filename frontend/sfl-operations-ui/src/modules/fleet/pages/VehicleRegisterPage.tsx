import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router';
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
import { EnumSelect, TextInput } from 'shared/components/fields';
import { formatOdometer } from 'shared/components/format';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { fleetPaths } from 'shared/layout/navigation';

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
  siteCode: defaultSite,
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
  const [pagination, setPagination] = useState({ page: 0, pageSize: defaultPageSize });
  const [registerOpen, setRegisterOpen] = useState(false);

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

  const columns = useMemo<Column<VehicleResponse>[]>(
    () => [
      {
        key: 'registrationNumber',
        header: 'Registration',
        width: 180,
        cell: (row) => (
          <CellStack
            primary={row.registrationNumber}
            secondary={`${row.make} ${row.model} · ${row.manufactureYear}`}
          />
        ),
      },
      {
        key: 'category',
        header: 'Category',
        width: 150,
        cell: (row) => humanise(row.category),
      },
      // The register is normally read one site at a time, so the site column is laptop-optional.
      {
        key: 'siteCode',
        header: 'Site',
        width: 100,
        hideBelowLg: true,
        cell: (row) => row.siteCode,
      },
      {
        key: 'lifecycleStatus',
        header: 'Lifecycle',
        width: 130,
        cell: (row) => <StatusChip value={row.lifecycleStatus} />,
      },
      {
        key: 'serviceStatus',
        header: 'Service',
        width: 140,
        cell: (row) => <StatusChip value={row.serviceStatus} />,
      },
      {
        key: 'availabilityStatus',
        header: 'Availability',
        width: 130,
        cell: (row) => <StatusChip value={row.availabilityStatus} />,
      },
      {
        key: 'odometerValue',
        header: 'Odometer',
        width: 130,
        align: 'right',
        cell: (row) => formatOdometer(row.odometerValue, row.odometerUnit),
      },
      {
        key: 'responsibleUnit',
        header: 'Responsible unit',
        width: 180,
        cell: (row) => row.responsibleUnit,
      },
      {
        key: 'emergencyOnly',
        header: 'Restriction',
        width: 130,
        cell: (row) =>
          row.emergencyOnly ? (
            <StatusChip value="EMERGENCY_ONLY" label="Emergency only" tone="accent" />
          ) : (
            <span className="text-theme-xs text-gray-600">None</span>
          ),
      },
    ],
    [],
  );

  const filtersActive = JSON.stringify(filters) !== JSON.stringify(emptyFilters);

  return (
    <div>
      <PageHeader
        title="Vehicle register"
        subtitle="Every vehicle in your site scope, with its lifecycle, service and availability standing."
        crumbs={[{ label: 'Fleet', to: fleetPaths.dashboard }, { label: 'Vehicle register' }]}
        actions={
          <>
            <Button variant="outline" startIcon="refresh" onClick={query.refetch}>
              Refresh
            </Button>
            <Button variant="accent" startIcon="plus" onClick={() => setRegisterOpen(true)}>
              Register vehicle
            </Button>
          </>
        }
      />

      <SectionCard flush>
        <FilterBar onReset={resetFilters} resetDisabled={!filtersActive}>
          <SiteSelect
            value={filters.siteCode}
            onChange={(value) => setFilter('siteCode', value)}
            allowEmpty
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

        <DataState
          loading={query.initialising}
          error={query.error}
          empty={(query.data?.content.length ?? 0) === 0 && !query.loading}
          emptyTitle="No vehicles match these filters"
          emptyHint="Adjust the filters, or register the first vehicle for this site."
          onRetry={query.refetch}
          minHeight={280}
        >
          <DataTable
            rows={query.data?.content ?? []}
            columns={columns}
            getRowId={(row) => row.id}
            loading={query.loading}
            onRowClick={(row) => navigate(fleetPaths.vehicleDetail(row.id))}
            page={pagination.page}
            pageSize={pagination.pageSize}
            totalElements={query.data?.totalElements ?? 0}
            onPageChange={(page) => setPagination((current) => ({ ...current, page }))}
            onPageSizeChange={(pageSize) => setPagination({ page: 0, pageSize })}
            emptyMessage="No vehicles match these filters."
          />
        </DataState>
      </SectionCard>

      {/* Mounted only while open, so the dialog picks up the current site filter as its default
          and cannot reopen holding a half-typed registration from a previous attempt. */}
      {registerOpen && (
        <RegisterVehicleDialog
          open
          defaultSiteCode={filters.siteCode || defaultSite}
          onClose={() => setRegisterOpen(false)}
          onSaved={() => {
            notifySuccess('Vehicle registered.');
            query.refetch();
          }}
        />
      )}
    </div>
  );
};

export default VehicleRegisterPage;
