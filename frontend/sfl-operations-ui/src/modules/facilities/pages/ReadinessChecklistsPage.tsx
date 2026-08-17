import { useState } from 'react';
import { useNavigate } from 'react-router';
import ControlButton from 'shared/components/ControlButton';
import DataState from 'shared/components/DataState';
import DataTable, { Column } from 'shared/components/DataTable';
import FilterBar from 'shared/components/FilterBar';
import PageHeader from 'shared/components/PageHeader';
import SiteSelect, { defaultSite } from 'shared/components/SiteSelect';
import StatusChip from 'shared/components/StatusChip';
import { useNotifier } from 'shared/components/Notifier';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { facilitiesPaths } from 'shared/layout/navigation';
import type { ReadinessChecklist } from '../api/dto';
import { createChecklist, listChecklists, updateChecklist } from '../api/facilitiesApi';
import { createChecklistControl, editChecklistControl } from '../api/workflow';
import RowActions from '../components/RowActions';
import { humaniseCode } from '../components/facilitiesFormat';
import { CreateChecklistDialog, EditChecklistDialog } from '../dialogs/checklistDialogs';

/**
 * The readiness checklists configured for a site.
 *
 * Applicability is the column worth reading: a checklist naming both a space type and an operating
 * mode applies narrowly, one naming neither applies to everything, and the most specific match wins
 * when an assessment is taken. Showing "Any" rather than an empty cell makes that rule visible.
 */
const ReadinessChecklistsPage = () => {
  const navigate = useNavigate();
  const notify = useNotifier();
  const [siteCode, setSiteCode] = useState<string>(defaultSite);
  const [adding, setAdding] = useState(false);
  const [editing, setEditing] = useState<ReadinessChecklist | null>(null);

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
    {
      key: 'actions',
      header: '',
      width: 100,
      align: 'right',
      cell: (checklist) => (
        <RowActions>
          <ControlButton
            state={editChecklistControl(checklist)}
            variant="ghost"
            size="sm"
            startIcon="edit"
            onClick={() => setEditing(checklist)}
          >
            Edit
          </ControlButton>
        </RowActions>
      ),
    },
  ];

  return (
    <>
      <PageHeader
        title="Readiness checklists"
        subtitle="What an assessment asks, and what a failure costs"
        actions={
          <ControlButton
            state={createChecklistControl()}
            variant="primary"
            startIcon="plus"
            onClick={() => setAdding(true)}
          >
            Add a checklist
          </ControlButton>
        }
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

      {adding && (
        <CreateChecklistDialog
          siteCode={siteCode || defaultSite}
          onClose={() => setAdding(false)}
          onSubmit={async (request) => {
            const created = await createChecklist(request);
            setAdding(false);
            notify.notifySuccess(`${created.checklistCode} added to ${created.siteCode}.`);
            refetch();
          }}
        />
      )}

      {editing && (
        <EditChecklistDialog
          checklist={editing}
          onClose={() => setEditing(null)}
          onSubmit={async (request) => {
            const saved = await updateChecklist(editing.id, request);
            setEditing(null);
            notify.notifySuccess(`${saved.checklistCode} saved at version ${saved.version}.`);
            refetch();
          }}
        />
      )}
    </>
  );
};

export default ReadinessChecklistsPage;
