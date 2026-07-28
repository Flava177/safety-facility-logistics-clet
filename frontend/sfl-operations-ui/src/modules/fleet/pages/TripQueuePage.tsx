import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router';
import { TripResponse, TripStatusValue } from 'modules/fleet/api/dto';
import { OPERATING_MODES, OperatingMode, TRIP_STATUSES, humanise } from 'modules/fleet/api/enums';
import { tripsApi } from 'modules/fleet/api/fleetApi';
import { CreateTripDialog } from 'modules/fleet/dialogs/tripDialogs';
import { defaultPageSize } from 'shared/api/config';
import Button from 'shared/components/Button';
import DataState from 'shared/components/DataState';
import DataTable, { CellStack, Column } from 'shared/components/DataTable';
import { DateTimeField } from 'shared/components/DateField';
import FilterBar from 'shared/components/FilterBar';
import { useNotifier } from 'shared/components/Notifier';
import PageHeader from 'shared/components/PageHeader';
import SectionCard from 'shared/components/SectionCard';
import SiteSelect, { defaultSite } from 'shared/components/SiteSelect';
import StatusChip from 'shared/components/StatusChip';
import { EnumSelect } from 'shared/components/fields';
import { formatDateTime, fromLocalInputValue } from 'shared/components/format';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { fleetPaths } from 'shared/layout/navigation';

interface Filters {
  siteCode: string;
  status: TripStatusValue | '';
  operatingMode: OperatingMode | '';
  from: string;
  to: string;
}

const emptyFilters: Filters = {
  siteCode: defaultSite,
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
  const [pagination, setPagination] = useState({ page: 0, pageSize: defaultPageSize });
  const [createOpen, setCreateOpen] = useState(false);

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

  const columns = useMemo<Column<TripResponse>[]>(
    () => [
      {
        key: 'tripNumber',
        header: 'Trip',
        width: 200,
        cell: (row) => (
          <CellStack primary={row.tripNumber} secondary={`${row.origin} → ${row.destination}`} />
        ),
      },
      { key: 'purpose', header: 'Purpose', width: 200, cell: (row) => row.purpose },
      {
        key: 'plannedStart',
        header: 'Planned window',
        width: 210,
        cell: (row) => (
          <CellStack
            primary={formatDateTime(row.plannedStart)}
            secondary={`to ${formatDateTime(row.plannedEnd)}`}
          />
        ),
      },
      {
        key: 'operatingMode',
        header: 'Mode',
        width: 130,
        cell: (row) => humanise(row.operatingMode),
      },
      { key: 'status', header: 'Status', width: 140, cell: (row) => <StatusChip value={row.status} /> },
      {
        key: 'vehicleId',
        header: 'Assignment',
        width: 140,
        cell: (row) =>
          row.vehicleId && row.driverId ? (
            <StatusChip value="ASSIGNED" label="Vehicle & driver" tone="active" />
          ) : (
            <StatusChip value="PLANNED" label="Unassigned" tone="caution" />
          ),
      },
      { key: 'siteCode', header: 'Site', width: 100, cell: (row) => row.siteCode },
    ],
    [],
  );

  const filtersActive = JSON.stringify(filters) !== JSON.stringify(emptyFilters);

  return (
    <div>
      <PageHeader
        title="Trips & assignments"
        subtitle="Plan a movement, assign a vehicle and driver, then start and close it against evidence."
        crumbs={[{ label: 'Fleet', to: fleetPaths.dashboard }, { label: 'Trips' }]}
        actions={
          <>
            <Button variant="outline" startIcon="refresh" onClick={query.refetch}>
              Refresh
            </Button>
            <Button variant="accent" startIcon="plus" onClick={() => setCreateOpen(true)}>
              Plan a trip
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
          <DateTimeField
            label="From"
            value={filters.from}
            onChange={(value) => setFilter('from', value)}
          />
          <DateTimeField
            label="To"
            value={filters.to}
            onChange={(value) => setFilter('to', value)}
          />
        </FilterBar>

        <DataState
          loading={query.initialising}
          error={query.error}
          empty={(query.data?.content.length ?? 0) === 0 && !query.loading}
          emptyTitle="No trips match these filters"
          emptyHint="Plan a trip to get a movement into the queue."
          onRetry={query.refetch}
          minHeight={280}
        >
          <DataTable
            rows={query.data?.content ?? []}
            columns={columns}
            getRowId={(row) => row.id}
            loading={query.loading}
            onRowClick={(row) => navigate(fleetPaths.tripDetail(row.id))}
            page={pagination.page}
            pageSize={pagination.pageSize}
            totalElements={query.data?.totalElements ?? 0}
            onPageChange={(page) => setPagination((current) => ({ ...current, page }))}
            onPageSizeChange={(pageSize) => setPagination({ page: 0, pageSize })}
            emptyMessage="No trips match these filters."
          />
        </DataState>
      </SectionCard>

      {/* Mounted only while open, so the dialog picks up the current site filter as its default
          and cannot reopen holding a half-typed trip from a previous attempt. */}
      {createOpen && (
        <CreateTripDialog
          open
          defaultSiteCode={filters.siteCode || defaultSite}
          onClose={() => setCreateOpen(false)}
          onSaved={() => {
            notifySuccess('Trip created.');
            query.refetch();
          }}
        />
      )}
    </div>
  );
};

export default TripQueuePage;
