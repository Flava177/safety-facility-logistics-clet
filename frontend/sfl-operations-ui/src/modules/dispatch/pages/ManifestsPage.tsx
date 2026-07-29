import { useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { DispatchManifest } from 'modules/dispatch/api/dto';
import { DISPATCH_STATUSES, DispatchStatus } from 'modules/dispatch/api/enums';
import { manifestsApi } from 'modules/dispatch/api/dispatchApi';
import { CreateManifestDialog } from 'modules/dispatch/dialogs/manifestDialogs';
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
import { EnumSelect, TextInput } from 'shared/components/fields';
import { formatDateTime, formatNumber } from 'shared/components/format';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { useClampPage, useServerPage } from 'shared/hooks/useServerPage';
import { dispatchPaths } from 'shared/layout/navigation';

/**
 * The manifest register.
 *
 * Site, status, destination centre, trip and the date range all reach the service. The seal count is
 * shown beside the item count because the two disagreeing is the first sign that a consignment was
 * assembled wrongly — a sealed manifest with no seals recorded should not exist.
 */
const ManifestsPage = () => {
  const navigate = useNavigate();
  const { notifySuccess } = useNotifier();
  const [searchParams] = useSearchParams();

  const [siteCode, setSiteCode] = useState(defaultSite);
  const [status, setStatus] = useState<DispatchStatus | ''>(
    (searchParams.get('status') as DispatchStatus | null) ?? '',
  );
  const [destinationCentre, setDestinationCentre] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [creating, setCreating] = useState(false);

  const filterKey = `${siteCode}|${status}|${destinationCentre}|${from}|${to}`;
  const paging = useServerPage(filterKey);

  const query = useApiQuery(
    (signal) =>
      manifestsApi.search(
        {
          siteCode,
          status: status || undefined,
          destinationCentre: destinationCentre.trim() || undefined,
          from: from ? new Date(from).toISOString() : undefined,
          to: to ? new Date(to).toISOString() : undefined,
          page: paging.page,
          size: paging.size,
        },
        signal,
      ),
    [siteCode, status, destinationCentre, from, to, paging.page, paging.size],
  );

  useClampPage(paging.page, query.data?.totalPages, paging.setPage);

  const columns = useMemo<Column<DispatchManifest>[]>(
    () => [
      {
        key: 'manifest',
        header: 'Manifest',
        width: 280,
        cell: (row) => (
          <CellStack
            primary={`${row.manifestNumber} · ${row.route}`}
            secondary={row.destinationCentre ?? 'No destination centre recorded'}
          />
        ),
      },
      {
        key: 'items',
        header: 'Items',
        width: 100,
        align: 'right',
        cell: (row) => formatNumber(row.itemCount),
      },
      {
        key: 'seals',
        header: 'Seals',
        width: 110,
        align: 'right',
        cell: (row) =>
          row.sealIds.length > 0 ? (
            formatNumber(row.sealIds.length)
          ) : (
            <span className="text-gray-500">None</span>
          ),
      },
      {
        key: 'handler',
        header: 'Handler',
        width: 150,
        hideBelowLg: true,
        cell: (row) => row.assignedHandler,
      },
      {
        key: 'dispatched',
        header: 'Dispatched',
        width: 160,
        hideBelowLg: true,
        cell: (row) => formatDateTime(row.dispatchedAt),
      },
      {
        key: 'movement',
        header: 'Movement',
        width: 110,
        align: 'center',
        hideBelowLg: true,
        cell: (row) =>
          row.tripId ? (
            <StatusChip value="ASSIGNED" label="Trip" tone="active" />
          ) : (
            <span className="text-gray-500">—</span>
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

  const filtersApplied = Boolean(status || destinationCentre || from || to);

  return (
    <div>
      <PageHeader
        title="Dispatch manifests"
        subtitle="Consignments, their seals, custody chain, receipt and return leg."
        crumbs={[{ label: 'Dispatch', to: dispatchPaths.dashboard }, { label: 'Manifests' }]}
        actions={
          <Button variant="primary" startIcon="plus" onClick={() => setCreating(true)}>
            Create manifest
          </Button>
        }
      />

      <SectionCard flush>
        <FilterBar
          onReset={() => {
            setStatus('');
            setDestinationCentre('');
            setFrom('');
            setTo('');
          }}
          resetDisabled={!filtersApplied}
        >
          <SiteSelect value={siteCode} onChange={setSiteCode} required />
          <EnumSelect
            label="Status"
            value={status}
            options={DISPATCH_STATUSES}
            onChange={(value) => setStatus(value)}
            allowEmpty
          />
          <TextInput
            label="Destination centre"
            value={destinationCentre}
            onChange={setDestinationCentre}
            placeholder="Part of a centre name"
          />
          <DateTimeField label="From" value={from} onChange={setFrom} />
          <DateTimeField label="To" value={to} onChange={setTo} />
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
              onRowClick={(row) => navigate(dispatchPaths.manifestDetail(row.id))}
              caption="Dispatch manifests matching the current filters, with item and seal counts, handler, dispatch time, whether a movement is assigned, and status."
              emptyMessage="No manifest matches these filters."
              page={query.data?.page ?? paging.page}
              pageSize={query.data?.size ?? paging.size}
              totalElements={query.data?.totalElements ?? 0}
              onPageChange={paging.setPage}
              onPageSizeChange={paging.setSize}
            />
          </DataState>
        </SectionCard>
      </div>

      {creating && (
        <CreateManifestDialog
          open
          defaultSiteCode={siteCode}
          onClose={() => setCreating(false)}
          onSaved={(manifest) => {
            notifySuccess(
              `${manifest.manifestNumber} created as a draft.`,
              'Add its items before sealing — the contents freeze at that point.',
            );
            query.refetch();
            navigate(dispatchPaths.manifestDetail(manifest.id));
          }}
        />
      )}
    </div>
  );
};

export default ManifestsPage;
