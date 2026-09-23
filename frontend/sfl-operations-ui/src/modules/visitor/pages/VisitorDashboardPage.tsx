import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router';
import Button from 'shared/components/Button';
import DataState from 'shared/components/DataState';
import DataTable, { CellStack, type Column } from 'shared/components/DataTable';
import PageHeader from 'shared/components/PageHeader';
import SectionCard from 'shared/components/SectionCard';
import SiteSelect, { defaultSite } from 'shared/components/SiteSelect';
import StatCard from 'shared/components/StatCard';
import StatusChip from 'shared/components/StatusChip';
import { formatDateTime } from 'shared/components/format';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { visitorPaths } from 'shared/layout/navigation';
import { visitorApi } from '../api/visitorApi';
import type { VisitorVisit } from '../api/dto';

const VisitorDashboardPage = () => {
  const navigate = useNavigate();
  const [siteCode, setSiteCode] = useState(defaultSite);
  const visits = useApiQuery((signal) => visitorApi.search({ siteCode: siteCode || undefined, page: 0, size: 100, sort: 'expectedArrival,desc' }, signal), [siteCode]);
  const rows = useMemo(() => visits.data?.content ?? [], [visits.data]);
  const count = (status: string) => rows.filter((visit) => visit.status === status).length;
  const active = rows.filter((visit) => ['PRE_REGISTERED', 'CONFIRMED', 'CHECKED_IN'].includes(visit.status));
  const columns = useMemo<Column<VisitorVisit>[]>(() => [
    { key: 'visitor', header: 'Visitor', width: 230, cell: (row) => <CellStack primary={row.visitorName} secondary={row.visitorOrganization ?? row.purpose} /> },
    { key: 'host', header: 'Host', width: 180, cell: (row) => row.hostName ?? row.hostId },
    { key: 'arrival', header: 'Expected arrival', width: 180, cell: (row) => formatDateTime(row.expectedArrival) },
    { key: 'status', header: 'Status', align: 'right', cell: (row) => <StatusChip value={row.status} /> },
  ], []);
  return <div>
    <PageHeader title="Visitor operations" subtitle="Expected arrivals, approvals and the people currently inside each site." crumbs={[{ label: 'Safety & security' }, { label: 'Visitor dashboard' }]} actions={<Button variant="outline" startIcon="refresh" onClick={visits.refetch}>Refresh</Button>} />
    <div className="mb-5"><SectionCard><div className="max-w-sm"><SiteSelect value={siteCode} onChange={setSiteCode} allowEmpty /></div></SectionCard></div>
    <DataState loading={visits.initialising} error={visits.error} onRetry={visits.refetch}>
      <div className="space-y-5">
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <StatCard label="Awaiting decision" value={count('PRE_REGISTERED')} icon="clock" tone={count('PRE_REGISTERED') ? 'caution' : 'neutral'} onClick={() => navigate(`${visitorPaths.visits}?status=PRE_REGISTERED`)} />
          <StatCard label="Confirmed" value={count('CONFIRMED')} icon="calendar" onClick={() => navigate(`${visitorPaths.visits}?status=CONFIRMED`)} />
          <StatCard label="On site now" value={count('CHECKED_IN')} icon="users" tone={count('CHECKED_IN') ? 'accent' : 'neutral'} onClick={() => navigate(visitorPaths.rollCall)} />
          <StatCard label="Watchlist flags" value={active.filter((visit) => visit.watchlistFlagged).length} icon="alert-triangle" tone={active.some((visit) => visit.watchlistFlagged) ? 'critical' : 'good'} />
        </div>
        <SectionCard title="Active and upcoming visits" subtitle="The newest 100 records are used for this operational snapshot." flush>
          <DataTable rows={active.slice(0, 10)} columns={columns} getRowId={(row) => row.id} onRowClick={(row) => navigate(visitorPaths.visitDetail(row.id))} caption="Active visitor visits" emptyMessage="No active or upcoming visits." />
        </SectionCard>
      </div>
    </DataState>
  </div>;
};
export default VisitorDashboardPage;
