import { useNavigate } from 'react-router';
import DataState from 'shared/components/DataState';
import DataTable, { Column } from 'shared/components/DataTable';
import PageHeader from 'shared/components/PageHeader';
import StatusChip from 'shared/components/StatusChip';
import { defaultSite } from 'shared/components/SiteSelect';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { facilitiesPaths } from 'shared/layout/navigation';
import { searchWorkOrders } from 'modules/facilities/api/facilitiesApi';
import type { WorkOrder } from 'modules/facilities/api/dto';

/**
 * The technician's and the vendor's queue — SRS §2.3 "Maintenance Technician / Vendor".
 *
 * ## The narrowing is the service's, and it is per record
 *
 * `WorkOrderApplicationService.assertVisible` refuses a work order not assigned to the actor, on
 * reads and writes alike, and `vendorFilter` narrows the list in the same breath. So this screen
 * asks for the queue and receives only what is theirs — including by id, which is the part an empty
 * list cannot prove.
 *
 * S153 recorded the reasoning: "the real boundary is **assignment**... because 'the ones assigned to
 * me' is not something a matrix can say". A vendor firm with three technicians sees three disjoint
 * queues; that is the stricter reading and the deliberate one.
 *
 * ## Why this exists when the work-order queue already does
 *
 * The operator queue is the whole site's, sorted overdue-first, with assignment controls. That is
 * the supervisor's screen and it is the wrong landing for somebody whose question is "what am I
 * doing today". Same data, same service, different first paragraph.
 */
const MyQueuePage = () => {
  const navigate = useNavigate();
  const site = defaultSite;

  const orders = useApiQuery(
    (signal) => searchWorkOrders({ siteCode: site }, signal),
    [site],
  );

  const rows = orders.data ?? [];

  const columns: Column<WorkOrder>[] = [
    { key: 'workOrderNumber', header: 'Job', cell: (row) => row.workOrderNumber },
    { key: 'title', header: 'What', cell: (row) => row.title },
    { key: 'locationCode', header: 'Where', cell: (row) => row.locationCode ?? '—' },
    { key: 'priority', header: 'Priority', cell: (row) => <StatusChip value={row.priority} /> },
    { key: 'status', header: 'Status', cell: (row) => <StatusChip value={row.status} /> },
    {
      key: 'overdue',
      header: 'SLA',
      // `overdue` comes down the wire. A browser deciding for itself what is late would disagree
      // with the escalation sweep the moment a workstation clock drifted — and the sweep is the one
      // that notifies people.
      cell: (row) => (row.overdue ? <StatusChip value="OVERDUE" tone="blocked" /> : '—'),
    },
  ];

  return (
    <div className="space-y-6">
      <PageHeader title="My work queue" subtitle="The jobs assigned to you" />
      <DataState
        loading={orders.loading}
        error={orders.error}
        empty={rows.length === 0}
        emptyTitle="Nothing is assigned to you"
        // Never "every job at this site is closed" — this queue is yours, and a contractor who sees
        // only their own has no way to know what else exists.
        emptyHint="Work assigned to you appears here. It does not show anybody else's."
        onRetry={orders.refetch}
      >
        <DataTable
          columns={columns}
          rows={rows}
          getRowId={(row) => row.id}
          onRowClick={(row) => navigate(facilitiesPaths.workOrderDetail(row.id))}
        />
      </DataState>
    </div>
  );
};

export default MyQueuePage;
