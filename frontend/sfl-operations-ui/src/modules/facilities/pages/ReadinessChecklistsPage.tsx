import { useState } from 'react';
import { useNavigate } from 'react-router';
import DataState from 'shared/components/DataState';
import DataTable, { Column } from 'shared/components/DataTable';
import FilterBar from 'shared/components/FilterBar';
import PageHeader from 'shared/components/PageHeader';
import SiteSelect, { defaultSite } from 'shared/components/SiteSelect';
import StatusChip from 'shared/components/StatusChip';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { facilitiesPaths } from 'shared/layout/navigation';
import type { ReadinessChecklist } from '../api/dto';
import { listChecklists } from '../api/facilitiesApi';
import { humaniseCode } from '../components/facilitiesFormat';

/**
 * The readiness checklists configured for a site.
 *
 * Applicability is the column worth reading: a checklist naming both a space type and an operating
 * mode applies narrowly, one naming neither applies to everything, and the most specific match wins
 * when an assessment is taken. Showing "Any" rather than an empty cell makes that rule visible.
 */
const ReadinessChecklistsPage = () => {
  const navigate = useNavigate();
  const [siteCode, setSiteCode] = useState<string>(defaultSite);

  const { data, loading, error, refetch } = useApiQuery(
    (signal) => listChecklists(siteCode || undefined, signal),
    [siteCode],
  );

  const columns: Column<ReadinessChecklist>[] = [
    {
      key: 'checklistCode',
      header: 'Code',
      width: 160,
      cell: (checklist) => (
        <span className="font-medium text-gray-900">{checklist.checklistCode}</span>
      ),
    },
    { key: 'name', header: 'Checklist', cell: (checklist) => checklist.name },
    {
      key: 'spaceType',
      header: 'Applies to',
      cell: (checklist) => (
        <span className="text-gray-600">
          {checklist.spaceType ? humaniseCode(checklist.spaceType) : 'Any space type'}
        </span>
      ),
    },
    {
      key: 'operatingMode',
      header: 'In mode',
      width: 140,
      cell: (checklist) =>
        checklist.operatingMode ? (
          <StatusChip
            value={checklist.operatingMode}
            tone={checklist.operatingMode === 'EXAMINATION' ? 'accent' : 'neutral'}
          />
        ) : (
          <span className="text-gray-500">Any mode</span>
        ),
    },
    {
      key: 'items',
      header: 'Items',
      align: 'right',
      width: 90,
      cell: (checklist) => checklist.items.length,
    },
    {
      key: 'version',
      header: 'Version',
      align: 'right',
      width: 100,
      cell: (checklist) => <span className="text-gray-600">v{checklist.version}</span>,
    },
  ];

  return (
    <>
      <PageHeader
        title="Readiness checklists"
        subtitle="What an assessment asks, and what a failure costs"
      />

      <FilterBar>
        <SiteSelect value={siteCode} onChange={setSiteCode} allowEmpty emptyLabel="All sites" />
      </FilterBar>

      <DataState
        loading={loading}
        error={error}
        empty={!data || data.length === 0}
        emptyTitle="No checklists configured"
        emptyHint="Without one, an assessment records no answers and a space stays UNKNOWN."
        onRetry={refetch}
      >
        {data && (
          <DataTable
            rows={data}
            columns={columns}
            getRowId={(checklist) => checklist.id}
            onRowClick={(checklist) => navigate(facilitiesPaths.checklistDetail(checklist.id))}
            caption="Readiness checklists"
          />
        )}
      </DataState>
    </>
  );
};

export default ReadinessChecklistsPage;
