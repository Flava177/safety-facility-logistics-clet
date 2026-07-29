import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router';
import { ScanImportBatch, ScanImportRow } from 'modules/dispatch/api/dto';
import { SCAN_CSV_HEADERS } from 'modules/dispatch/api/enums';
import { scanImportsApi } from 'modules/dispatch/api/dispatchApi';
import { ScanImportDialog } from 'modules/dispatch/dialogs/exceptionDialogs';
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
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { dispatchPaths } from 'shared/layout/navigation';

const ROW_FILTERS = [
  { value: 'ALL', label: 'Every row' },
  { value: 'PROBLEM', label: 'Mismatched and unregistered' },
  { value: 'MATCHED', label: 'Matched only' },
];

/**
 * Scanner batch import.
 *
 * Unlike the fuel CSV import, a batch here **is** readable afterwards — `GET /scans/imports/{id}`
 * and its `/rows` both exist. What does not exist is a way to *list* batches for a site, so this
 * screen holds the batches uploaded in this browsing session and says so. A batch identifier can be
 * pasted in to reopen one from an earlier session, which is the workaround the missing list forces.
 *
 * Recorded as gap 3.
 */
const ScanImportsPage = () => {
  const navigate = useNavigate();
  const { notifySuccess, notifyError } = useNotifier();

  const [siteCode, setSiteCode] = useState(defaultSite);
  const [importing, setImporting] = useState(false);
  const [sessionBatches, setSessionBatches] = useState<ScanImportBatch[]>([]);
  const [activeBatchId, setActiveBatchId] = useState<string | null>(null);
  const [lookupId, setLookupId] = useState('');
  const [rowFilter, setRowFilter] = useState('ALL');

  const batch = useApiQuery(
    (signal) =>
      activeBatchId ? scanImportsApi.batch(activeBatchId, signal) : Promise.resolve(undefined),
    [activeBatchId],
  );

  const rows = useApiQuery(
    (signal) =>
      activeBatchId ? scanImportsApi.rows(activeBatchId, signal) : Promise.resolve(undefined),
    [activeBatchId],
  );

  const filteredRows = useMemo(() => {
    const all = rows.data ?? [];
    if (rowFilter === 'PROBLEM') {
      return all.filter((row) => row.outcome !== 'MATCHED');
    }
    if (rowFilter === 'MATCHED') {
      return all.filter((row) => row.outcome === 'MATCHED');
    }
    return all;
  }, [rows.data, rowFilter]);

  const rowColumns = useMemo<Column<ScanImportRow>[]>(
    () => [
      {
        key: 'reference',
        header: 'Row',
        width: 160,
        cell: (row) => <span className="font-semibold text-gray-900">{row.rowReference}</span>,
      },
      {
        key: 'code',
        header: 'Scanned code',
        width: 220,
        cell: (row) => <span className="font-mono text-theme-xs">{row.scannedCode}</span>,
      },
      {
        key: 'outcome',
        header: 'Outcome',
        width: 150,
        cell: (row) => <StatusChip value={row.outcome} />,
      },
      {
        key: 'detail',
        header: 'Detail',
        width: 320,
        cell: (row) =>
          row.courierItemId ? (
            <CellStack primary={row.message ?? 'Matched to an item'} secondary={shortId(row.courierItemId)} />
          ) : (
            (row.message ?? <span className="text-gray-500">No message recorded</span>)
          ),
      },
      {
        key: 'open',
        header: '',
        width: 100,
        align: 'right',
        cell: (row) =>
          row.courierItemId ? (
            <Button
              size="sm"
              variant="ghost"
              endIcon="chevron-right"
              onClick={() => navigate(dispatchPaths.itemDetail(row.courierItemId as string))}
            >
              Open
            </Button>
          ) : null,
      },
    ],
    [navigate],
  );

  const openLookup = () => {
    const id = lookupId.trim();
    if (!id) {
      return;
    }
    setActiveBatchId(id);
    setRowFilter('ALL');
  };

  const selected = batch.data;

  return (
    <div>
      <PageHeader
        title="Scan imports"
        subtitle="Scanner batches checked against the manifest, row by row."
        crumbs={[{ label: 'Dispatch', to: dispatchPaths.dashboard }, { label: 'Scan imports' }]}
        actions={
          <Button variant="primary" startIcon="upload" onClick={() => setImporting(true)}>
            Import a batch
          </Button>
        }
      />

      <div className="space-y-5">
        <Alert variant="info" title="Batches are readable, but there is no list of them">
          The service can return any batch and its rows by identifier, and does not offer a way to
          list the batches for a site. The batches below are the ones uploaded from this screen since
          it was opened; paste an identifier to reopen one from an earlier session.
        </Alert>

        <SectionCard title="Where to import" flush>
          <div className="grid gap-4 border-b border-gray-200 px-5 pt-5 pb-6 sm:grid-cols-2 lg:grid-cols-3">
            <SiteSelect
              value={siteCode}
              onChange={setSiteCode}
              required
              helperText="Scans are checked against this site's manifests."
            />
            <div className="flex items-end gap-2">
              <div className="min-w-0 flex-1">
                <label
                  htmlFor="batch-lookup"
                  className="mb-2 block text-theme-sm font-medium text-gray-800"
                >
                  Open a batch by identifier
                </label>
                <input
                  id="batch-lookup"
                  value={lookupId}
                  onChange={(event) => setLookupId(event.target.value)}
                  onKeyDown={(event) => {
                    if (event.key === 'Enter') {
                      openLookup();
                    }
                  }}
                  placeholder="Batch UUID"
                  className="h-10 w-full rounded-md border border-gray-500 bg-white px-3 text-theme-sm text-gray-900 placeholder:text-gray-500 hover:border-gray-700"
                />
              </div>
              <Button variant="outline" startIcon="search" onClick={openLookup}>
                Open
              </Button>
            </div>
          </div>
        </SectionCard>

        {sessionBatches.length > 0 && (
          <SectionCard title="Batches imported in this session" flush>
            <ul className="divide-y divide-gray-100">
              {sessionBatches.map((row) => (
                <li key={row.id}>
                  <button
                    type="button"
                    onClick={() => {
                      setActiveBatchId(row.id);
                      setRowFilter(row.mismatchRows > 0 ? 'PROBLEM' : 'ALL');
                    }}
                    className={`flex w-full flex-wrap items-center justify-between gap-3 px-5 py-3 text-left transition-colors hover:bg-gray-50 ${
                      activeBatchId === row.id ? 'bg-gold-25' : ''
                    }`}
                  >
                    <span className="min-w-0">
                      <span className="block truncate text-theme-sm font-semibold text-gray-900">
                        {row.batchReference ?? shortId(row.id)}
                      </span>
                      <span className="block text-theme-xs text-gray-600">
                        {row.sourceSystem} · {row.totalRows} rows
                      </span>
                    </span>
                    <span className="flex shrink-0 items-center gap-2">
                      <StatusChip
                        value="ACCEPTED"
                        label={`${row.acceptedRows} matched`}
                        tone="ready"
                      />
                      {row.mismatchRows > 0 && (
                        <StatusChip
                          value="MISMATCH"
                          label={`${row.mismatchRows} mismatched`}
                          tone="blocked"
                        />
                      )}
                      <StatusChip value={row.status} />
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          </SectionCard>
        )}

        {activeBatchId && (
          <DataState
            loading={batch.initialising}
            error={batch.error}
            onRetry={batch.refetch}
            minHeight={200}
          >
            {selected && (
              <>
                <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
                  <StatCard
                    label="Rows scanned"
                    value={formatNumber(selected.totalRows)}
                    icon="upload"
                    caption={selected.batchReference ?? shortId(selected.id)}
                  />
                  <StatCard
                    label="Matched"
                    value={formatNumber(selected.acceptedRows)}
                    icon="check-circle"
                    tone={selected.acceptedRows > 0 ? 'good' : 'neutral'}
                    caption="Found on the manifest"
                  />
                  <StatCard
                    label="Mismatched"
                    value={formatNumber(selected.mismatchRows)}
                    icon="alert-circle"
                    tone={selected.mismatchRows > 0 ? 'critical' : 'neutral'}
                    caption="Each raises an exception case"
                    onClick={() => navigate(`${dispatchPaths.exceptions}?type=SCAN_MISMATCH`)}
                  />
                  <StatCard
                    label="Source"
                    value={selected.sourceSystem}
                    icon="cloud"
                    caption={
                      selected.dispatchId
                        ? `Checked against ${shortId(selected.dispatchId)}`
                        : 'Checked against the site’s manifests'
                    }
                  />
                </div>

                <SectionCard
                  className="mt-5"
                  title="Row outcomes"
                  subtitle={`${selected.totalRows} rows · ${selected.sourceSystem}`}
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
                    loading={rows.initialising}
                    error={rows.error}
                    empty={filteredRows.length === 0}
                    emptyTitle="No rows match this filter"
                    emptyHint={
                      rowFilter === 'PROBLEM'
                        ? 'Every scanned code in this batch matched the manifest.'
                        : 'Nothing to show.'
                    }
                    onRetry={rows.refetch}
                    minHeight={200}
                  >
                    <DataTable
                      rows={filteredRows}
                      columns={rowColumns}
                      getRowId={(row) => row.id}
                      loading={rows.loading}
                      caption="Every scanned row in this batch, with the code, the outcome the service classified it as, and the item it matched."
                      dense
                    />
                  </DataState>
                </SectionCard>
              </>
            )}
          </DataState>
        )}

        <div className="grid gap-5 xl:grid-cols-2">
          <SectionCard title="File format" subtitle="Two columns, read by position">
            <p className="text-theme-sm text-gray-700">
              The header row is skipped. Columns are read by position rather than by name:
            </p>
            <ul className="mt-2 flex flex-wrap gap-1.5">
              {SCAN_CSV_HEADERS.map((header, index) => (
                <li
                  key={header}
                  className="rounded border border-gray-200 bg-gray-50 px-2 py-0.5 font-mono text-theme-xs text-gray-900"
                >
                  {index + 1}. {header}
                </li>
              ))}
            </ul>
            <p className="mt-4 text-theme-xs text-gray-600">
              A file with a single column is read as the scanned code, with a row reference generated
              for each line. Blank lines are skipped; a file with only a header is refused.
            </p>
          </SectionCard>

          <SectionCard title="How a row is classified" subtitle="Three outcomes, one of them fine">
            <ul className="space-y-3 text-theme-sm text-gray-700">
              <li className="flex items-start gap-2.5">
                <Icon name="check-circle" size={15} className="mt-0.5 shrink-0 text-success-700" />
                <span>
                  <strong>Matched</strong> — the code belongs to an item the manifest expects.
                </span>
              </li>
              <li className="flex items-start gap-2.5">
                <Icon name="alert-circle" size={15} className="mt-0.5 shrink-0 text-error-800" />
                <span>
                  <strong>Mismatch</strong> — the code belongs to a registered item, but not one this
                  manifest carries. An exception case is raised.
                </span>
              </li>
              <li className="flex items-start gap-2.5">
                <Icon name="alert-triangle" size={15} className="mt-0.5 shrink-0 text-warning-700" />
                <span>
                  <strong>Unregistered</strong> — the code belongs to no item on the register at all.
                  An exception case is raised.
                </span>
              </li>
              <li className="flex items-start gap-2.5">
                <Icon name="info" size={15} className="mt-0.5 shrink-0 text-teal-700" />
                <span>
                  Scanning a batch against the wrong consignment raises a case per row, so check the
                  manifest before uploading.
                </span>
              </li>
            </ul>
          </SectionCard>
        </div>
      </div>

      {importing && (
        <ScanImportDialog
          open
          defaultSiteCode={siteCode}
          onClose={() => setImporting(false)}
          onImported={(imported) => {
            setSessionBatches((current) => [imported, ...current]);
            setActiveBatchId(imported.id);
            setRowFilter(imported.mismatchRows > 0 ? 'PROBLEM' : 'ALL');
            if (imported.mismatchRows > 0) {
              notifyError(
                undefined,
                `${imported.mismatchRows} of ${imported.totalRows} rows did not match. Each has raised an exception case.`,
              );
            } else {
              notifySuccess(
                `All ${imported.totalRows} scanned rows matched the manifest.`,
              );
            }
          }}
        />
      )}
    </div>
  );
};

export default ScanImportsPage;
