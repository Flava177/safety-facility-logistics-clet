import { useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import Alert from 'shared/components/Alert';
import Button from 'shared/components/Button';
import DataState from 'shared/components/DataState';
import DataTable, { Column } from 'shared/components/DataTable';
import KeyValueGrid from 'shared/components/KeyValueGrid';
import PageHeader from 'shared/components/PageHeader';
import SectionCard from 'shared/components/SectionCard';
import StatCard from 'shared/components/StatCard';
import StatusChip from 'shared/components/StatusChip';
import { useNotifier } from 'shared/components/Notifier';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { bookingPaths, facilitiesPaths } from 'shared/layout/navigation';
import {
  formatDateTime,
  humaniseCode,
  orDash,
  relativeTime,
} from 'modules/facilities/components/facilitiesFormat';
import { bookingsApi } from '../api/bookingApi';
import type { BookingAllocation, SetupTask } from '../api/dto';
import { HOLD_REASON_DESCRIPTIONS } from '../api/enums';
import {
  canCancel,
  canComplete,
  canDecide,
  canReschedule,
  canStart,
  isOwnBooking,
} from '../api/workflow';
import ControlButton from '../components/ControlButton';
import {
  bookingStatusTone,
  bufferSummary,
  formatWindow,
  setupTaskTone,
} from '../components/bookingFormat';
import CancelBookingDialog from '../dialogs/CancelBookingDialog';
import CompleteBookingDialog from '../dialogs/CompleteBookingDialog';
import DecideBookingDialog from '../dialogs/DecideBookingDialog';
import RescheduleBookingDialog from '../dialogs/RescheduleBookingDialog';

type OpenDialog = 'decide' | 'reschedule' | 'cancel' | 'complete' | null;

/**
 * One booking, and everything that decides what happens to it.
 *
 * Four things on one screen, because somebody looking at a booking is deciding what to do about it:
 * where it stands, what the estate currently thinks of the space, what it is holding, and what has to
 * happen to the room before it starts.
 *
 * ## The readiness notice is the point of the screen
 *
 * A confirmed booking on a hall blocked on Tuesday is still confirmed and still in somebody's diary.
 * S159 marks it rather than cancelling it, deliberately — moving it to an `AT_RISK` state would
 * decide on the estate's behalf that Tuesday's leak will still be there on Friday. So the notice says
 * what is wrong and leaves the decision to a person, which is what the flag is for.
 *
 * ## Start is not ceremony
 *
 * Marking a booking in use is what stops the no-show sweep releasing the space. That is worth saying
 * on the button's own page, because "we were in the room, why did it release?" is otherwise a
 * reasonable question with an invisible answer.
 */
const BookingDetailPage = () => {
  const { bookingId = '' } = useParams();
  const navigate = useNavigate();
  const notify = useNotifier();
  const [dialog, setDialog] = useState<OpenDialog>(null);

  const booking = useApiQuery((signal) => bookingsApi.findById(bookingId, signal), [bookingId]);
  const approvals = useApiQuery((signal) => bookingsApi.approvals(bookingId, signal), [bookingId]);
  const allocations = useApiQuery(
    (signal) => bookingsApi.allocations(bookingId, signal),
    [bookingId],
  );
  const setupTasks = useApiQuery((signal) => bookingsApi.setupTasks(bookingId, signal), [bookingId]);

  const refreshAll = () => {
    booking.refetch();
    approvals.refetch();
    allocations.refetch();
    setupTasks.refetch();
  };

  const record = booking.data;

  const start = async () => {
    if (!record) {
      return;
    }
    try {
      await bookingsApi.start(bookingId, { expectedVersion: record.metadata.version });
      notify.notifySuccess('Marked in use. The no-show sweep will leave it alone.');
      refreshAll();
    } catch (cause) {
      notify.notifyError(cause);
    }
  };

  const allocationColumns: Column<BookingAllocation>[] = [
    { key: 'resource', header: 'Resource', cell: (row) => orDash(row.resourceCode) },
    { key: 'quantity', header: 'Quantity', align: 'right', width: 100, cell: (row) => row.quantity },
    {
      key: 'exclusive',
      header: 'Exclusive',
      width: 120,
      hideBelowLg: true,
      // Worth its own column: an exclusive resource is refused by the database rather than by
      // arithmetic, which is a materially stronger guarantee than "there are three of them".
      cell: (row) => (row.exclusive ? <StatusChip value="EXCLUSIVE" tone="accent" /> : '—'),
    },
    {
      key: 'released',
      header: 'Standing',
      width: 130,
      cell: (row) =>
        row.released ? (
          <StatusChip value="RELEASED" tone="neutral" />
        ) : (
          <StatusChip value="HELD" tone="active" />
        ),
    },
    {
      key: 'allocatedAt',
      header: 'Allocated',
      hideBelowLg: true,
      cell: (row) => `${relativeTime(row.allocatedAt)} by ${row.allocatedBy}`,
    },
  ];

  const setupColumns: Column<SetupTask>[] = [
    { key: 'description', header: 'Task', cell: (row) => row.description },
    {
      key: 'dueBy',
      header: 'Needed by',
      width: 180,
      cell: (row) => (
        <span className={row.overdue ? 'font-medium text-error-800' : undefined}>
          {row.dueBy ? formatDateTime(row.dueBy) : '—'}
        </span>
      ),
    },
    { key: 'assignedTo', header: 'Assigned', hideBelowLg: true, cell: (row) => orDash(row.assignedTo) },
    {
      key: 'status',
      header: 'Status',
      width: 120,
      cell: (row) => <StatusChip value={row.status} tone={setupTaskTone(row.status)} />,
    },
  ];

  return (
    <>
      <DataState
        loading={booking.loading}
        error={booking.error}
        onRetry={booking.refetch}
        minHeight={280}
      >
        {record && (
          <>
            <PageHeader
              title={record.title}
              subtitle={`${record.bookingReference} · ${orDash(record.roomCode)} · ${record.siteCode}`}
              crumbs={[
                { label: 'Bookings', to: bookingPaths.diary },
                { label: record.bookingReference },
              ]}
              meta={
                <div className="flex flex-wrap items-center gap-2">
                  <StatusChip
                    value={record.status}
                    tone={bookingStatusTone(record.status)}
                    size="md"
                  />
                  {/* Beside the status, never instead of it — the two say different things. */}
                  {record.readinessHoldReason && (
                    <StatusChip value="ON_HOLD" label="Readiness hold" tone="blocked" size="md" />
                  )}
                  {record.overridden && (
                    <StatusChip value="OVERRIDDEN" label="Overridden" tone="accent" size="md" />
                  )}
                </div>
              }
              actions={
                <div className="flex flex-wrap gap-2">
                  <ControlButton state={canDecide(record)} onClick={() => setDialog('decide')}>
                    Decide
                  </ControlButton>
                  <ControlButton state={canStart(record)} variant="outline" onClick={start}>
                    Mark in use
                  </ControlButton>
                  <ControlButton
                    state={canComplete(record)}
                    variant="outline"
                    onClick={() => setDialog('complete')}
                  >
                    Complete
                  </ControlButton>
                  <ControlButton
                    state={canReschedule(record)}
                    variant="outline"
                    onClick={() => setDialog('reschedule')}
                  >
                    Move
                  </ControlButton>
                  <ControlButton
                    state={canCancel(record)}
                    variant="outline"
                    onClick={() => setDialog('cancel')}
                  >
                    {isOwnBooking(record) ? 'Withdraw' : 'Cancel'}
                  </ControlButton>
                </div>
              }
            />

            <div className="space-y-5">
              {record.readinessHoldReason && (
                <Alert variant="warning" title="The estate has a problem with this space">
                  <p className="text-theme-sm">
                    {HOLD_REASON_DESCRIPTIONS[record.readinessHoldReason]} Held since{' '}
                    {formatDateTime(record.readinessHeldAt)}.
                  </p>
                  <p className="mt-2 text-theme-sm">
                    The booking is still {humaniseCode(record.status).toLowerCase()} and still in
                    everybody&rsquo;s diary — the hold marks it rather than cancelling it, because
                    whether the space is fixed by then is a judgement for a person. Move it or cancel
                    it if it cannot go ahead.
                  </p>
                </Alert>
              )}

              {record.overridden && (
                <Alert variant="warning" title="Booked into a space readiness refused">
                  <p className="text-theme-sm">{record.overrideReason}</p>
                </Alert>
              )}

              {record.status === 'NO_SHOW' && (
                <Alert variant="error" title="Nobody turned up">
                  <p className="text-theme-sm">
                    The window closed with no attendance recorded, so the sweep released the space and
                    everything it was holding. Marking a booking in use when it starts is what
                    prevents this.
                  </p>
                </Alert>
              )}

              <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
                <StatCard
                  label="Status"
                  value={humaniseCode(record.status)}
                  caption={
                    record.holdsTheSpace
                      ? 'Currently holding the space'
                      : 'Not holding the space'
                  }
                  tone={
                    record.status === 'NO_SHOW'
                      ? 'critical'
                      : record.holdsTheSpace
                        ? 'good'
                        : 'neutral'
                  }
                  icon="calendar"
                />
                <StatCard
                  label="Booked window"
                  value={formatWindow(record.startsAt, record.endsAt)}
                  caption={bufferSummary(record) ?? 'No setup or teardown buffer'}
                  icon="clock"
                />
                <StatCard
                  label="Occupied window"
                  value={formatWindow(record.occupiedFrom, record.occupiedTo)}
                  // The one figure that surprises people. This, not the booked window, is what the
                  // exclusion constraint tests and what the next requester is refused on.
                  caption="What the next requester is refused on"
                  icon="lock"
                />
                <StatCard
                  label="Approval"
                  value={
                    record.approvalRequired
                      ? record.approvalId
                        ? 'Decided'
                        : 'Awaiting a decision'
                      : 'Not needed'
                  }
                  caption={
                    record.approvalRequired
                      ? record.confirmedAt
                        ? `Confirmed ${relativeTime(record.confirmedAt)}`
                        : 'The space is held until it is decided'
                      : 'Confirmed on request by this site’s configuration'
                  }
                  tone={
                    record.approvalRequired && !record.approvalId && record.status === 'REQUESTED'
                      ? 'caution'
                      : 'neutral'
                  }
                  icon="check-circle"
                />
              </div>

              {record.description && (
                <SectionCard title="Notes">
                  <p className="whitespace-pre-line text-theme-sm text-gray-800">
                    {record.description}
                  </p>
                </SectionCard>
              )}

              {record.closureReason && (
                <SectionCard
                  title={
                    record.status === 'COMPLETED'
                      ? 'How it finished'
                      : record.status === 'REJECTED'
                        ? 'Why it was refused'
                        : 'Why it was withdrawn'
                  }
                  subtitle={formatDateTime(record.completedAt ?? record.metadata.lastModifiedAt)}
                >
                  <p className="whitespace-pre-line text-theme-sm text-gray-800">
                    {record.closureReason}
                  </p>
                </SectionCard>
              )}

              <SectionCard title="The booking">
                <KeyValueGrid
                  items={[
                    { label: 'Reference', value: record.bookingReference },
                    { label: 'Purpose', value: humaniseCode(record.purpose) },
                    {
                      label: 'Space',
                      value: (
                        <Button
                          variant="ghost"
                          onClick={() => navigate(facilitiesPaths.spaceDetail(record.roomId))}
                        >
                          {orDash(record.roomCode)}
                        </Button>
                      ),
                    },
                    { label: 'Site', value: record.siteCode },
                    { label: 'Expected attendees', value: String(record.expectedAttendees) },
                    { label: 'Requested by', value: record.requestedBy },
                    { label: 'On behalf of', value: orDash(record.requestedFor) },
                    { label: 'Requested', value: formatDateTime(record.requestedAt) },
                    { label: 'Started', value: record.startedAt ? formatDateTime(record.startedAt) : '—' },
                    {
                      label: 'Completed',
                      value: record.completedAt ? formatDateTime(record.completedAt) : '—',
                    },
                    { label: 'Lifecycle', value: humaniseCode(record.lifecycleStatus) },
                    { label: 'Version', value: String(record.metadata.version) },
                  ]}
                />
              </SectionCard>

              <SectionCard
                title="Approval"
                subtitle={
                  record.approvalRequired
                    ? 'Decisions taken on this request'
                    : 'This booking needed no approval, so there is nothing to show'
                }
                flush
              >
                <DataState
                  loading={approvals.loading}
                  error={approvals.error}
                  empty={(approvals.data ?? []).length === 0}
                  emptyTitle={
                    record.approvalRequired ? 'Not yet decided' : 'No approval was needed'
                  }
                  /*
                    The absence of an approval record is itself the statement that none was needed —
                    there is no separate flag that could fall out of step with it.
                  */
                  emptyHint={
                    record.approvalRequired
                      ? 'The space is held until somebody decides.'
                      : 'This site’s configuration confirms this purpose on request.'
                  }
                  minHeight={140}
                  onRetry={approvals.refetch}
                >
                  <ul className="divide-y divide-gray-100">
                    {(approvals.data ?? []).map((approval) => (
                      <li key={approval.id} className="px-5 py-4">
                        <div className="flex flex-wrap items-center gap-2">
                          <StatusChip
                            value={approval.decision}
                            tone={approval.decision === 'APPROVED' ? 'ready' : 'blocked'}
                          />
                          <span className="text-theme-sm text-gray-700">
                            {approval.decidedBy} · {formatDateTime(approval.decidedAt)}
                          </span>
                        </div>
                        {approval.reason && (
                          <p className="mt-2 text-theme-sm text-gray-800">{approval.reason}</p>
                        )}
                      </li>
                    ))}
                  </ul>
                </DataState>
              </SectionCard>

              <SectionCard title="Resources it holds" flush>
                <DataState
                  loading={allocations.loading}
                  error={allocations.error}
                  empty={(allocations.data ?? []).length === 0}
                  emptyTitle="No resources allocated"
                  emptyHint="This booking takes the room and nothing else."
                  minHeight={140}
                  onRetry={allocations.refetch}
                >
                  <DataTable
                    rows={allocations.data ?? []}
                    columns={allocationColumns}
                    getRowId={(row) => row.id}
                    dense
                    caption="Resources allocated to this booking"
                  />
                </DataState>
              </SectionCard>

              <SectionCard
                title="Room turnaround"
                subtitle="Raised automatically for every resource that needs setting up"
                flush
              >
                <DataState
                  loading={setupTasks.loading}
                  error={setupTasks.error}
                  empty={(setupTasks.data ?? []).length === 0}
                  emptyTitle="Nothing to set up"
                  emptyHint="No resource on this booking declares that it needs setting up."
                  minHeight={140}
                  onRetry={setupTasks.refetch}
                >
                  <DataTable
                    rows={setupTasks.data ?? []}
                    columns={setupColumns}
                    getRowId={(row) => row.id}
                    dense
                    onRowClick={() => navigate(bookingPaths.setupTasks)}
                    caption="Setup tasks for this booking"
                  />
                </DataState>
              </SectionCard>
            </div>
          </>
        )}
      </DataState>

      {dialog === 'decide' && record && (
        <DecideBookingDialog
          booking={record}
          onClose={() => setDialog(null)}
          onSubmit={async (body) => {
            const decided = await bookingsApi.decide(bookingId, body);
            setDialog(null);
            notify.notifySuccess(
              decided.status === 'CONFIRMED'
                ? 'Approved and confirmed.'
                : 'Rejected. The space and its resources are released.',
            );
            refreshAll();
          }}
        />
      )}

      {dialog === 'reschedule' && record && (
        <RescheduleBookingDialog
          booking={record}
          onClose={() => setDialog(null)}
          onSubmit={async (body) => {
            await bookingsApi.reschedule(bookingId, body);
            setDialog(null);
            notify.notifySuccess('Moved. Its resources moved with it.');
            refreshAll();
          }}
        />
      )}

      {dialog === 'complete' && record && (
        <CompleteBookingDialog
          booking={record}
          onClose={() => setDialog(null)}
          onSubmit={async (body) => {
            await bookingsApi.complete(bookingId, body);
            setDialog(null);
            notify.notifySuccess('Completed. Everything it was holding is released.');
            refreshAll();
          }}
        />
      )}

      {dialog === 'cancel' && record && (
        <CancelBookingDialog
          booking={record}
          onClose={() => setDialog(null)}
          onSubmit={async (body) => {
            await bookingsApi.cancel(bookingId, body);
            setDialog(null);
            notify.notifySuccess('Cancelled. The space is free again.');
            refreshAll();
          }}
        />
      )}
    </>
  );
};

export default BookingDetailPage;
