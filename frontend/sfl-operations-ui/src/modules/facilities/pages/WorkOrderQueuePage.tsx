import { useState } from 'react';
import { useNavigate } from 'react-router';
import DataState from 'shared/components/DataState';
import DataTable, { Column } from 'shared/components/DataTable';
import FilterBar from 'shared/components/FilterBar';
import PageHeader from 'shared/components/PageHeader';
import SiteSelect, { defaultSite } from 'shared/components/SiteSelect';
import StatusChip from 'shared/components/StatusChip';
import { SelectInput } from 'shared/components/fields';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { facilitiesPaths } from 'shared/layout/navigation';
import type { WorkOrder } from '../api/dto';
import type { WorkOrderStatus } from '../api/enums';
import { workOrderStatuses } from '../api/enums';
import { listVendors, searchWorkOrders } from '../api/facilitiesApi';
import {
  formatDateTime,
  heldFor,
  humaniseCode,
  orDash,
  overdueBy,
  priorityTone,
  workOrderStatusTone,
} from '../components/facilitiesFormat';

/**
 * The work-order queue — SRS-SFL-S153-02.
 *
 * ## Overdue first, and why the sort is not a preference
 *
 * The service returns the queue newest-first. This screen re-sorts it so that anything past its SLA
 * is at the top, ordered by how late it is. That is the only ordering that matches what the queue is
 * for: a supervisor opening it at eight in the morning needs the overdue work in the first screen,
 * not on page three behind everything raised overnight.
 *
 * Sorting here rather than asking the service to do it is deliberate — `overdue` and
 * `minutesOverdue` both come down the wire already computed, so this is arranging the answer rather
 * than recomputing it.
 *
 * ## What a vendor sees
 *
 * A contractor sees only the work assigned to them. That is enforced per record by the service, on
 * reads and writes alike, so this screen shows the assignee filter to everybody and simply receives
 * a shorter list — offering a filter that implied otherwise would be the shell contradicting the
 * service.
 */
const WorkOrderQueuePage = () => {
  const navigate = useNavigate();

  const [siteCode, setSiteCode] = useState<string>(defaultSite);
  const [status, setStatus] = useState<string>('');
  const [vendorId, setVendorId] = useState<string>('');
  const [openOnly, setOpenOnly] = useState(true);

  const orders = useApiQuery(
    (signal) =>
      searchWorkOrders(
        {
          siteCode: siteCode || undefined,
          status: (status || undefined) as WorkOrderStatus | undefined,
          vendorId: vendorId || undefined,
          openOnly: openOnly || undefined,
          limit: 200,
        },
        signal,
      ),
    [siteCode, status, vendorId, openOnly],
  );

  const vendors = useApiQuery(
    (signal) => listVendors(siteCode || undefined, signal),
    [siteCode],
  );

  /** Overdue first, worst first within that; everything else keeps the service's order. */
  const rows = [...(orders.data ?? [])].sort((a, b) => {
    if (a.overdue !== b.overdue) {
      return a.overdue ? -1 : 1;
    }
    return (b.minutesOverdue ?? 0) - (a.minutesOverdue ?? 0);
  });

  const overdueCount = rows.filter((order) => order.overdue).length;

  const columns: Column<WorkOrder>[] = [
    {
      key: 'workOrderNumber',
      header: 'Work order',
      width: 170,
      cell: (order) => <span className="font-medium text-gray-900">{order.workOrderNumber}</span>,
    },
    { key: 'title', header: 'Work', cell: (order) => order.title },
    {
      key: 'workOrderType',
      header: 'Type',
      width: 120,
      hideBelowLg: true,
      cell: (order) => humaniseCode(order.workOrderType),
    },
    {
      key: 'assignedTo',
      header: 'Assigned to',
      width: 150,
      cell: (order) => orDash(order.assignedTo),
    },
    {
      key: 'priority',
      header: 'Priority',
      width: 110,
      cell: (order) => (
        <StatusChip value={humaniseCode(order.priority)} tone={priorityTone(order.priority)} />
      ),
    },
    {
      key: 'status',
      header: 'Status',
      width: 150,
      cell: (order) => (
        <div className="flex flex-col gap-0.5">
          <StatusChip
            value={humaniseCode(order.status)}
            tone={workOrderStatusTone(order.status)}
          />
          {order.status === 'ON_HOLD' && order.totalHeldSeconds > 0 && (
            <span className="text-theme-xs text-gray-500">{heldFor(order.totalHeldSeconds)}</span>
          )}
        </div>
      ),
    },
    {
      key: 'slaDueAt',
      header: 'SLA',
      width: 190,
      cell: (order) =>
        order.overdue ? (
          <span className="text-theme-xs font-medium text-error-600">
            {overdueBy(order.minutesOverdue)}
            {order.escalationLevel > 0 ? ` · level ${order.escalationLevel}` : ''}
          </span>
        ) : (
          <span className="text-theme-xs text-gray-600">{formatDateTime(order.slaDueAt)}</span>
        ),
    },
  ];

  return (
    <>
      <PageHeader
        title="Work orders"
        subtitle={
          overdueCount > 0
            ? `${overdueCount} of ${rows.length} past their SLA`
            : 'What is booked, who has it, and what is late'
        }
        crumbs={[{ label: 'Facilities', to: facilitiesPaths.dashboard }, { label: 'Work orders' }]}
      />

      <FilterBar>
        <SiteSelect value={siteCode} onChange={setSiteCode} />
        <SelectInput
          label="Status"
          value={status}
          onChange={setStatus}
          allowEmpty
          emptyLabel="Any status"
          options={workOrderStatuses.map((value) => ({ value, label: humaniseCode(value) }))}
        />
        <SelectInput
          label="Vendor"
          value={vendorId}
          onChange={setVendorId}
          allowEmpty
          emptyLabel="Any vendor"
          options={(vendors.data ?? []).map((vendor) => ({
            value: vendor.id,
            label: vendor.name,
          }))}
        />
        <SelectInput
          label="Show"
          value={openOnly ? 'open' : 'all'}
          onChange={(value) => setOpenOnly(value === 'open')}
          options={[
            { value: 'open', label: 'Outstanding only' },
            { value: 'all', label: 'Everything' },
          ]}
        />
      </FilterBar>

      <DataState
        loading={orders.loading}
        error={orders.error}
        empty={rows.length === 0}
        emptyTitle={openOnly ? 'Nothing outstanding' : 'No work orders'}
        emptyHint={
          // Deliberately does not claim to know *why* the list is empty. A contractor sees only the
          // work assigned to them, so "everything here is closed" would be a confident falsehood on
          // a site with a full queue they simply cannot see.
          openOnly
            ? 'Nothing outstanding is visible to you. Switch to Everything to include closed and cancelled work.'
            : 'Nothing here is visible to you. Work orders are raised against a reported fault, or generated by a preventive schedule — and a contractor sees only the ones assigned to them.'
        }
        onRetry={orders.refetch}
      >
        <DataTable
          rows={rows}
          columns={columns}
          getRowId={(order) => order.id}
          onRowClick={(order) => navigate(facilitiesPaths.workOrderDetail(order.id))}
        />
      </DataState>
    </>
  );
};

export default WorkOrderQueuePage;
