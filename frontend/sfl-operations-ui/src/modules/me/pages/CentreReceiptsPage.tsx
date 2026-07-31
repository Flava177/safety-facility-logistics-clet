import { useNavigate } from 'react-router';
import Alert from 'shared/components/Alert';
import DataState from 'shared/components/DataState';
import DataTable, { Column } from 'shared/components/DataTable';
import PageHeader from 'shared/components/PageHeader';
import StatusChip from 'shared/components/StatusChip';
import { defaultSite } from 'shared/components/SiteSelect';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { dispatchPaths } from 'shared/layout/navigation';
import { manifestsApi } from 'modules/dispatch/api/dispatchApi';
import type { DispatchManifest } from 'modules/dispatch/api/dto';

/**
 * The centre manager's receipts — S171's destination persona.
 *
 * ## A portal that cannot yet keep the promise its name makes
 *
 * `CENTRE_MANAGER` has **no §2.3 user class**; it was added additively under S171 decision D-14 for
 * the person at an examination centre who signs for a consignment. That is a real job, so the screen
 * is built — but it is a **Deviation**, and its limitation is stated on the page rather than buried
 * in a report.
 *
 * `Dispatch.destinationCentre` and `assignedHandler` are `VARCHAR(200)` free text supplied at
 * creation, with no relationship to a principal. There is nothing to narrow on. A rule built on them
 * would hold whenever somebody happened to type an actor id into the field and fail silently
 * otherwise, which is worse than no rule because it looks like enforcement. So this lists
 * consignments **at this site**, says so twice, and does not claim to be "my centre".
 *
 * Closing it needs a principal-bound centre reference on a dispatch — a schema change and an
 * identity decision for the Transportation & Logistics Unit. Recorded as C-16 in
 * `docs/fleet/S166_Gap_And_Conflict_Report.md`.
 */
const CentreReceiptsPage = () => {
  const navigate = useNavigate();
  const site = defaultSite;

  const manifests = useApiQuery(
    (signal) => manifestsApi.search({ siteCode: site, size: 50 }, signal),
    [site],
  );

  const rows = manifests.data?.content ?? [];
  const inbound = rows.filter((row) => row.status === 'IN_TRANSIT' || row.status === 'DISPATCHED');

  const columns: Column<DispatchManifest>[] = [
    { key: 'manifestNumber', header: 'Manifest', cell: (row) => row.manifestNumber },
    { key: 'destinationCentre', header: 'Destination', cell: (row) => row.destinationCentre ?? '—' },
    { key: 'route', header: 'Route', cell: (row) => row.route ?? '—' },
    { key: 'itemCount', header: 'Items', cell: (row) => String(row.itemCount) },
    { key: 'status', header: 'Status', cell: (row) => <StatusChip value={row.status} /> },
  ];

  const notice =
    'A dispatch records its destination centre as free text, with nothing tying it to your account, '
    + 'so the platform cannot yet tell which consignments are yours. Everything at '
    + site
    + ' is shown. Narrowing needs a schema change owned by the Transportation and Logistics Unit — '
    + 'see C-16 in the S166 gap report.';

  return (
    <div className="space-y-8">
      <PageHeader
        title="Centre receipts"
        subtitle={'Consignments at ' + site + ' — confirm receipt, record a variance, chase returns'}
      />

      <Alert variant="info" title="This list is not narrowed to your centre">
        {notice}
      </Alert>

      <section className="space-y-3">
        <h2 className="text-lg font-semibold text-slate-800">On the way</h2>
        <DataState
          loading={manifests.loading}
          error={manifests.error}
          empty={inbound.length === 0}
          emptyTitle="Nothing is in transit to this site"
          emptyHint="Consignments appear here once they are sealed and despatched."
          onRetry={manifests.refetch}
        >
          <DataTable
            columns={columns}
            rows={inbound}
            getRowId={(row) => row.id}
            onRowClick={(row) => navigate(dispatchPaths.manifestDetail(row.id))}
          />
        </DataState>
      </section>

      <section className="space-y-3">
        <h2 className="text-lg font-semibold text-slate-800">All consignments at this site</h2>
        <DataState
          loading={manifests.loading}
          error={manifests.error}
          empty={rows.length === 0}
          emptyTitle="No consignments at this site"
          onRetry={manifests.refetch}
        >
          <DataTable
            columns={columns}
            rows={rows}
            getRowId={(row) => row.id}
            onRowClick={(row) => navigate(dispatchPaths.manifestDetail(row.id))}
          />
        </DataState>
      </section>
    </div>
  );
};

export default CentreReceiptsPage;
