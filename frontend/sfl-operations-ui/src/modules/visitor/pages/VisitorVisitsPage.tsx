import { useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import Button from 'shared/components/Button';
import DataTable, { CellStack, type Column } from 'shared/components/DataTable';
import FormDialog from 'shared/components/FormDialog';
import PageHeader from 'shared/components/PageHeader';
import SectionCard from 'shared/components/SectionCard';
import SiteSelect, { defaultSite } from 'shared/components/SiteSelect';
import StatusChip from 'shared/components/StatusChip';
import { EnumSelect, TextInput } from 'shared/components/fields';
import { formatDateTime } from 'shared/components/format';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { permits } from 'shared/layout/actorPermissions';
import { visitorPaths } from 'shared/layout/navigation';
import { useNotifier } from 'shared/components/Notifier';
import { visitorApi } from '../api/visitorApi';
import type { VisitPurpose, VisitStatus, VisitorVisit } from '../api/dto';

const statuses: VisitStatus[] = ['PRE_REGISTERED', 'CONFIRMED', 'CHECKED_IN', 'CHECKED_OUT', 'REJECTED', 'CANCELLED', 'NO_SHOW'];
const purposes: VisitPurpose[] = ['MEETING', 'INTERVIEW', 'EVENT', 'DELIVERY', 'MAINTENANCE_CONTRACTOR', 'OTHER'];
const toIso = (value: string) => new Date(value).toISOString();

const VisitorVisitsPage = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const notify = useNotifier();
  const [siteCode, setSiteCode] = useState(defaultSite);
  const requestedStatus = searchParams.get('status') as VisitStatus | null;
  const [status, setStatus] = useState<VisitStatus | ''>(requestedStatus && statuses.includes(requestedStatus) ? requestedStatus : '');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);
  const [open, setOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [form, setForm] = useState({ visitorName: '', visitorOrganization: '', visitorContact: '', hostId: '', hostName: '', purpose: 'MEETING' as VisitPurpose, expectedArrival: '', expectedDeparture: '' });

  const query = useApiQuery(
    (signal) => visitorApi.search({ siteCode: siteCode || undefined, status: status || undefined, page, size, sort: 'expectedArrival,desc' }, signal),
    [siteCode, status, page, size],
  );

  const columns = useMemo<Column<VisitorVisit>[]>(() => [
    { key: 'visitor', header: 'Visitor', width: 240, cell: (row) => <CellStack primary={row.visitorName} secondary={row.visitorOrganization ?? row.visitorContact ?? 'No organisation'} /> },
    { key: 'host', header: 'Host', width: 190, cell: (row) => <CellStack primary={row.hostName ?? row.hostId} secondary={row.hostName ? row.hostId : undefined} /> },
    { key: 'purpose', header: 'Purpose', width: 150, cell: (row) => <StatusChip value={row.purpose} /> },
    { key: 'arrival', header: 'Expected arrival', width: 180, cell: (row) => formatDateTime(row.expectedArrival) },
    { key: 'site', header: 'Site', width: 100, hideBelowLg: true, cell: (row) => row.siteCode },
    { key: 'status', header: 'Status', width: 150, align: 'right', cell: (row) => <StatusChip value={row.status} /> },
  ], []);

  const create = async () => {
    if (!siteCode || !form.visitorName.trim() || !form.hostId.trim() || !form.expectedArrival) return;
    setSubmitting(true);
    try {
      const visit = await visitorApi.preRegister({
        siteCode,
        visitorName: form.visitorName.trim(),
        visitorOrganization: form.visitorOrganization.trim() || undefined,
        visitorContact: form.visitorContact.trim() || undefined,
        hostId: form.hostId.trim(),
        hostName: form.hostName.trim() || undefined,
        purpose: form.purpose,
        expectedArrival: toIso(form.expectedArrival),
        expectedDeparture: form.expectedDeparture ? toIso(form.expectedDeparture) : undefined,
      });
      notify.notifySuccess('Visit pre-registered', `${visit.visitorName} · ${visit.siteCode}`);
      setOpen(false);
      query.refetch();
      navigate(visitorPaths.visitDetail(visit.id));
    } catch (error) {
      notify.notifyError(error);
    } finally {
      setSubmitting(false);
    }
  };

  return <div>
    <PageHeader title="Visitor management" subtitle="Pre-register visits, manage approvals and follow every visitor through arrival and departure." crumbs={[{ label: 'Safety & security' }, { label: 'Visitors' }]} actions={permits('VISITOR_VISIT_CREATE') ? <Button variant="primary" startIcon="plus" onClick={() => setOpen(true)}>Pre-register visit</Button> : undefined} />
    <SectionCard title="Visit register" subtitle="Server-paginated and scoped to the selected site and lifecycle state." flush>
      <div className="grid gap-4 px-5 py-4 sm:grid-cols-2">
        <SiteSelect value={siteCode} onChange={(value) => { setSiteCode(value); setPage(0); }} allowEmpty />
        <EnumSelect label="Status" value={status} options={statuses} allowEmpty onChange={(value) => { setStatus(value); setPage(0); }} />
      </div>
      <DataTable rows={query.data?.content ?? []} columns={columns} getRowId={(row) => row.id} loading={query.loading} onRowClick={(row) => navigate(visitorPaths.visitDetail(row.id))} caption="Visitor visits" page={page} pageSize={size} totalElements={query.data?.totalElements ?? 0} onPageChange={setPage} onPageSizeChange={(value) => { setSize(value); setPage(0); }} emptyMessage="No visits match these filters." />
    </SectionCard>

    <FormDialog open={open} title="Pre-register a visit" description="Reserve the visit window and start the approval workflow." submitLabel="Pre-register visit" submitting={submitting} submitDisabled={!siteCode || !form.visitorName.trim() || !form.hostId.trim() || !form.expectedArrival} onClose={() => setOpen(false)} onSubmit={create}>
      <div className="grid gap-4 sm:grid-cols-2">
        <SiteSelect value={siteCode} onChange={setSiteCode} required />
        <EnumSelect label="Purpose" value={form.purpose} options={purposes} required onChange={(value) => value && setForm((current) => ({ ...current, purpose: value }))} />
        <TextInput label="Visitor name" value={form.visitorName} onChange={(value) => setForm((current) => ({ ...current, visitorName: value }))} required />
        <TextInput label="Organisation" value={form.visitorOrganization} onChange={(value) => setForm((current) => ({ ...current, visitorOrganization: value }))} />
        <TextInput label="Contact" value={form.visitorContact} onChange={(value) => setForm((current) => ({ ...current, visitorContact: value }))} />
        <TextInput label="Host ID" value={form.hostId} onChange={(value) => setForm((current) => ({ ...current, hostId: value }))} required />
        <TextInput label="Host name" value={form.hostName} onChange={(value) => setForm((current) => ({ ...current, hostName: value }))} />
        <label className="text-theme-sm font-medium text-gray-800">Expected arrival<input type="datetime-local" className="mt-2 h-10 w-full rounded-md border border-gray-500 px-3" value={form.expectedArrival} onChange={(event) => setForm((current) => ({ ...current, expectedArrival: event.target.value }))} required /></label>
        <label className="text-theme-sm font-medium text-gray-800">Expected departure<input type="datetime-local" className="mt-2 h-10 w-full rounded-md border border-gray-500 px-3" value={form.expectedDeparture} onChange={(event) => setForm((current) => ({ ...current, expectedDeparture: event.target.value }))} /></label>
      </div>
    </FormDialog>
  </div>;
};

export default VisitorVisitsPage;
