import { useState } from 'react';
import { useNavigate } from 'react-router';
import ControlButton from 'shared/components/ControlButton';
import DataState from 'shared/components/DataState';
import DataTable, { Column } from 'shared/components/DataTable';
import FilterBar from 'shared/components/FilterBar';
import PageHeader from 'shared/components/PageHeader';
import SiteSelect, { defaultSite } from 'shared/components/SiteSelect';
import StatusChip from 'shared/components/StatusChip';
import { SelectInput } from 'shared/components/fields';
import { useNotifier } from 'shared/components/Notifier';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { facilitiesPaths } from 'shared/layout/navigation';
import type { FacilityAsset } from '../api/dto';
import { assetCategories, assetCriticalities, assetOperationalStatuses } from '../api/enums';
import type { AssetCategory, AssetCriticality, AssetOperationalStatus } from '../api/enums';
import { registerAsset, relocateAsset, searchAssets, updateAsset } from '../api/facilitiesApi';
import { createAssetControl, editAssetControl, relocateAssetControl } from '../api/workflow';
import RowActions from '../components/RowActions';
import { assetStatusTone, formatDate, humaniseCode } from '../components/facilitiesFormat';
import {
  EditAssetDialog,
  RegisterAssetDialog,
  RelocateAssetDialog,
} from '../dialogs/assetDialogs';

/**
 * The facility asset register.
 *
 * Fixed plant - the chillers, lifts, generators and panels S153 raises work orders against. Not the
 * asset references that carry cross-programme identity for movable things; the two are linked by
 * value and answer different questions.
 *
 * Criticality and condition sit next to each other because their combination is what matters: a low
 * asset out of service is a note, a critical one out of service has blocked a hall.
 */
