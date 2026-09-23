import { useState } from 'react';
import { useNavigate } from 'react-router';
import Button from 'shared/components/Button';
import DataState from 'shared/components/DataState';
import PageHeader from 'shared/components/PageHeader';
import SectionCard from 'shared/components/SectionCard';
import SiteSelect, { defaultSite } from 'shared/components/SiteSelect';
import StatCard from 'shared/components/StatCard';
import StatusChip from 'shared/components/StatusChip';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { incidentPaths } from 'shared/layout/navigation';
import { incidentApi } from '../api/incidentApi';
import type { IncidentStatus, Severity } from '../api/dto';

const IncidentDashboardPage = () => {
  const navigate = useNavigate();
  const [siteCode, setSiteCode] = useState(defaultSite);
  const query = useApiQuery((signal) => siteCode ? incidentApi.dashboard(siteCode, signal) : Promise.resolve({ byStatus: {}, bySeverity: {} } as import('../api/dto').IncidentDashboard), [siteCode]);
  const countStatus = (value: IncidentStatus) => query.data?.byStatus[value] ?? 0;
  const countSeverity = (value: Severity) => query.data?.bySeverity[value] ?? 0;
  const open = countStatus('TRIAGE') + countStatus('INVESTIGATING');
  return <div>
    <PageHeader title="Incident assurance" subtitle="Open cases, risk concentration and emergency-rated events for the selected site." crumbs={[{ label: 'Safety & security' }, { label: 'Incident dashboard' }]} actions={<Button variant="outline" startIcon="refresh" onClick={query.refetch}>Refresh</Button>} />
    <div className="mb-5"><SectionCard><div className="max-w-sm"><SiteSelect value={siteCode} onChange={setSiteCode} required /></div></SectionCard></div>
    <DataState loading={query.initialising} error={query.error} onRetry={query.refetch}>
      <div className="space-y-5">
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <StatCard label="Open cases" value={open} icon="clipboard-list" tone={open ? 'caution' : 'good'} onClick={() => navigate(incidentPaths.cases)} />
          <StatCard label="Awaiting triage" value={countStatus('TRIAGE')} icon="clock" tone={countStatus('TRIAGE') ? 'caution' : 'neutral'} onClick={() => navigate(`${incidentPaths.cases}?status=TRIAGE`)} />
          <StatCard label="Under investigation" value={countStatus('INVESTIGATING')} icon="search" onClick={() => navigate(`${incidentPaths.cases}?status=INVESTIGATING`)} />
          <StatCard label="Emergency rated" value={countSeverity('EMERGENCY')} icon="siren" tone={countSeverity('EMERGENCY') ? 'critical' : 'good'} onClick={() => navigate(`${incidentPaths.cases}?severity=EMERGENCY`)} />
        </div>
        <div className="grid gap-5 lg:grid-cols-2">
          <SectionCard title="Cases by lifecycle"><div className="space-y-3">{(['TRIAGE', 'INVESTIGATING', 'CLOSED'] as IncidentStatus[]).map((status) => <div key={status} className="flex items-center justify-between border-b border-gray-100 pb-3 last:border-0"><StatusChip value={status} /><strong className="tabular-nums">{countStatus(status)}</strong></div>)}</div></SectionCard>
          <SectionCard title="Cases by severity"><div className="space-y-3">{(['EMERGENCY', 'CRITICAL', 'HIGH', 'MEDIUM', 'LOW'] as Severity[]).map((severity) => <div key={severity} className="flex items-center justify-between border-b border-gray-100 pb-3 last:border-0"><StatusChip value={severity} /><strong className="tabular-nums">{countSeverity(severity)}</strong></div>)}</div></SectionCard>
        </div>
      </div>
    </DataState>
  </div>;
};
export default IncidentDashboardPage;
