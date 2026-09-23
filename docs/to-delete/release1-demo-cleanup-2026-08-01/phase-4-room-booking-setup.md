# Phase 4 Room Booking Setup

## What Phase 4 Adds

Phase 4 adds the Room and Resource Booking vertical slice under the IFIMP fast-track systems.

Implemented scope:

- Room booking domain model.
- Booking lifecycle: Submitted, Approved, Rejected, Cancelled and Completed.
- Room/resource conflict checking.
- Audit and outbox hooks.
- Workflow hooks for approval and room setup tasks.
- PostgreSQL-ready persistence model.
- API endpoints under `/api/v1/ifimp/room-bookings`.
- Mobile endpoints under `/api/mobile/v1/ifimp/room-bookings`.
- Portal pages:
  - `/room-bookings`
  - `/room-bookings/create`
  - `/room-bookings/{id}`
  - `/room-bookings/approvals`

## Database Migration

Run this from Visual Studio Package Manager Console after pulling the Phase 4 code:

```powershell
Add-Migration AddRoomBookingPersistence -Context SflDbContext -OutputDir Migrations
Update-Database -Context SflDbContext
```

This creates the `ifimp.room_bookings` table and updates the EF model snapshot.

## Demo Flow

1. Start `SFL.Api`.
2. Start `SFL.WebPortal.Razor`.
3. Sign in using seeded credentials.
4. Open `Room Booking`.
5. Create a booking request.
6. Open `Room Booking Approvals`.
7. Approve the booking.
8. Open the booking details and mark it complete.

## Architecture Notes

This slice follows the same pattern as CMMS:

- Domain rules stay in `SFL.IFIMP`.
- PostgreSQL mapping stays in `SFL.Infrastructure.Persistence`.
- API controllers stay in `SFL.Api`.
- Mobile routes stay in `SFL.MobileApi`.
- Portal calls go through `MaintenanceApiClient`.
- Events are named through `SflIntegrationEventNames`.

This makes Room Booking a second reference slice for the remaining 13 fast-track systems.