const AssetRegisterPage = () => {
  const navigate = useNavigate();
  const notify = useNotifier();
  const [siteCode, setSiteCode] = useState<string>(defaultSite);
  const [category, setCategory] = useState<string>('');
  const [criticality, setCriticality] = useState<string>('');
  const [status, setStatus] = useState<string>('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);
  const [adding, setAdding] = useState(false);
  const [editing, setEditing] = useState<FacilityAsset | null>(null);
  const [moving, setMoving] = useState<FacilityAsset | null>(null);

  const { data, loading, error, refetch } = useApiQuery(
    (signal) =>
      searchAssets(
        {
          siteCode: siteCode || undefined,
          category: (category as AssetCategory) || undefined,
          criticality: (criticality as AssetCriticality) || undefined,
          operationalStatus: (status as AssetOperationalStatus) || undefined,
          page,
          size,
        },
        signal,
      ),
    [siteCode, category, criticality, status, page, size],
  );

  const changeFilter = (apply: () => void) => {
    apply();
    setPage(0);
  };

  const columns: Column<FacilityAsset>[] = [
    {
      key: 'assetCode',
      header: 'Code',
      width: 140,
      cell: (asset) => <span className="font-medium text-gray-900">{asset.assetCode}</span>,
    },
    { key: 'name', header: 'Asset', cell: (asset) => asset.name },
    {
      key: 'category',
      header: 'Category',
      hideBelowLg: true,
      cell: (asset) => humaniseCode(asset.category),
    },
    {
      key: 'criticality',
      header: 'Criticality',
      width: 120,
      cell: (asset) => (
        <StatusChip
          value={asset.criticality}
          tone={
            asset.criticality === 'CRITICAL'
              ? 'blocked'
              : asset.criticality === 'HIGH'
                ? 'caution'
                : 'neutral'
          }
        />
      ),
    },
    {
      key: 'status',
      header: 'Condition',
      width: 160,
      cell: (asset) => (
        <StatusChip
          value={asset.operationalStatus}
          tone={assetStatusTone(asset.operationalStatus)}
        />
      ),
    },
    {
      key: 'serviceDue',
      header: 'Service due',
      width: 140,
      align: 'right',
      hideBelowLg: true,
      cell: (asset) => <span className="text-gray-600">{formatDate(asset.serviceDueOn)}</span>,
    },
    {
      key: 'impairs',
      header: '',
      width: 130,
      align: 'right',
      cell: (asset) =>
        asset.impairsReadiness ? (
          <StatusChip value="BLOCKING" label="Impairs space" tone="blocked" />
        ) : null,
    },
    {
      key: 'actions',
      header: '',
      width: 150,
      align: 'right',
      cell: (asset) => (
        <RowActions>
          <ControlButton
            state={editAssetControl(asset)}
            variant="ghost"
            size="sm"
            startIcon="edit"
            onClick={() => setEditing(asset)}
          >
            Edit
          </ControlButton>
          <ControlButton
            state={relocateAssetControl(asset)}
            variant="ghost"
            size="sm"
            onClick={() => setMoving(asset)}
          >
            Move
          </ControlButton>
        </RowActions>
      ),
    },
  ];

  return (
    <>
      <PageHeader
        title="Facility assets"
        subtitle="Fixed plant and equipment, and what its condition does to the estate"
        actions={
          <ControlButton
            state={createAssetControl()}
            variant="primary"
            startIcon="plus"
            onClick={() => setAdding(true)}
          >
            Register an asset
          </ControlButton>
        }
      />

      {/*
        Every control here carries a label. They were bare `Select`s with only a placeholder, and
        `FilterBar` aligns its children at the top - so the three unlabelled ones sat a label's height
        above the site select and the row read as broken. A label is the better fix than a spacer
        because an operator should not have to open a dropdown to learn what it filters.
      */}
      <FilterBar>
        <SiteSelect
          value={siteCode}
          onChange={(v) => changeFilter(() => setSiteCode(v))}
          allowEmpty
          emptyLabel="All sites"
        />
        <SelectInput
          label="Category"
          value={category}
          onChange={(v) => changeFilter(() => setCategory(v))}
          allowEmpty
          emptyLabel="Any category"
          options={assetCategories.map((value) => ({ value, label: humaniseCode(value) }))}
        />
        <SelectInput
          label="Criticality"
          value={criticality}
          onChange={(v) => changeFilter(() => setCriticality(v))}
          allowEmpty
          emptyLabel="Any criticality"
          options={assetCriticalities.map((value) => ({ value, label: humaniseCode(value) }))}
        />
        <SelectInput
          label="Condition"
          value={status}
          onChange={(v) => changeFilter(() => setStatus(v))}
          allowEmpty
          emptyLabel="Any condition"
          options={assetOperationalStatuses.map((value) => ({ value, label: humaniseCode(value) }))}
        />
      </FilterBar>

      <DataState
        loading={loading}
        error={error}
        empty={!data || data.items.length === 0}
        emptyTitle="No assets match these filters"
        emptyHint="Widen the site, or clear the category and condition filters."
        onRetry={refetch}
      >
        {data && (
          <DataTable
            rows={data.items}
            columns={columns}
            getRowId={(asset) => asset.id}
            onRowClick={(asset) => navigate(facilitiesPaths.assetDetail(asset.id))}
            page={data.page}
            pageSize={data.size}
            totalElements={data.totalElements}
            onPageChange={setPage}
            onPageSizeChange={(next) => changeFilter(() => setSize(next))}
            caption="Facility assets"
          />
        )}
      </DataState>

      {adding && (
        <RegisterAssetDialog
          siteCode={siteCode || defaultSite}
          onClose={() => setAdding(false)}
          onSubmit={async (request) => {
            const created = await registerAsset(request);
            setAdding(false);
            notify.notifySuccess(`${created.assetCode} registered at ${created.siteCode}.`);
            refetch();
          }}
        />
      )}

      {editing && (
        <EditAssetDialog
          asset={editing}
          onClose={() => setEditing(null)}
          onSubmit={async (request) => {
            const saved = await updateAsset(editing.id, request);
            setEditing(null);
            notify.notifySuccess(`${saved.assetCode} updated.`);
            refetch();
          }}
        />
      )}

      {moving && (
        <RelocateAssetDialog
          asset={moving}
          onClose={() => setMoving(null)}
          onSubmit={async (request) => {
            const saved = await relocateAsset(moving.id, request);
            setMoving(null);
            notify.notifySuccess(`${saved.assetCode} moved.`);
            refetch();
          }}
        />
      )}
    </>
  );
};

export default AssetRegisterPage;
