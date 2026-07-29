import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router';
import { ImportResult, ImportRowResult } from 'modules/fuel/api/dto';
import { CSV_OPTIONAL_HEADERS, CSV_REQUIRED_HEADERS } from 'modules/fuel/api/enums';
import { CsvImportDialog } from 'modules/fuel/dialogs/importDialogs';
import { shortId } from 'modules/fuel/components/fuelFormat';
import Alert from 'shared/components/Alert';
import Button from 'shared/components/Button';
import DataState from 'shared/components/DataState';
import DataTable, { CellStack, Column } from 'shared/components/DataTable';
import Icon from 'shared/components/Icon';
import { useNotifier } from 'shared/components/Notifier';
import PageHeader from 'shared/components/PageHeader';
import SectionCard from 'shared/components/SectionCard';
import SiteSelect, { defaultSite } from 'shared/components/SiteSelect';
import StatCard from 'shared/components/StatCard';
import StatusChip from 'shared/components/StatusChip';
import { formatNumber } from 'shared/components/format';
import { fuelPaths } from 'shared/layout/navigation';

/** A batch plus the two things the response does not carry: what was uploaded, and when. */
interface CompletedImport {
  result: ImportResult;
  fileName: string;
  siteCode: string;
  at: string;
}

const ROW_FILTERS = [
  { value: 'ALL', label: 'Every row' },
  { value: 'REJECTED', label: 'Rejected only' },
  { value: 'ACCEPTED', label: 'Accepted only' },
];

/**
 * CSV import.
 *
 * Two things about this screen are unusual, and both come from the service rather than a choice
 * made here. There is **no import history** — `fuel_import_batches` and `fuel_import_rows` are
 * written on every upload but no endpoint reads them (gap 2) — so this page shows the batches
 * uploaded in *this browsing session* and says so, rather than presenting an empty history as
 * though nothing had ever been imported. And a batch is **never rejected as a whole for a bad
 * row**: each row goes through the same capture command as a manual entry and is accepted or
 * rejected on its own, so the row table is the real result, not the totals above it.
 */
