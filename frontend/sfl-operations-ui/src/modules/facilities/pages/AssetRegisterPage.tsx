import { useState } from 'react';
import { useNavigate } from 'react-router';
import DataState from 'shared/components/DataState';
import DataTable, { Column } from 'shared/components/DataTable';
import FilterBar from 'shared/components/FilterBar';
import PageHeader from 'shared/components/PageHeader';
import Select from 'shared/components/Select';
import SiteSelect, { defaultSite } from 'shared/components/SiteSelect';
import StatusChip from 'shared/components/StatusChip';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { facilitiesPaths } from 'shared/layout/navigation';
import type { FacilityAsset } from '../api/dto';
import { assetCategories, assetCriticalities, assetOperationalStatuses } from '../api/enums';
import type { AssetCategory, AssetCriticality, AssetOperationalStatus } from '../api/enums';
import { searchAssets } from '../api/facilitiesApi';
import {
  assetStatusTone,
  formatDate,
  humaniseCode,
} from '../components/facilitiesFormat';

/**
 * The facility asset register.
 *
 * Fixed plant — the chillers, lifts, generators and panels S153 will raise work orders against. Not
 * AVAMP-Lite's asset references, which carry cross-programme identity for movable things; the two
 * are linked by value and answer different questions.
 *
 * Criticality and condition sit next to each other because their combination is what matters: a low
 * asset out of service is a note, a critical one out of service has blocked a hall.
 */
const AssetRegisterPage = () => {
  const navigate = useNavigate();
  const [siteCode, setSiteCode] = useState<string>(defaultSite);
  const [category, setCategory] = useState<string>('');
  const [criticality, setCriticality] = useState<string>('');
  const [status, setStatus] = useState<string>('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);

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
  ];

  return (
    <>
      <PageHeader
        title="Facility assets"
        subtitle="Fixed plant and equipment, and what its condition does to the estate"
      />

      <FilterBar>
        <SiteSelect
          value={siteCode}
          onChange={(v) => changeFilter(() => setSiteCode(v))}
          allowEmpty
          emptyLabel="All sites"
        />
        <Select
          value={category}
          onChange={(v) => changeFilter(() => setCategory(v))}
          placeholder="Any category"
          options={[
            { value: '', label: 'Any category' },
            ...assetCategories.map((value) => ({ value, label: humaniseCode(value) })),
          ]}
        />
        <Select
          value={criticality}
          onChange={(v) => changeFilter(() => setCriticality(v))}
          placeholder="Any criticality"
          options={[
            { value: '', label: 'Any criticality' },
            ...assetCriticalities.map((value) => ({ value, label: humaniseCode(value) })),
          ]}
        />
        <Select
          value={status}
          onChange={(v) => changeFilter(() => setStatus(v))}
          placeholder="Any condition"
          options={[
            { value: '', label: 'Any condition' },
            ...assetOperationalStatuses.map((value) => ({ value, label: humaniseCode(value) })),
          ]}
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
    </>
  );
};

export default AssetRegisterPage;
