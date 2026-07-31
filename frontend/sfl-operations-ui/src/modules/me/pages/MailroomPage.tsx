import { useNavigate } from 'react-router';
import DataState from 'shared/components/DataState';
import DataTable, { Column } from 'shared/components/DataTable';
import PageHeader from 'shared/components/PageHeader';
import StatusChip from 'shared/components/StatusChip';
import { defaultSite } from 'shared/components/SiteSelect';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { dispatchPaths } from 'shared/layout/navigation';
import { courierItemsApi } from 'modules/dispatch/api/dispatchApi';
import type { CourierItem } from 'modules/dispatch/api/dto';

/**
 * The mailroom officer's day — S171, Derived from the Fleet / Logistics Officer class.
 *
 * The derivation is direct: the mapping's S171 entry is titled "Mailroom / Courier & Despatch
 * Tracking" and names inbound mail explicitly, and the Transportation & Logistics Unit that owns it
 * staffs a mailroom. `MAILROOM_OFFICER` was added additively under S171 decision D-14 for this desk.
 *
 * ## What the seven permissions allow, and what they do not
 *
 * Registering and distributing inbound mail is the whole of it. There is no
 * `DISPATCH_MANIFEST_CREATE` here — sealing and despatching a consignment is the controller's act,
 * not the mailroom's — so this screen does not offer it. Before the nav item carried a permission a
 * mailroom officer was offered the dispatch dashboard as their landing page and met a 403 on
 * arrival; that is the mistake this page exists to stop repeating.
 *
 * ## Not narrowed, and it does not pretend to be
 *
 * `assignedHandler` is free text with no relationship to a principal, so there is nothing to narrow
 * on and this is the site's inbound register rather than "mine". Recorded as C-16 in
 * `docs/fleet/S166_Gap_And_Conflict_Report.md`; the headings say "at this site" for that reason.
 */
const MailroomPage = () => {
  const navigate = useNavigate();
  const site = defaultSite;

  const inbound = useApiQuery(
    (signal) => courierItemsApi.search({ siteCode: site, direction: 'INBOUND', size: 50 }, signal),
    [site],
  );

  const rows = inbound.data?.content ?? [];
  const awaiting = rows.filter((row) => row.status === 'RECEIVED');

  const columns: Column<CourierItem>[] = [
    { key: 'itemNumber', header: 'Item', cell: (row) => row.itemNumber },
    { key: 'sender', header: 'From', cell: (row) => row.sender ?? row.origin },
    { key: 'recipient', header: 'For', cell: (row) => row.recipient ?? row.destination },
    { key: 'itemType', header: 'Type', cell: (row) => <StatusChip value={row.itemType} /> },
    { key: 'sensitivity', header: 'Sensitivity', cell: (row) => <StatusChip value={row.sensitivity} /> },
    { key: 'status', header: 'Status', cell: (row) => <StatusChip value={row.status} /> },
  ];

  return (
    <div className="space-y-8">
      <PageHeader
        title="Mailroom"
        subtitle={'Inbound items at ' + site + ' — register what arrives, distribute what is due'}
      />

      <section className="space-y-3">
        <h2 className="text-lg font-semibold text-slate-800">Awaiting distribution</h2>
        <DataState
          loading={inbound.loading}
          error={inbound.error}
          empty={awaiting.length === 0}
          emptyTitle="Nothing is waiting to go out"
          emptyHint="Items you register arrive here until they are distributed and acknowledged."
          onRetry={inbound.refetch}
        >
          <DataTable
            columns={columns}
            rows={awaiting}
            getRowId={(row) => row.id}
            onRowClick={(row) => navigate(dispatchPaths.itemDetail(row.id))}
          />
        </DataState>
      </section>

      <section className="space-y-3">
        <h2 className="text-lg font-semibold text-slate-800">All inbound at this site</h2>
        <DataState
          loading={inbound.loading}
          error={inbound.error}
          empty={rows.length === 0}
          emptyTitle="No inbound items at this site"
          onRetry={inbound.refetch}
        >
          <DataTable
            columns={columns}
            rows={rows}
            getRowId={(row) => row.id}
            onRowClick={(row) => navigate(dispatchPaths.itemDetail(row.id))}
          />
        </DataState>
      </section>
    </div>
  );
};

export default MailroomPage;
