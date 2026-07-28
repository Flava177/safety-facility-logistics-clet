import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router';
import { DriverResponse } from 'modules/fleet/api/dto';
import {
  DRIVER_ELIGIBILITY_STATUSES,
  DRIVER_LIFECYCLE_STATUSES,
  DriverEligibilityStatus,
  DriverLifecycleStatus,
} from 'modules/fleet/api/enums';
import { describeDriverEligibility } from 'modules/fleet/api/driverEligibility';
import { driversApi } from 'modules/fleet/api/fleetApi';
import { RegisterDriverDialog } from 'modules/fleet/dialogs/driverDialogs';
import { defaultPageSize } from 'shared/api/config';
import Button from 'shared/components/Button';
import DataState from 'shared/components/DataState';
import DataTable, { CellStack, Column } from 'shared/components/DataTable';
import { DateField } from 'shared/components/DateField';
import FilterBar from 'shared/components/FilterBar';
import { useNotifier } from 'shared/components/Notifier';
import PageHeader from 'shared/components/PageHeader';
import SectionCard from 'shared/components/SectionCard';
import SiteSelect, { defaultSite } from 'shared/components/SiteSelect';
import StatusChip from 'shared/components/StatusChip';
import { EnumSelect, TextInput } from 'shared/components/fields';
import { formatDate, formatDaysRemaining } from 'shared/components/format';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { fleetPaths } from 'shared/layout/navigation';

interface Filters {
  siteCode: string;
  search: string;
  status: DriverLifecycleStatus | '';
  eligibility: DriverEligibilityStatus | '';
  responsibleUnit: string;
  licenceExpiringBefore: string;
}

const emptyFilters: Filters = {
  siteCode: defaultSite,
  search: '',
  status: '',
  eligibility: '',
  responsibleUnit: '',
  licenceExpiringBefore: '',
};

/** Licence expiry earns colour: an expired licence is a refusal at assignment time, not a note. */
const expiryTone = (days: number) =>
  days < 0 ? 'text-error-600' : days < 30 ? 'text-warning-600' : 'text-gray-500';

/** The driver register: licence standing, lifecycle and eligibility in one scan. */
const DriverRegisterPage = () => {
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

  const columns = useMemo<Column<DriverResponse>[]>(
    () => [
      {
        key: 'displayName',
        header: 'Driver',
        width: 200,
        cell: (row) => (
          <CellStack
            primary={row.displayName}
            secondary={`${row.staffReference} · ${row.siteCode}`}
          />
        ),
      },
      {
        key: 'licenceClass',
        header: 'Licence',
        width: 150,
        cell: (row) => (
          <CellStack
            primary={`Class ${row.licenceClass}`}
            secondary={row.licenceNumberMasked ? 'Number masked' : (row.licenceNumber ?? '—')}
          />
        ),
      },
      {
        key: 'licenceExpiresOn',
        header: 'Licence expiry',
        width: 170,
        cell: (row) => (
          <div className="min-w-0">
            <div className="truncate">{formatDate(row.licenceExpiresOn)}</div>
            <div className={`truncate text-theme-xs ${expiryTone(row.daysUntilLicenceExpiry)}`}>
              {formatDaysRemaining(row.daysUntilLicenceExpiry)}
            </div>
          </div>
        ),
      },
      {
        key: 'medicalClearanceExpiresOn',
        header: 'Medical clearance',
        width: 150,
        cell: (row) => formatDate(row.medicalClearanceExpiresOn),
      },
      {
        key: 'lifecycleStatus',
        header: 'Lifecycle',
        width: 130,
        cell: (row) => <StatusChip value={row.lifecycleStatus} />,
      },
      {
        key: 'eligibilityStatus',
        header: 'Eligibility',
        width: 230,
        // The leading reason only: a scan of the register should explain itself without a click,
        // and the full list is one row-click away on the driver record.
        cell: (row) => {
          const [reason] = describeDriverEligibility(row);
          return (
            <div className="min-w-0">
              <StatusChip value={row.eligibilityStatus} />
              {reason && <p className="mt-1 text-theme-xs text-gray-600">{reason}</p>}
            </div>
          );
        },
      },
      {
        key: 'responsibleUnit',
        header: 'Responsible unit',
        width: 180,
        hideBelowLg: true,
        cell: (row) => row.responsibleUnit,
      },
    ],
    [],
  );

  const filtersActive = JSON.stringify(filters) !== JSON.stringify(emptyFilters);
  const noResults = (query.data?.content.length ?? 0) === 0 && !query.loading;
  // The table is edge-to-edge in a flush card; only the loading, error and empty panels need padding.
  const panelOnly = query.initialising || Boolean(query.error) || noResults;

  return (
    <div>
      <PageHeader
        title="Driver register"
        subtitle="Licence standing, lifecycle and eligibility for every driver in your site scope."
        crumbs={[{ label: 'Fleet', to: fleetPaths.dashboard }, { label: 'Driver register' }]}
        actions={
          <>
            <Button variant="outline" startIcon="refresh" onClick={query.refetch}>
              Refresh
            </Button>
            <Button variant="accent" startIcon="user-plus" onClick={() => setRegisterOpen(true)}>
              Register driver
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
          <DateField
            label="Licence expiring before"
            value={filters.licenceExpiringBefore}
            onChange={(value) => setFilter('licenceExpiringBefore', value)}
          />
        </FilterBar>

        <div className={panelOnly ? 'p-4' : undefined}>
          <DataState
            loading={query.initialising}
            error={query.error}
            empty={noResults}
            emptyTitle="No drivers match these filters"
            emptyHint="Adjust the filters, or register the first driver for this site."
            onRetry={query.refetch}
            minHeight={280}
          >
            <DataTable
              rows={query.data?.content ?? []}
              columns={columns}
              getRowId={(row) => row.id}
              loading={query.loading}
              onRowClick={(row) => navigate(fleetPaths.driverDetail(row.id))}
              page={pagination.page}
              pageSize={pagination.pageSize}
              totalElements={query.data?.totalElements ?? 0}
              onPageChange={(page) => setPagination((current) => ({ ...current, page }))}
              onPageSizeChange={(pageSize) => setPagination({ page: 0, pageSize })}
              emptyMessage="No drivers match these filters."
            />
          </DataState>
        </div>
      </SectionCard>

      {/* Mounted only while open, so the dialog picks up the current site filter as its default
          and cannot reopen holding a half-typed profile from a previous attempt. */}
      {registerOpen && (
        <RegisterDriverDialog
          open
          defaultSiteCode={filters.siteCode || defaultSite}
          onClose={() => setRegisterOpen(false)}
          onSaved={() => {
            notifySuccess('Driver registered.');
            query.refetch();
          }}
        />
      )}
    </div>
  );
};

export default DriverRegisterPage;
