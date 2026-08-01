import { useState } from 'react';
import { useNavigate } from 'react-router';
import Button from 'shared/components/Button';
import DataState from 'shared/components/DataState';
import DataTable, { CellStack, Column } from 'shared/components/DataTable';
import FilterBar from 'shared/components/FilterBar';
import PageHeader from 'shared/components/PageHeader';
import Select from 'shared/components/Select';
import SiteSelect, { defaultSite } from 'shared/components/SiteSelect';
import StatusChip from 'shared/components/StatusChip';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { bookingPaths } from 'shared/layout/navigation';
import { humaniseCode, orDash } from 'modules/facilities/components/facilitiesFormat';
import { bookingsApi } from '../api/bookingApi';
import type { Booking } from '../api/dto';
import { BOOKING_PURPOSES, BOOKING_STATUSES, HOLD_REASON_DESCRIPTIONS } from '../api/enums';
import type { BookingPurpose, BookingStatus } from '../api/enums';
import { canRequest } from '../api/workflow';
import { bookingStatusTone, formatWindow } from '../components/bookingFormat';

/**
 * The diary — SRS-SFL-S159-01.
 *
 * The register S159 has had an API for since it shipped and no screen at all.
 *
 * **A requester sees a shorter list, and this screen does nothing to make that happen.**
 * `BookingApplicationService.requesterFilter` narrows per record, on reads and on writes, so the
 * register arrives already narrowed. There is deliberately no client-side filter: one would be a
 * display convention, and the rows would still have crossed the boundary.
 *
 * **The readiness hold is a column, not a status.** S159 decided that deliberately — a confirmed
 * booking on a hall blocked on Tuesday is still a confirmed booking somebody has in their diary, and
 * moving it to an `AT_RISK` state would decide on the estate's behalf that Tuesday's leak will still
 * be there on Friday. So the status says what the booking is and the hold says what the estate
 * currently thinks of it, side by side.
 */
const BookingDiaryPage = () => {
  const navigate = useNavigate();
  const [siteCode, setSiteCode] = useState(defaultSite);
  const [status, setStatus] = useState('');
  const [purpose, setPurpose] = useState('');
  const [scope, setScope] = useState('');

  const bookings = useApiQuery(
    (signal) =>
      bookingsApi.search(
        {
          siteCode: siteCode || undefined,
          status: (status as BookingStatus) || undefined,
          purpose: (purpose as BookingPurpose) || undefined,
          liveOnly: scope === 'live' ? true : undefined,
          onReadinessHold: scope === 'held' ? true : undefined,
          limit: 200,
        },
        signal,
      ),
    [siteCode, status, purpose, scope],
  );

  const rows = bookings.data ?? [];
  const filtered = Boolean(status || purpose || scope);

  const columns: Column<Booking>[] = [
    {
      key: 'title',
      header: 'Booking',
      width: 260,
      cell: (booking) => (
        <CellStack primary={booking.title} secondary={booking.bookingReference} />
      ),
    },
    {
      key: 'room',
      header: 'Space',
      width: 130,
      cell: (booking) => orDash(booking.roomCode),
    },
    {
      key: 'window',
      header: 'When',
      width: 200,
      cell: (booking) => (
        <CellStack
          primary={formatWindow(booking.startsAt, booking.endsAt)}
          // The occupied window, not the booked one — it is what the next requester is refused on.
          secondary={
            booking.setupMinutes > 0 || booking.teardownMinutes > 0
              ? `Holds ${formatWindow(booking.occupiedFrom, booking.occupiedTo)}`
              : undefined
          }
        />
      ),
    },
    {
      key: 'purpose',
      header: 'Purpose',
      hideBelowLg: true,
      cell: (booking) => humaniseCode(booking.purpose),
    },
    {
      key: 'requestedBy',
      header: 'Requested by',
      hideBelowLg: true,
      cell: (booking) => booking.requestedBy,
    },
    {
      key: 'status',
      header: 'Status',
      width: 130,
      cell: (booking) => (
        <StatusChip value={booking.status} tone={bookingStatusTone(booking.status)} />
      ),
    },
    {
      key: 'hold',
      header: 'Readiness',
      width: 130,
      align: 'right',
      cell: (booking) =>
        booking.readinessHoldReason ? (
          <span title={HOLD_REASON_DESCRIPTIONS[booking.readinessHoldReason]}>
            <StatusChip value="ON_HOLD" label="On hold" tone="blocked" />
          </span>
        ) : (
          <span className="text-gray-400">—</span>
        ),
    },
  ];

  return (
    <>
      <PageHeader
        title="Booking diary"
        subtitle="Rooms and resources booked at this site, and what the estate currently thinks of each"
        actions={
          canRequest().kind === 'allowed' ? (
            <Button startIcon="calendar" onClick={() => navigate(bookingPaths.availability)}>
              Find a space
            </Button>
          ) : undefined
        }
      />

      <FilterBar
        onReset={() => {
          setStatus('');
          setPurpose('');
          setScope('');
        }}
        resetDisabled={!filtered}
      >
        <SiteSelect value={siteCode} onChange={setSiteCode} allowEmpty emptyLabel="All sites" />
        <Select
          value={status}
          onChange={setStatus}
          placeholder="Any status"
          options={[
            { value: '', label: 'Any status' },
            ...BOOKING_STATUSES.map((value) => ({ value, label: humaniseCode(value) })),
          ]}
        />
        <Select
          value={purpose}
          onChange={setPurpose}
          placeholder="Any purpose"
          options={[
            { value: '', label: 'Any purpose' },
            ...BOOKING_PURPOSES.map((value) => ({ value, label: humaniseCode(value) })),
          ]}
        />
        <Select
          value={scope}
          onChange={setScope}
          placeholder="Everything"
          options={[
            { value: '', label: 'Everything' },
            { value: 'live', label: 'Holding a space' },
            { value: 'held', label: 'On readiness hold' },
          ]}
        />
      </FilterBar>

      <DataState
        loading={bookings.loading}
        error={bookings.error}
        empty={rows.length === 0}
        emptyTitle="No bookings match"
        /*
          Describes what is visible to *you*. A requester sees only the bookings they raised, so
          "this site has no bookings" is a claim this screen is not in a position to make.
        */
        emptyHint="Nothing visible to you matches these filters."
        onRetry={bookings.refetch}
      >
        <DataTable
          rows={rows}
          columns={columns}
          getRowId={(booking) => booking.id}
          onRowClick={(booking) => navigate(bookingPaths.bookingDetail(booking.id))}
          caption="Bookings"
        />
      </DataState>
    </>
  );
};

export default BookingDiaryPage;
