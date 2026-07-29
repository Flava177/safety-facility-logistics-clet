import { useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { FuelTransaction } from 'modules/fuel/api/dto';
import { FUEL_TRANSACTION_STATUSES, FuelTransactionStatus } from 'modules/fuel/api/enums';
import { fuelTransactionsApi } from 'modules/fuel/api/fuelApi';
import { CaptureTransactionDialog } from 'modules/fuel/dialogs/transactionDialogs';
import { DriverSelect, VehicleSelect } from 'modules/fuel/components/FleetReferenceSelect';
import { useClampPage, useServerPage } from 'modules/fuel/components/useServerPage';
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

/** `sourceSystem` is an exact match on the wire; these are the values this deployment writes. */
const SOURCE_FILTERS = [
  { value: 'MANUAL', label: 'Manual capture' },
  { value: 'CSV-IMPORT', label: 'CSV import' },
];

/**
 * The fuel transaction register.
 *
 * Every filter here goes to the service, and the table is server-paged with a real total. Source
 * and vendor used to be applied in the browser over a capped window — so "manual captures at this
 * site" really meant "manual captures among the first two hundred" — and are now query parameters.
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

  const filterKey = `${siteCode}|${status}|${vehicleId}|${driverId}|${from}|${to}|${source}|${vendor}`;
  const paging = useServerPage(filterKey);

  const query = useApiQuery(
    (signal) =>
      fuelTransactionsApi.search(
        {
          siteCode,
          status: status || undefined,
          vehicleId: vehicleId || undefined,
          driverId: driverId || undefined,
          sourceSystem: source || undefined,
          vendorReference: vendor.trim() || undefined,
          from: from ? new Date(from).toISOString() : undefined,
          to: to ? new Date(to).toISOString() : undefined,
          page: paging.page,
          size: paging.size,
        },
        signal,
      ),
    [filterKey, paging.page, paging.size],
  );

  useClampPage(paging.page, query.data?.totalPages, paging.setPage);

  /**
   * The service's own report, not the filtered table.
   *
   * `GET /reports/transactions.csv` takes a site and nothing else, so the download is described as
   * the site's report rather than "these results", which it is not.
   */
  const exportReport = async () => {
    setExporting(true);
    try {
      const fileName = await fuelTransactionsApi.downloadReport(siteCode);
      notifySuccess(
        `Downloaded ${fileName}.`,
        'The service exports the site’s most recent transactions, not the filtered view.',
      );
    } catch (error) {
      notifyError(error);
    } finally {
      setExporting(false);
    }
  };

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
          />
          <TextInput
            label="Vendor"
            value={vendor}
            onChange={setVendor}
            placeholder="Part of the vendor name"
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
              rows={query.data?.content ?? []}
              columns={columns}
              getRowId={(row) => row.id}
              loading={query.loading}
              onRowClick={(row) => navigate(fuelPaths.transactionDetail(row.id))}
              caption="Fuel transactions matching the current filters, with quantity, cost, odometer reading, source, receipt standing and status."
              emptyMessage="No transaction matches these filters."
              page={query.data?.page ?? paging.page}
              pageSize={query.data?.size ?? paging.size}
              totalElements={query.data?.totalElements ?? 0}
              onPageChange={paging.setPage}
              onPageSizeChange={paging.setSize}
            />
          </DataState>
        </SectionCard>
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
