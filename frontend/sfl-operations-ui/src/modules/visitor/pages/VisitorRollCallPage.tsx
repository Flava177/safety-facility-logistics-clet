import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router';
import Button from 'shared/components/Button';
import DataState from 'shared/components/DataState';
import DataTable, { CellStack, type Column } from 'shared/components/DataTable';
import PageHeader from 'shared/components/PageHeader';
import SectionCard from 'shared/components/SectionCard';
import SiteSelect, { defaultSite } from 'shared/components/SiteSelect';
import StatusChip from 'shared/components/StatusChip';
import { formatDateTime } from 'shared/components/format';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { visitorPaths } from 'shared/layout/navigation';
import { visitorApi } from '../api/visitorApi';
import type { VisitorVisit } from '../api/dto';

const VisitorRollCallPage = () => {
  const navigate = useNavigate();
  const [siteCode, setSiteCode] = useState(defaultSite);
  const query = useApiQuery((signal) => siteCode ? visitorApi.rollCall(siteCode, signal) : Promise.resolve([]), [siteCode]);
  const columns = useMemo<Column<VisitorVisit>[]>(() => [
    { key: 'visitor', header: 'Visitor', width: 240, cell: (row) => <CellStack primary={row.visitorName} secondary={row.visitorOrganization ?? row.visitorContact ?? 'No organisation'} /> },
    { key: 'host', header: 'Host', width: 190, cell: (row) => row.hostName ?? row.hostId },
    { key: 'badge', header: 'Badge', width: 120, cell: (row) => row.badgeNumber ?? '-' },
    { key: 'zones', header: 'Access zones', width: 220, cell: (row) => row.accessZones.join(', ') || '-' },
    { key: 'arrival', header: 'Checked in', width: 180, cell: (row) => formatDateTime(row.checkedInAt) },
    { key: 'status', header: 'Status', align: 'right', cell: (row) => <StatusChip value={row.status} /> },
  ], []);
  return <div>
    <PageHeader title="Visitor roll call" subtitle="The live list of visitors currently checked in at a site." crumbs={[{ label: 'Safety & security' }, { label: 'Visitors', to: visitorPaths.visits }, { label: 'Roll call' }]} actions={<Button variant="outline" startIcon="refresh" onClick={query.refetch}>Refresh</Button>} />
    <SectionCard title="People currently on site" subtitle="Use this list during an evacuation or site accountability check." flush>
      <div className="max-w-sm px-5 py-4"><SiteSelect value={siteCode} onChange={setSiteCode} required /></div>
      <DataState loading={query.initialising} error={query.error} onRetry={query.refetch} empty={!query.data?.length} emptyTitle="No visitors are checked in" emptyHint="The roll call will populate as reception checks visitors in.">
        <DataTable rows={query.data ?? []} columns={columns} getRowId={(row) => row.id} onRowClick={(row) => navigate(visitorPaths.visitDetail(row.id))} caption="Visitor roll call" />
      </DataState>
    </SectionCard>
  </div>;
};
export default VisitorRollCallPage;