const FuelImportsPage = () => {
  const navigate = useNavigate();
  const { notifySuccess } = useNotifier();

  const [siteCode, setSiteCode] = useState(defaultSite);
  const [importing, setImporting] = useState(false);
  const [batches, setBatches] = useState<CompletedImport[]>([]);
  const [selectedBatchId, setSelectedBatchId] = useState<string | null>(null);
  const [rowFilter, setRowFilter] = useState('ALL');

  const selected = useMemo(
    () => batches.find((batch) => batch.result.batchId === selectedBatchId) ?? batches[0],
    [batches, selectedBatchId],
  );

  const rows = useMemo(() => {
    const all = selected?.result.rows ?? [];
    if (rowFilter === 'REJECTED') {
      return all.filter((row) => row.status !== 'ACCEPTED');
    }
    if (rowFilter === 'ACCEPTED') {
      return all.filter((row) => row.status === 'ACCEPTED');
    }
    return all;
  }, [selected, rowFilter]);

  const rowColumns = useMemo<Column<ImportRowResult>[]>(
    () => [
      {
        key: 'row',
        header: 'Row',
        width: 90,
        cell: (row) => <span className="font-semibold text-gray-900">{row.rowNumber}</span>,
      },
      {
        key: 'status',
        header: 'Outcome',
        width: 120,
        cell: (row) => <StatusChip value={row.status} />,
      },
      {
        key: 'detail',
        header: 'Detail',
        width: 420,
        cell: (row) =>
          row.status === 'ACCEPTED' ? (
            <CellStack
              primary="Transaction created"
              secondary={row.transactionId ? shortId(row.transactionId) : undefined}
            />
          ) : (
            <CellStack
              primary={row.errorMessage ?? 'The service gave no message.'}
              secondary={row.errorCode ?? undefined}
            />
          ),
      },
      {
        key: 'link',
        header: '',
        width: 110,
        align: 'right',
        cell: (row) =>
          row.transactionId ? (
            <Button
              size="sm"
              variant="ghost"
              endIcon="chevron-right"
              onClick={() => navigate(fuelPaths.transactionDetail(row.transactionId as string))}
            >
              Open
            </Button>
          ) : null,
      },
    ],
    [navigate],
  );

  return (
    <div>
      <PageHeader
        title="CSV imports"
        subtitle="Bulk capture, with an accepted or rejected outcome for every row."
        crumbs={[{ label: 'Fuel', to: fuelPaths.dashboard }, { label: 'CSV imports' }]}
        actions={
          <Button variant="primary" startIcon="upload" onClick={() => setImporting(true)}>
            Import a CSV
          </Button>
        }
      />

      <div className="space-y-5">
        <Alert variant="info" title="Imports are not retained for viewing">
          The service records each batch and its rows, but exposes no endpoint to read them back. The
          batches below are the ones uploaded from this screen since it was opened; leaving the page
          loses them. Deal with any rejected rows before you navigate away.
        </Alert>

        <SectionCard title="Where to import" flush>
          <div className="grid gap-4 border-b border-gray-200 px-5 pt-5 pb-6 sm:grid-cols-2 lg:grid-cols-3">
            <SiteSelect
              value={siteCode}
              onChange={setSiteCode}
              required
              helperText="Every row in the batch is captured against this site."
            />
          </div>
        </SectionCard>

        <div className="grid gap-5 xl:grid-cols-2">
          <SectionCard
            title="Required column headers"
            subtitle="The first row of the file must name the columns"
          >
            <p className="text-theme-sm text-gray-700">
              These ten must be present and populated on every row:
            </p>
            <ul className="mt-2 flex flex-wrap gap-1.5">
              {CSV_REQUIRED_HEADERS.map((header) => (
                <li
                  key={header}
                  className="rounded border border-gray-200 bg-gray-50 px-2 py-0.5 font-mono text-theme-xs text-gray-900"
                >
                  {header}
                </li>
              ))}
            </ul>
            <p className="mt-4 text-theme-sm text-gray-700">These are optional:</p>
            <ul className="mt-2 flex flex-wrap gap-1.5">
              {CSV_OPTIONAL_HEADERS.map((header) => (
                <li
                  key={header}
                  className="rounded border border-gray-200 px-2 py-0.5 font-mono text-theme-xs text-gray-700"
                >
                  {header}
                </li>
              ))}
            </ul>
            <p className="mt-4 text-theme-xs text-gray-600">
              Column names are case sensitive and every row must have the same number of columns as
              the header. `occurredAt` is an ISO-8601 instant; `vehicleId`, `driverId`, `tripId` and
              `receiptEvidenceId` are UUIDs. Omitting `totalCost` lets the service compute it.
            </p>
          </SectionCard>

          <SectionCard title="How a batch is judged" subtitle="Row by row, never as a whole">
            <ul className="space-y-3 text-theme-sm text-gray-700">
              <li className="flex items-start gap-2.5">
                <Icon name="check-circle" size={15} className="mt-0.5 shrink-0 text-success-700" />
                <span>
                  Each row is captured through the same command as a manual entry, so it fails for
                  the same reasons: an unknown vehicle or driver, a total that does not match
                  quantity × unit price, an unparseable timestamp.
                </span>
              </li>
              <li className="flex items-start gap-2.5">
                <Icon name="info" size={15} className="mt-0.5 shrink-0 text-teal-700" />
                <span>
                  A rejected row does not stop the batch. Accepted rows are committed and appear in
                  the transaction register immediately.
                </span>
              </li>
              <li className="flex items-start gap-2.5">
                <Icon name="lock" size={15} className="mt-0.5 shrink-0 text-gray-600" />
                <span>
                  The same file content cannot be imported twice for one site and source system. The
                  batch is keyed on its own hash, and the second attempt fails on a database
                  constraint the service does not map — so it returns an unhandled server error
                  rather than a message naming the cause. No transaction is duplicated when this
                  happens.
                </span>
              </li>
              <li className="flex items-start gap-2.5">
                <Icon name="scale" size={15} className="mt-0.5 shrink-0 text-gray-600" />
                <span>
                  Imported rows arrive in the received state. They are not reconciled until a run
                  covers them.
                </span>
              </li>
            </ul>
          </SectionCard>
        </div>

        {batches.length > 1 && (
          <SectionCard title="Batches imported in this session" flush>
            <ul className="divide-y divide-gray-100">
              {batches.map((batch) => (
                <li key={batch.result.batchId}>
                  <button
                    type="button"
                    onClick={() => setSelectedBatchId(batch.result.batchId)}
                    className={`flex w-full flex-wrap items-center justify-between gap-3 px-5 py-3 text-left transition-colors hover:bg-gray-50 ${
                      selected?.result.batchId === batch.result.batchId ? 'bg-gold-25' : ''
                    }`}
                  >
                    <span className="min-w-0">
                      <span className="block truncate text-theme-sm font-semibold text-gray-900">
                        {batch.fileName}
                      </span>
                      <span className="block text-theme-xs text-gray-600">
                        {batch.siteCode} · {batch.at} · batch {shortId(batch.result.batchId)}
                      </span>
                    </span>
                    <span className="flex shrink-0 items-center gap-2">
                      <StatusChip
                        value="ACCEPTED"
                        label={`${batch.result.acceptedRows} accepted`}
                        tone="ready"
                      />
                      {batch.result.rejectedRows > 0 && (
                        <StatusChip
                          value="REJECTED"
                          label={`${batch.result.rejectedRows} rejected`}
                          tone="blocked"
                        />
                      )}
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          </SectionCard>
        )}

        {selected ? (
          <>
            <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
              <StatCard
                label="Rows in the file"
                value={formatNumber(selected.result.totalRows)}
                icon="document"
                caption={selected.fileName}
              />
              <StatCard
                label="Accepted"
                value={formatNumber(selected.result.acceptedRows)}
                icon="check-circle"
                tone={selected.result.acceptedRows > 0 ? 'good' : 'neutral'}
                caption="Transactions created"
              />
              <StatCard
                label="Rejected"
                value={formatNumber(selected.result.rejectedRows)}
                icon="alert-circle"
                tone={selected.result.rejectedRows > 0 ? 'critical' : 'neutral'}
                caption="Nothing was created for these"
              />
              <StatCard
                label="Batch"
                value={shortId(selected.result.batchId)}
                icon="inbox"
                caption={`Imported into ${selected.siteCode}`}
              />
            </div>

            <SectionCard
              title="Row outcomes"
              subtitle={`${selected.result.totalRows} rows · ${selected.fileName}`}
              actions={
                <div className="flex items-center gap-1.5">
                  {ROW_FILTERS.map((filter) => (
                    <Button
                      key={filter.value}
                      size="sm"
                      variant={rowFilter === filter.value ? 'primary' : 'outline'}
                      onClick={() => setRowFilter(filter.value)}
                    >
                      {filter.label}
                    </Button>
                  ))}
                </div>
              }
              flush
            >
              <DataState
                loading={false}
                empty={rows.length === 0}
                emptyTitle="No rows match this filter"
                emptyHint={
                  rowFilter === 'REJECTED'
                    ? 'Every row in this batch was accepted.'
                    : 'Nothing to show.'
                }
                minHeight={200}
              >
                <DataTable
                  rows={rows}
                  columns={rowColumns}
                  getRowId={(row) => String(row.rowNumber)}
                  caption="The outcome of every row in this import batch, with the validation error the service recorded for each rejected row."
                  dense
                />
              </DataState>
            </SectionCard>
          </>
        ) : (
          <SectionCard title="No import yet">
            <DataState
              loading={false}
              empty
              emptyTitle="Nothing imported in this session"
              emptyHint="Upload a CSV to see its row-by-row outcome here."
              minHeight={200}
            >
              <span />
            </DataState>
          </SectionCard>
        )}
      </div>

      {importing && (
        <CsvImportDialog
          open
          defaultSiteCode={siteCode}
          onClose={() => setImporting(false)}
          onImported={(result, fileName) => {
            const batch: CompletedImport = {
              result,
              fileName,
              siteCode,
              at: new Date().toLocaleString(),
            };
            setBatches((current) => [batch, ...current]);
            setSelectedBatchId(result.batchId);
            setRowFilter(result.rejectedRows > 0 ? 'REJECTED' : 'ALL');
            notifySuccess(
              `Imported ${result.acceptedRows} of ${result.totalRows} rows.`,
              result.rejectedRows > 0
                ? `${result.rejectedRows} rows were rejected — their errors are listed below.`
                : 'Every row was accepted.',
            );
          }}
        />
      )}
    </div>
  );
};

export default FuelImportsPage;
