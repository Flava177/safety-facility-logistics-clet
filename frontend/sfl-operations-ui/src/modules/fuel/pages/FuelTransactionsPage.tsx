import { useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { FuelTransaction } from 'modules/fuel/api/dto';
import { FUEL_TRANSACTION_STATUSES, FuelTransactionStatus } from 'modules/fuel/api/enums';
import { DEFAULT_WINDOW, fuelTransactionsApi } from 'modules/fuel/api/fuelApi';
import { CaptureTransactionDialog } from 'modules/fuel/dialogs/transactionDialogs';
import {
  DriverSelect,
  VehicleSelect,
} from 'modules/fuel/components/FleetReferenceSelect';
import WindowNotice from 'modules/fuel/components/WindowNotice';
import { useClientWindow } from 'modules/fuel/components/useClientWindow';
import { formatMoney, formatQuantity, shortId } from 'modules/fuel/components/fuelFormat';
import Button from 'shared/components/Button';
import DataState from 'shared/components/DataState';
import DataTable, { CellStack, Column } from 'shared/components/DataTable';
import FilterBar from 'shared/components/FilterBar';
import { useNotifier } from 'shared/components/Notifier';
import PageHeader from 'shared/components/PageHeader';
import SectionCard from 'shared/components/SectionCard';
import SiteSelect, { defaultSite } from 'shared/components/SiteSelect';
import StatusChip from 'shared/components/StatusChip';
import { DateTimeField } from 'shared/components/DateField';
import { EnumSelect, SelectInput, TextInput } from 'shared/components/fields';
import { formatDateTime, formatNumber } from 'shared/components/format';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { fuelPaths } from 'shared/layout/navigation';

/**
 * Manual capture, CSV import and provider ingest all write `sourceSystem`; `MANUAL` is the only one
 * the console produces, so "manual" versus "everything else" is the distinction an operator draws.
 */
const SOURCE_FILTERS = [
  { value: 'MANUAL', label: 'Manual capture' },
  { value: 'NOT_MANUAL', label: 'Imported or provider' },
];

/**
 * The fuel transaction register.
 *
 * Site, status, vehicle, driver and the date range all go to the service — they are the five filters
 * `GET /transactions` accepts. Source and vendor are filtered here over the returned window, and are
 * labelled as such, because the endpoint has no parameter for either.
 *
 * There is no pagination on the fuel side (gap 4), so the footer counts the window the service
 * returned and `WindowNotice` says plainly when that window came back full.
 */
const FuelTransactionsPage = () => {
  const navigate = useNavigate();
  const { notifySuccess, notifyError } = useNotifier();
  const [searchParams] = useSearchParams();

  const [siteCode, setSiteCode] = useState(defaultSite);
  // A dashboard card links here with a status already applied, so the register opens on what the
  // operator clicked rather than on everything.
  const [status, setStatus] = useState<FuelTransactionStatus | ''>(
    (searchParams.get('status') as FuelTransactionStatus | null) ?? '',
  );
  const [vehicleId, setVehicleId] = useState('');
  const [driverId, setDriverId] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [source, setSource] = useState('');
  const [vendor, setVendor] = useState('');
  const [capturing, setCapturing] = useState(false);
  const [exporting, setExporting] = useState(false);

  /**
   * The service's own report, not the filtered table.
   *
   * `GET /reports/transactions.csv` takes a site and nothing else, and caps at 500 rows — so the
   * download is deliberately described as the site's report rather than "these results", which it
   * is not.
   */
  const exportReport = async () => {
    setExporting(true);
    try {
      const fileName = await fuelTransactionsApi.downloadReport(siteCode);
      notifySuccess(
        `Downloaded ${fileName}.`,
        'The service exports the site’s most recent 500 transactions, not the filtered view.',
      );
    } catch (error) {
      notifyError(error);
    } finally {
      setExporting(false);
    }
  };

  const query = useApiQuery(
    (signal) =>
      fuelTransactionsApi.search(
        {
          siteCode,
          status: status || undefined,
          vehicleId: vehicleId || undefined,
          driverId: driverId || undefined,
          from: from ? new Date(from).toISOString() : undefined,
          to: to ? new Date(to).toISOString() : undefined,
        },
        signal,
      ),
    [siteCode, status, vehicleId, driverId, from, to],
  );

  const filtered = useMemo(() => {
    let rows = query.data ?? [];
    if (source === 'MANUAL') {
      rows = rows.filter((row) => row.sourceSystem.toUpperCase() === 'MANUAL');
    } else if (source === 'NOT_MANUAL') {
      rows = rows.filter((row) => row.sourceSystem.toUpperCase() !== 'MANUAL');
    }
    if (vendor.trim()) {
      const needle = vendor.trim().toLowerCase();
      rows = rows.filter((row) => row.vendorReference.toLowerCase().includes(needle));
    }
    return rows;
  }, [query.data, source, vendor]);

  const windowed = useClientWindow(
    filtered,
    `${siteCode}|${status}|${vehicleId}|${driverId}|${from}|${to}|${source}|${vendor}`,
    query.data?.length,
  );

  const columns = useMemo<Column<FuelTransaction>[]>(
    () => [
      {
        key: 'transaction',
        header: 'Transaction',
        width: 280,
        cell: (row) => (
          <CellStack
            primary={`${row.vendorReference}${row.stationReference ? ` · ${row.stationReference}` : ''}`}
            secondary={`${formatDateTime(row.occurredAt)} · ${row.fuelProduct}`}
          />
        ),
      },
      {
        key: 'quantity',
        header: 'Quantity',
        width: 120,
        align: 'right',
        cell: (row) => formatQuantity(row.quantity, row.quantityUnit),
      },
      {
        key: 'cost',
        header: 'Total cost',
        width: 140,
        align: 'right',
        cell: (row) => formatMoney(row.totalCost, row.currency),
      },
      {
        key: 'odometer',
        header: 'Odometer',
        width: 120,
        align: 'right',
        hideBelowLg: true,
        cell: (row) => `${formatNumber(row.odometerReading)} km`,
      },
      {
        key: 'source',
        header: 'Source',
        width: 150,
        hideBelowLg: true,
        cell: (row) => (
          <CellStack
            primary={row.sourceSystem}
            secondary={
              row.providerTransactionId ? `ref ${shortId(row.providerTransactionId)}` : 'no reference'
            }
          />
        ),
      },
      {
        key: 'receipt',
        header: 'Receipt',
        width: 100,
        align: 'center',
        hideBelowLg: true,
        cell: (row) =>
          row.receiptEvidenceId ? (
            <StatusChip value="ACTIVE" label="Held" tone="ready" />
          ) : (
            <StatusChip value="MISSING" label="None" tone="caution" />
          ),
      },
      {
        key: 'status',
        header: 'Status',
        width: 130,
        align: 'right',
        cell: (row) => <StatusChip value={row.status} />,
      },
    ],
    [],
  );

  const filtersApplied = Boolean(
    status || vehicleId || driverId || from || to || source || vendor,
  );

  const resetFilters = () => {
    setStatus('');
    setVehicleId('');
    setDriverId('');
    setFrom('');
    setTo('');
    setSource('');
    setVendor('');
  };

  return (
    <div>
      <PageHeader
        title="Fuel transactions"
        subtitle="Every captured, imported and provider-ingested transaction at this site."
        crumbs={[{ label: 'Fuel', to: fuelPaths.dashboard }, { label: 'Transactions' }]}
        actions={
          <>
            <Button
              variant="outline"
              startIcon="download"
              loading={exporting}
              onClick={exportReport}
            >
              Export CSV
            </Button>
            <Button variant="primary" startIcon="plus" onClick={() => setCapturing(true)}>
              Capture transaction
            </Button>
          </>
        }
      />

      <SectionCard flush>
        <FilterBar onReset={resetFilters} resetDisabled={!filtersApplied}>
          <SiteSelect value={siteCode} onChange={setSiteCode} required />
          <EnumSelect
            label="Status"
            value={status}
            options={FUEL_TRANSACTION_STATUSES}
            onChange={(value) => setStatus(value)}
            allowEmpty
          />
          <VehicleSelect
            siteCode={siteCode}
            value={vehicleId}
            onChange={setVehicleId}
            allowEmpty
            emptyLabel="Any vehicle"
            helperText=" "
          />
          <DriverSelect
            siteCode={siteCode}
            value={driverId}
            onChange={setDriverId}
            allowEmpty
            emptyLabel="Any driver"
            helperText=" "
          />
          <DateTimeField label="From" value={from} onChange={setFrom} />
          <DateTimeField label="To" value={to} onChange={setTo} />
          <SelectInput
            label="Source"
            value={source}
            onChange={setSource}
            options={SOURCE_FILTERS}
            allowEmpty
            emptyLabel="Any source"
            helperText="Filters the loaded records."
          />
          <TextInput
            label="Vendor"
            value={vendor}
            onChange={setVendor}
            placeholder="Part of the vendor name"
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
              onRowClick={(row) => navigate(fuelPaths.transactionDetail(row.id))}
              caption="Fuel transactions matching the current filters, with quantity, cost, odometer reading, source, receipt standing and status."
              emptyMessage="No transaction matches these filters."
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
          noun="transactions"
        />
      </div>

      {capturing && (
        <CaptureTransactionDialog
          open
          defaultSiteCode={siteCode}
          onClose={() => setCapturing(false)}
          onSaved={(transaction) => {
            notifySuccess(
              `Transaction captured against ${transaction.vendorReference}.`,
              'It is in the received state until reconciliation runs.',
            );
            query.refetch();
          }}
        />
      )}
    </div>
  );
};

export default FuelTransactionsPage;
