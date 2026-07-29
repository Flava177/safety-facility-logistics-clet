import { useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { DriverLogbook } from 'modules/fuel/api/dto';
import {
  LOGBOOK_STATUSES,
  LOGBOOK_USE_CLASSIFICATIONS,
  LogbookStatus,
  LogbookUseClassification,
} from 'modules/fuel/api/enums';
import { DEFAULT_WINDOW, driverLogbooksApi } from 'modules/fuel/api/fuelApi';
import { CreateLogbookDialog } from 'modules/fuel/dialogs/logbookDialogs';
import WindowNotice from 'modules/fuel/components/WindowNotice';
import { useClientWindow } from 'modules/fuel/components/useClientWindow';
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
import { formatDate, formatDateTime, formatNumber } from 'shared/components/format';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { fuelPaths } from 'shared/layout/navigation';

/**
 * The driver logbook register.
 *
 * Site and status are the only two filters `GET /logbooks` accepts; use classification and the
 * route search are applied here over the returned window and are labelled as such.
 *
 * One service behaviour shapes what an operator sees and is worth knowing: a `FLEET_DRIVER`-only
 * actor gets **their own logbooks only** — `FuelApplicationService.logbooks` passes `ownOnly` from
 * `isDriverOnly(actor)`. A manager or logistics officer sees the site. The console does not filter
 * this itself; it is simply what came back.
 */
const DriverLogbooksPage = () => {
  const navigate = useNavigate();
  const { notifySuccess } = useNotifier();
  const [searchParams] = useSearchParams();

  const [siteCode, setSiteCode] = useState(defaultSite);
  const [status, setStatus] = useState<LogbookStatus | ''>(
    (searchParams.get('status') as LogbookStatus | null) ?? '',
  );
  const [useClass, setUseClass] = useState<LogbookUseClassification | ''>('');
  const [search, setSearch] = useState('');
  const [creating, setCreating] = useState(false);

  const query = useApiQuery(
    (signal) => driverLogbooksApi.search({ siteCode, status: status || undefined }, signal),
    [siteCode, status],
  );

  const filtered = useMemo(() => {
    let rows = query.data ?? [];
    if (useClass) {
      rows = rows.filter((row) => row.useClassification === useClass);
    }
    if (search.trim()) {
      const needle = search.trim().toLowerCase();
      rows = rows.filter(
        (row) =>
          row.logbookNumber.toLowerCase().includes(needle) ||
          row.origin.toLowerCase().includes(needle) ||
          row.destination.toLowerCase().includes(needle) ||
          row.purpose.toLowerCase().includes(needle),
      );
    }
    return rows;
  }, [query.data, useClass, search]);

  const windowed = useClientWindow(
    filtered,
    `${siteCode}|${status}|${useClass}|${search}`,
    query.data?.length,
  );

  const columns = useMemo<Column<DriverLogbook>[]>(
    () => [
      {
        key: 'logbook',
        header: 'Logbook',
        width: 280,
        cell: (row) => (
          <CellStack
            primary={`${row.logbookNumber} · ${row.origin} → ${row.destination}`}
            secondary={row.purpose}
          />
        ),
      },
      {
        key: 'journey',
        header: 'Journey date',
        width: 130,
        cell: (row) => formatDate(row.journeyDate),
      },
      {
        key: 'distance',
        header: 'Distance',
        width: 110,
        align: 'right',
        cell: (row) =>
          row.endOdometer === null || row.endOdometer === undefined
            ? '—'
            : `${formatNumber(row.endOdometer - row.startOdometer)} km`,
      },
      {
        key: 'use',
        header: 'Use',
        width: 120,
        hideBelowLg: true,
        cell: (row) => <StatusChip value={row.useClassification} tone="neutral" />,
      },
      {
        key: 'submitted',
        header: 'Submitted',
        width: 160,
        hideBelowLg: true,
        cell: (row) => formatDateTime(row.submittedAt),
      },
      {
        key: 'status',
        header: 'Status',
        width: 140,
        align: 'right',
        cell: (row) => <StatusChip value={row.status} />,
      },
    ],
    [],
  );

  const filtersApplied = Boolean(status || useClass || search);

  return (
    <div>
      <PageHeader
        title="Driver logbooks"
        subtitle="Journey records from draft through review to approval."
        crumbs={[{ label: 'Fuel', to: fuelPaths.dashboard }, { label: 'Driver logbooks' }]}
        actions={
          <Button variant="primary" startIcon="plus" onClick={() => setCreating(true)}>
            Create logbook
          </Button>
        }
      />

      <SectionCard flush>
        <FilterBar
          onReset={() => {
            setStatus('');
            setUseClass('');
            setSearch('');
          }}
          resetDisabled={!filtersApplied}
        >
          <SiteSelect value={siteCode} onChange={setSiteCode} required />
          <EnumSelect
            label="Status"
            value={status}
            options={LOGBOOK_STATUSES}
            onChange={(value) => setStatus(value)}
            allowEmpty
          />
          <EnumSelect
            label="Use classification"
            value={useClass}
            options={LOGBOOK_USE_CLASSIFICATIONS}
            onChange={(value) => setUseClass(value)}
            allowEmpty
            helperText="Filters the loaded records."
          />
          <TextInput
            label="Search"
            value={search}
            onChange={setSearch}
            placeholder="Number, route or purpose"
            helperText="Filters the loaded records."
          />
        </FilterBar>
      </SectionCard>

      <div className="mt-5">
        <SectionCard flush>
          <DataState
            loading={query.initialising}
            error={query.error}
            onRetry={query.refetch}
            minHeight={300}
          >
            <DataTable
              rows={windowed.rows}
              columns={columns}
              getRowId={(row) => row.id}
              loading={query.loading}
              onRowClick={(row) => navigate(fuelPaths.logbookDetail(row.id))}
              caption="Driver logbooks matching the current filters, with journey date, distance, use classification, submission time and status."
              emptyMessage="No logbook matches these filters."
              page={windowed.page}
              pageSize={windowed.pageSize}
              totalElements={windowed.total}
              onPageChange={windowed.setPage}
              onPageSizeChange={windowed.setPageSize}
            />
          </DataState>
        </SectionCard>

        <WindowNotice
          truncated={windowed.truncated}
          total={query.data?.length ?? 0}
          requestedSize={DEFAULT_WINDOW}
          noun="logbooks"
        />
      </div>

      {creating && (
        <CreateLogbookDialog
          open
          defaultSiteCode={siteCode}
          onClose={() => setCreating(false)}
          onSaved={(logbook) => {
            notifySuccess(
              `${logbook.logbookNumber} created as a draft.`,
              'Complete the journey and accept the declaration before submitting it.',
            );
            query.refetch();
            navigate(fuelPaths.logbookDetail(logbook.id));
          }}
        />
      )}
    </div>
  );
};

export default DriverLogbooksPage;
