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
import FacetFilter from 'shared/components/FacetFilter';
import FilterBar, { ActiveFilter } from 'shared/components/FilterBar';
import { useNotifier } from 'shared/components/Notifier';
import PageHeader from 'shared/components/PageHeader';
import SectionCard from 'shared/components/SectionCard';
import SiteSelect, { defaultSite } from 'shared/components/SiteSelect';
import SearchInput from 'shared/components/SearchInput';
import StatusChip from 'shared/components/StatusChip';
import { formatOdometer } from 'shared/components/format';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { fleetPaths } from 'shared/layout/navigation';
import { canManageVehicles } from '../api/access';

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
 * `FacetFilter` is built for a union the operator composes themselves - the search endpoint takes
 * one value per axis, not several, so "select" here always replaces rather than adds. Toggling the
 * option already active clears it, same as the dropdown it replaces; toggling a different one while
 * one is active swaps to the new choice instead of appearing to hold both.
 */
const pickSingle = <T extends string>(current: T | '', next: string[]): T | '' => {
  if (next.length === 0) {
    return '';
  }
  return (next.find((value) => value !== current) ?? next[0]) as T;
};

/**
 * The vehicle register.
 *
 * Filtering, sorting and paging all run server-side - the service owns site scoping, so a
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

  /*
    Seven controls, six of them dropdowns, and a closed dropdown says nothing. This register is
    where that costs the most: filtered to a category and a service status, the table can be two
    rows and look like a broken query. The chips say which two constraints did it.
  */
  const clearFilter = (key: keyof Filters) => () => setFilter(key, emptyFilters[key]);
  const chip = (key: keyof Filters, label: string, value: string): ActiveFilter[] =>
    filters[key] === emptyFilters[key]
      ? []
      : [{ key, label, value, onClear: clearFilter(key) }];

  const activeFilters: ActiveFilter[] = [
    ...chip('siteCode', 'Site', filters.siteCode === '' ? 'All sites' : filters.siteCode),
    ...chip('registrationNumber', 'Registration', filters.registrationNumber),
    ...chip('status', 'Lifecycle', humanise(filters.status)),
    ...chip('serviceStatus', 'Service', humanise(filters.serviceStatus)),
    ...chip('availability', 'Availability', humanise(filters.availability)),
    ...chip('category', 'Category', humanise(filters.category)),
    ...chip('responsibleUnit', 'Unit', filters.responsibleUnit),
  ];

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
            {/* Hidden, not disabled: a driver will never hold FLEET_VEHICLE_MANAGE, and a
                permanently greyed control is a question they cannot answer. */}
            {canManageVehicles() && (
              <Button variant="accent" startIcon="plus" onClick={() => setRegisterOpen(true)}>
                Register vehicle
              </Button>
            )}
          </>
        }
      />

      <SectionCard flush>
        <FilterBar onReset={resetFilters} active={activeFilters}>
          <SiteSelect
            value={filters.siteCode}
            onChange={(value) => setFilter('siteCode', value)}
            allowEmpty
          />
          <SearchInput
            label="Registration number"
            value={filters.registrationNumber}
            onChange={(value) => setFilter('registrationNumber', value)}
            placeholder="GT-1234-24"
          />
          <FacetFilter
            label="Lifecycle"
            selected={filters.status ? [filters.status] : []}
            onChange={(next) => setFilter('status', pickSingle(filters.status, next))}
            options={VEHICLE_LIFECYCLE_STATUSES.map((value) => ({ value, label: humanise(value) }))}
          />
          <FacetFilter
            label="Service status"
            selected={filters.serviceStatus ? [filters.serviceStatus] : []}
            onChange={(next) => setFilter('serviceStatus', pickSingle(filters.serviceStatus, next))}
            options={VEHICLE_SERVICE_STATUSES.map((value) => ({ value, label: humanise(value) }))}
          />
          <FacetFilter
            label="Availability"
            selected={filters.availability ? [filters.availability] : []}
            onChange={(next) => setFilter('availability', pickSingle(filters.availability, next))}
            options={VEHICLE_AVAILABILITY_STATUSES.map((value) => ({ value, label: humanise(value) }))}
          />
          <FacetFilter
            label="Category"
            selected={filters.category ? [filters.category] : []}
            onChange={(next) => setFilter('category', pickSingle(filters.category, next))}
            options={VEHICLE_CATEGORIES.map((value) => ({ value, label: humanise(value) }))}
          />
          <SearchInput
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
