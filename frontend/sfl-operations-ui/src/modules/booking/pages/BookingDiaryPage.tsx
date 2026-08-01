import { useState } from 'react';
import { useNavigate } from 'react-router';
import Button from 'shared/components/Button';
import DataState from 'shared/components/DataState';
import DataTable, { CellStack, Column } from 'shared/components/DataTable';
import FilterBar from 'shared/components/FilterBar';
import PageHeader from 'shared/components/PageHeader';
import FacetFilter from 'shared/components/FacetFilter';
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
  const [status, setStatus] = useState<string[]>([]);
  const [purpose, setPurpose] = useState<string[]>([]);
  const [scope, setScope] = useState('');

  const bookings = useApiQuery(
    (signal) =>
      bookingsApi.search(
        {
          siteCode: siteCode || undefined,
          /*
            One value goes to the service; the rest are applied to the returned set below.

            `BookingQuery` takes a single status and a single purpose, so a multi-select cannot be
            pushed down whole. Sending the first narrows the fetch — which matters, the register is
            capped at 200 — and the remainder is filtered here. That is a compromise and it is worth
            naming: with more than one value selected the cap applies to a *wider* set than the
            filter shows, so a very large site could clip. Widening the query to accept a list is the
            real fix and belongs in the service.
          */
          status: (status[0] as BookingStatus) || undefined,
          purpose: (purpose[0] as BookingPurpose) || undefined,
          liveOnly: scope === 'live' ? true : undefined,
          onReadinessHold: scope === 'held' ? true : undefined,
          limit: 200,
        },
        signal,
      ),
    [siteCode, status, purpose, scope],
  );

  const fetched = bookings.data ?? [];
  const rows = fetched.filter(
    (booking) =>
      (status.length === 0 || status.includes(booking.status)) &&
      (purpose.length === 0 || purpose.includes(booking.purpose)),
  );
  const filtered = status.length > 0 || purpose.length > 0 || Boolean(scope);

  /*
    Counts come from what the service returned, so they describe the data in hand rather than the
    whole register. A count that claimed to be the site total would be a promise this screen cannot
    keep — the fetch is capped and a requester's view is narrowed per record.
  */
  const countBy = (pick: (booking: Booking) => string) =>
    fetched.reduce<Record<string, number>>((tally, booking) => {
      const key = pick(booking);
      tally[key] = (tally[key] ?? 0) + 1;
      return tally;
    }, {});
  const statusCounts = countBy((booking) => booking.status);
  const purposeCounts = countBy((booking) => booking.purpose);

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
          setStatus([]);
          setPurpose([]);
          setScope('');
        }}
        resetDisabled={!filtered}
      >
        <SiteSelect value={siteCode} onChange={setSiteCode} allowEmpty emptyLabel="All sites" />
        <FacetFilter
          label="Status"
          selected={status}
          onChange={setStatus}
          options={BOOKING_STATUSES.map((value) => ({
            value,
            label: humaniseCode(value),
            count: statusCounts[value] ?? 0,
          }))}
        />
        <FacetFilter
          label="Purpose"
          selected={purpose}
          onChange={setPurpose}
          options={BOOKING_PURPOSES.map((value) => ({
            value,
            label: humaniseCode(value),
            count: purposeCounts[value] ?? 0,
          }))}
        />
        <FacetFilter
          label="Holding"
          selected={scope ? [scope] : []}
          // Single-valued by nature: "holding a space" and "on readiness hold" are different
          // questions, and selecting both would mean neither.
          onChange={(next) => setScope(next[next.length - 1] ?? '')}
          options={[
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
