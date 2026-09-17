import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router';
import { TripResponse, TripStatusValue } from 'modules/fleet/api/dto';
import { OPERATING_MODES, OperatingMode, TRIP_STATUSES, humanise } from 'modules/fleet/api/enums';
import { tripsApi } from 'modules/fleet/api/fleetApi';
import { CreateTripDialog } from 'modules/fleet/dialogs/tripDialogs';
import { defaultPageSize } from 'shared/api/config';
import Alert from 'shared/components/Alert';
import Button from 'shared/components/Button';
import DataState from 'shared/components/DataState';
import DataTable, { CellStack, Column } from 'shared/components/DataTable';
import { DateTimeField } from 'shared/components/DateField';
import FacetFilter from 'shared/components/FacetFilter';
import FilterBar from 'shared/components/FilterBar';
import { useNotifier } from 'shared/components/Notifier';
import PageHeader from 'shared/components/PageHeader';
import SectionCard from 'shared/components/SectionCard';
import SiteSelect, { defaultSite } from 'shared/components/SiteSelect';
import StatusChip from 'shared/components/StatusChip';
import { formatDateTime, fromLocalInputValue } from 'shared/components/format';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { fleetPaths } from 'shared/layout/navigation';
import { canManageTrips } from '../api/access';

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
        width: 150,
        cell: (row) => <CellStack primary={row.tripNumber} secondary={row.purpose} />,
      },
      {
        /*
          The route gets a column of its own.

          It was the secondary line under the trip number, sharing 200px with it, so on any realistic
          place name - "Accra HQ Motor Pool", "Regional Examination Centre" - it truncated to the
          point of being unreadable, and it was the first thing anyone scanning this register looks
          for. Origin over destination rather than side by side: two short lines survive a narrow
          column where one long one does not.
        */
        key: 'route',
        header: 'Route',
        width: 240,
        cell: (row) => (
          <CellStack primary={row.origin || 'Not set'} secondary={`to ${row.destination || 'not set'}`} />
        ),
      },
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
        /*
          A separate column from Status, not a variant of it. A dispatcher's question the morning a
          vehicle is due out is "which of these has the driver not answered for", and folding the
          answer into the status chip would make that question unanswerable at a glance - an assigned
          trip and a confirmed one are both ASSIGNED.
        */
        key: 'acknowledgementState',
        header: 'Driver',
        width: 150,
        cell: (row) =>
          row.status === 'ASSIGNED' ? (
            <StatusChip
              value={row.acknowledgementState}
              label={
                row.acknowledgementState === 'CONFIRMED'
                  ? 'Confirmed'
                  : row.acknowledgementState === 'DEFERRED'
                    ? 'Deferred'
                    : 'Awaiting reply'
              }
              tone={
                row.acknowledgementState === 'CONFIRMED'
                  ? 'ready'
                  : row.acknowledgementState === 'DEFERRED'
                    ? 'blocked'
                    : 'caution'
              }
            />
          ) : (
            <span className="text-gray-400">-</span>
          ),
      },
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
            {/* A driver reads the register and plans nothing. Planning is FLEET_TRIP_MANAGE. */}
            {canManageTrips() && (
              <Button variant="accent" startIcon="plus" onClick={() => setCreateOpen(true)}>
                Plan a trip
              </Button>
            )}
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
          <FacetFilter
            label="Status"
            selected={filters.status ? [filters.status] : []}
            onChange={(next) => setFilter('status', pickSingle(filters.status, next))}
            options={TRIP_STATUSES.map((value) => ({ value, label: humanise(value) }))}
          />
          <FacetFilter
            label="Operating mode"
            selected={filters.operatingMode ? [filters.operatingMode] : []}
            onChange={(next) => setFilter('operatingMode', pickSingle(filters.operatingMode, next))}
            options={OPERATING_MODES.map((value) => ({ value, label: humanise(value) }))}
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

        {/*
          Why the list is shorter than the site's, when it is. The server sends this on a narrowed
          list - a driver sees their own trips - and sends nothing on an unnarrowed one. Showing it is
          what stops a driver reading their own list as "the queue is nearly empty today", and what
          tells an unbound driver why theirs is empty rather than leaving them at a blank screen.
        */}
        {query.data?.scopeNotice && (
          <Alert variant="info" className="mb-3">
            {query.data.scopeNotice}
          </Alert>
        )}

        <DataState
          loading={query.initialising}
          error={query.error}
          empty={(query.data?.content.length ?? 0) === 0 && !query.loading}
          emptyTitle="No trips match these filters"
          emptyHint={
            query.data?.scopeNotice ?? 'Plan a trip to get a movement into the queue.'
          }
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
