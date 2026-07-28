import { useState } from 'react';
import { Link as RouterLink, useNavigate, useParams } from 'react-router';
import { Alert, Box, Button, Divider, Stack, Typography } from '@mui/material';
import { TripResponse } from 'modules/fleet/api/dto';
import { humanise } from 'modules/fleet/api/enums';
import { driversApi, tripsApi, vehiclesApi } from 'modules/fleet/api/fleetApi';
import {
  AssignTripDialog,
  CancelTripDialog,
  CloseTripDialog,
  HoldTripDialog,
  RecordInspectionDialog,
  StartTripDialog,
} from 'modules/fleet/dialogs/tripDialogs';
import DataState from 'shared/components/DataState';
import KeyValueGrid from 'shared/components/KeyValueGrid';
import { useNotifier } from 'shared/components/Notifier';
import PageHeader from 'shared/components/PageHeader';
import SectionCard from 'shared/components/SectionCard';
import StatusChip from 'shared/components/StatusChip';
import WorkflowTimeline, { TimelineEntry } from 'shared/components/WorkflowTimeline';
import { formatDateTime, formatNumber, formatOdometer } from 'shared/components/format';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { fleetPaths } from 'shared/layout/navigation';
import IconifyIcon from 'components/base/IconifyIcon';

type DialogKey = 'assign' | 'start' | 'close' | 'cancel' | 'hold' | 'resume' | 'inspection' | null;

/** Which transitions the trip's current status permits — mirrors the service's transition policy. */
const permitted = (trip: TripResponse) => ({
  assign: ['PLANNED', 'ASSIGNED', 'ON_HOLD'].includes(trip.status),
  start: trip.status === 'ASSIGNED',
  hold: ['ASSIGNED', 'IN_PROGRESS'].includes(trip.status),
  resume: trip.status === 'ON_HOLD',
  close: trip.status === 'IN_PROGRESS',
  cancel: ['PLANNED', 'ASSIGNED', 'ON_HOLD'].includes(trip.status),
  inspect: ['PLANNED', 'ASSIGNED', 'IN_PROGRESS'].includes(trip.status),
});

/**
 * Trip detail — the workflow surface.
 *
 * Buttons are shown only for transitions the current status allows, so an operator is never
 * offered an action the service is certain to reject. The service remains the authority: an
 * attempted transition that the policy refuses still surfaces its error.
 */
const TripDetailPage = () => {
  const { tripId = '' } = useParams();
  const navigate = useNavigate();
  const { notifySuccess } = useNotifier();
  const [dialog, setDialog] = useState<DialogKey>(null);

  const trip = useApiQuery((signal) => tripsApi.findById(tripId, signal), [tripId]);
  const inspections = useApiQuery((signal) => tripsApi.inspections(tripId, signal), [tripId]);

  const vehicleId = trip.data?.vehicleId ?? '';
  const driverId = trip.data?.driverId ?? '';

  const vehicle = useApiQuery(
    (signal) => (vehicleId ? vehiclesApi.findById(vehicleId, signal) : Promise.resolve(undefined)),
    [vehicleId],
  );
  const driver = useApiQuery(
    (signal) => (driverId ? driversApi.findById(driverId, signal) : Promise.resolve(undefined)),
    [driverId],
  );

  const refreshAll = () => {
    trip.refetch();
    inspections.refetch();
    vehicle.refetch();
  };

  const timeline: TimelineEntry[] = trip.data
    ? [
        {
          id: 'created',
          title: 'Trip planned',
          detail: `${trip.data.origin} → ${trip.data.destination}`,
          actor: trip.data.createdBy,
          occurredAt: trip.data.createdAt,
        },
        ...(trip.data.actualStart
          ? [
              {
                id: 'started',
                title: 'Trip started',
                detail:
                  trip.data.startOdometer !== null
                    ? `Start odometer ${formatNumber(trip.data.startOdometer)} km`
                    : null,
                occurredAt: trip.data.actualStart,
                tone: 'accent' as const,
              },
            ]
          : []),
        ...(trip.data.holdReason
          ? [
              {
                id: 'held',
                title: 'Placed on hold',
                detail: trip.data.holdReason,
                occurredAt: trip.data.lastModifiedAt ?? trip.data.createdAt,
                tone: 'danger' as const,
              },
            ]
          : []),
        ...(trip.data.actualEnd
          ? [
              {
                id: 'closed',
                title: 'Trip closed',
                detail: trip.data.closureReason,
                occurredAt: trip.data.actualEnd,
              },
            ]
          : []),
        ...(trip.data.cancellationReason
          ? [
              {
                id: 'cancelled',
                title: 'Trip cancelled',
                detail: trip.data.cancellationReason,
                occurredAt: trip.data.lastModifiedAt ?? trip.data.createdAt,
                tone: 'danger' as const,
              },
            ]
          : []),
      ]
    : [];

  return (
    <Box>
      <PageHeader
        title={trip.data?.tripNumber ?? 'Trip'}
        subtitle={
          trip.data
            ? `${trip.data.origin} → ${trip.data.destination} · ${trip.data.purpose}`
            : undefined
        }
        crumbs={[
          { label: 'Fleet', to: fleetPaths.dashboard },
          { label: 'Trips', to: fleetPaths.trips },
          { label: trip.data?.tripNumber ?? '…' },
        ]}
        actions={
          <Button
            variant="soft"
            color="neutral"
            onClick={() => navigate(fleetPaths.trips)}
            startIcon={<IconifyIcon icon="material-symbols:arrow-back-rounded" />}
          >
            Trip queue
          </Button>
        }
        meta={
          trip.data && (
            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
              <StatusChip value={trip.data.status} />
              <StatusChip value={trip.data.operatingMode} tone="neutral" />
              <StatusChip value={trip.data.siteCode} label={trip.data.siteCode} tone="neutral" />
            </Stack>
          )
        }
      />

      <DataState
        loading={trip.initialising}
        error={trip.error}
        onRetry={trip.refetch}
        minHeight={320}
      >
        {trip.data && (
          <Stack spacing={2.5}>
            <SectionCard title="Actions" subtitle="Transitions permitted from the current status">
              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                {permitted(trip.data).assign && (
                  <Button variant="contained" color="secondary" onClick={() => setDialog('assign')}>
                    {trip.data.vehicleId ? 'Reassign' : 'Assign vehicle & driver'}
                  </Button>
                )}
                {permitted(trip.data).inspect && (
                  <Button variant="soft" color="neutral" onClick={() => setDialog('inspection')}>
                    Record inspection
                  </Button>
                )}
                {permitted(trip.data).start && (
                  <Button variant="soft" color="secondary" onClick={() => setDialog('start')}>
                    Start trip
                  </Button>
                )}
                {permitted(trip.data).hold && (
                  <Button variant="soft" color="neutral" onClick={() => setDialog('hold')}>
                    Place on hold
                  </Button>
                )}
                {permitted(trip.data).resume && (
                  <Button variant="soft" color="neutral" onClick={() => setDialog('resume')}>
                    Resume
                  </Button>
                )}
                {permitted(trip.data).close && (
                  <Button variant="contained" color="secondary" onClick={() => setDialog('close')}>
                    Close trip
                  </Button>
                )}
                {permitted(trip.data).cancel && (
                  <Button variant="soft" color="error" onClick={() => setDialog('cancel')}>
                    Cancel trip
                  </Button>
                )}
                {['COMPLETED', 'CANCELLED'].includes(trip.data.status) && (
                  <Typography variant="body2" color="text.secondary">
                    This trip is {humanise(trip.data.status).toLowerCase()}. Its record is now
                    read-only history.
                  </Typography>
                )}
              </Stack>
            </SectionCard>

            <Box
              sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', lg: '1.4fr 1fr' } }}
            >
              <SectionCard title="Trip record">
                <Stack spacing={2.5}>
                  <KeyValueGrid
                    items={[
                      { label: 'Trip number', value: trip.data.tripNumber },
                      { label: 'Operating mode', value: humanise(trip.data.operatingMode) },
                      { label: 'Site', value: trip.data.siteCode },
                      { label: 'Purpose', value: trip.data.purpose, span: 2 },
                      { label: 'Origin', value: trip.data.origin },
                      { label: 'Destination', value: trip.data.destination },
                      { label: 'Planned start', value: formatDateTime(trip.data.plannedStart) },
                      { label: 'Planned end', value: formatDateTime(trip.data.plannedEnd) },
                      { label: 'Actual start', value: formatDateTime(trip.data.actualStart) },
                      { label: 'Actual end', value: formatDateTime(trip.data.actualEnd) },
                      { label: 'Start odometer', value: formatNumber(trip.data.startOdometer) },
                      { label: 'End odometer', value: formatNumber(trip.data.endOdometer) },
                      {
                        label: 'Distance covered',
                        value: formatOdometer(trip.data.distanceCovered),
                      },
                      { label: 'Record version', value: trip.data.version },
                    ]}
                  />

                  {(trip.data.holdReason ||
                    trip.data.closureReason ||
                    trip.data.cancellationReason) && (
                    <>
                      <Divider />
                      <KeyValueGrid
                        columns={2}
                        items={[
                          ...(trip.data.holdReason
                            ? [
                                {
                                  label: 'Hold reason',
                                  value: trip.data.holdReason,
                                  span: 2 as const,
                                },
                              ]
                            : []),
                          ...(trip.data.closureReason
                            ? [
                                {
                                  label: 'Closure reason',
                                  value: trip.data.closureReason,
                                  span: 2 as const,
                                },
                              ]
                            : []),
                          ...(trip.data.closureEvidenceId
                            ? [
                                {
                                  label: 'Closure evidence',
                                  value: trip.data.closureEvidenceId,
                                  span: 2 as const,
                                },
                              ]
                            : []),
                          ...(trip.data.cancellationReason
                            ? [
                                {
                                  label: 'Cancellation reason',
                                  value: trip.data.cancellationReason,
                                  span: 2 as const,
                                },
                              ]
                            : []),
                        ]}
                      />
                    </>
                  )}
                </Stack>
              </SectionCard>

              <Stack spacing={2}>
                <SectionCard title="Assignment">
                  {trip.data.vehicleId || trip.data.driverId ? (
                    <Stack spacing={1.5}>
                      {vehicle.data && (
                        <Stack
                          component={RouterLink}
                          to={fleetPaths.vehicleDetail(vehicle.data.id)}
                          spacing={0.25}
                          sx={{
                            textDecoration: 'none',
                            color: 'inherit',
                            p: 1.5,
                            border: 1,
                            borderColor: 'divider',
                            borderRadius: 1.5,
                            '&:hover': { borderColor: 'secondary.main' },
                          }}
                        >
                          <Typography variant="caption" color="text.secondary">
                            Vehicle
                          </Typography>
                          <Typography variant="body2" fontWeight={700}>
                            {vehicle.data.registrationNumber}
                          </Typography>
                          <Typography variant="caption" color="text.secondary">
                            {vehicle.data.make} {vehicle.data.model} ·{' '}
                            {formatOdometer(vehicle.data.odometerValue, vehicle.data.odometerUnit)}
                          </Typography>
                        </Stack>
                      )}
                      {driver.data && (
                        <Stack
                          component={RouterLink}
                          to={fleetPaths.driverDetail(driver.data.id)}
                          spacing={0.25}
                          sx={{
                            textDecoration: 'none',
                            color: 'inherit',
                            p: 1.5,
                            border: 1,
                            borderColor: 'divider',
                            borderRadius: 1.5,
                            '&:hover': { borderColor: 'secondary.main' },
                          }}
                        >
                          <Typography variant="caption" color="text.secondary">
                            Driver
                          </Typography>
                          <Typography variant="body2" fontWeight={700}>
                            {driver.data.displayName}
                          </Typography>
                          <Typography variant="caption" color="text.secondary">
                            Class {driver.data.licenceClass} ·{' '}
                            {humanise(driver.data.eligibilityStatus)}
                          </Typography>
                        </Stack>
                      )}
                    </Stack>
                  ) : (
                    <Alert severity="warning" variant="outlined">
                      No vehicle or driver assigned yet. The trip cannot start until both are set.
                    </Alert>
                  )}
                </SectionCard>

                <SectionCard title="History">
                  <WorkflowTimeline entries={timeline} />
                </SectionCard>
              </Stack>
            </Box>

            <SectionCard
              title="Inspections"
              subtitle="Pre-trip and post-trip checks recorded against this trip"
            >
              <DataState
                loading={inspections.initialising}
                error={inspections.error}
                empty={(inspections.data?.length ?? 0) === 0}
                emptyTitle="No inspections recorded"
                emptyHint="A valid pre-trip inspection is required before the trip can start."
                onRetry={inspections.refetch}
                minHeight={140}
              >
                <Stack spacing={1.5}>
                  {inspections.data?.map((inspection) => (
                    <Box
                      key={inspection.id}
                      sx={{ p: 1.75, border: 1, borderColor: 'divider', borderRadius: 1.5 }}
                    >
                      <Stack
                        direction={{ xs: 'column', sm: 'row' }}
                        justifyContent="space-between"
                        spacing={1}
                      >
                        <Box sx={{ minWidth: 0 }}>
                          <Stack
                            direction="row"
                            spacing={1}
                            alignItems="center"
                            flexWrap="wrap"
                            useFlexGap
                          >
                            <Typography variant="body2" fontWeight={700}>
                              {humanise(inspection.inspectionType)}
                            </Typography>
                            <StatusChip value={inspection.result} />
                            <StatusChip value={inspection.status} />
                          </Stack>
                          <Typography variant="caption" color="text.secondary">
                            {formatDateTime(inspection.performedAt)} ·{' '}
                            {inspection.performedBy ?? 'unknown operator'} ·{' '}
                            {formatNumber(inspection.odometerReading)} km
                          </Typography>
                        </Box>
                        {!inspection.permitsUse && (
                          <StatusChip value="BLOCKED" label="Blocks vehicle use" tone="blocked" />
                        )}
                      </Stack>

                      {inspection.findings.length > 0 && (
                        <Stack spacing={0.75} sx={{ mt: 1.25 }}>
                          {inspection.findings.map((finding) => (
                            <Stack
                              key={`${inspection.id}-${finding.checkCode}`}
                              direction="row"
                              spacing={1}
                              alignItems="center"
                              flexWrap="wrap"
                              useFlexGap
                            >
                              <StatusChip value={finding.severity} />
                              <Typography variant="body2">
                                <strong>{finding.checkCode}</strong> — {finding.description}
                              </Typography>
                              {finding.resolved && (
                                <StatusChip value="RESOLVED" label="Resolved" tone="ready" />
                              )}
                            </Stack>
                          ))}
                        </Stack>
                      )}

                      {inspection.hasOpenCriticalDefect && (
                        <Alert severity="error" sx={{ mt: 1.25 }}>
                          An unresolved critical defect is recorded — the vehicle is blocked from
                          use until it is cleared.
                        </Alert>
                      )}

                      {inspection.notes && (
                        <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                          {inspection.notes}
                        </Typography>
                      )}
                    </Box>
                  ))}
                </Stack>
              </DataState>
            </SectionCard>

            <AssignTripDialog
              open={dialog === 'assign'}
              trip={trip.data}
              onClose={() => setDialog(null)}
              onSaved={() => {
                notifySuccess('Trip assignment updated.');
                refreshAll();
              }}
            />
            <StartTripDialog
              open={dialog === 'start'}
              trip={trip.data}
              vehicleOdometer={vehicle.data?.odometerValue}
              onClose={() => setDialog(null)}
              onSaved={() => {
                notifySuccess('Trip started.');
                refreshAll();
              }}
            />
            <CloseTripDialog
              open={dialog === 'close'}
              trip={trip.data}
              onClose={() => setDialog(null)}
              onSaved={() => {
                notifySuccess('Trip closed.');
                refreshAll();
              }}
            />
            <CancelTripDialog
              open={dialog === 'cancel'}
              trip={trip.data}
              onClose={() => setDialog(null)}
              onSaved={() => {
                notifySuccess('Trip cancelled.');
                refreshAll();
              }}
            />
            <HoldTripDialog
              open={dialog === 'hold' || dialog === 'resume'}
              action={dialog === 'resume' ? 'RESUME' : 'HOLD'}
              trip={trip.data}
              onClose={() => setDialog(null)}
              onSaved={() => {
                notifySuccess(dialog === 'resume' ? 'Trip resumed.' : 'Trip placed on hold.');
                refreshAll();
              }}
            />
            <RecordInspectionDialog
              open={dialog === 'inspection'}
              trip={trip.data}
              vehicleOdometer={vehicle.data?.odometerValue}
              onClose={() => setDialog(null)}
              onSaved={() => {
                notifySuccess('Inspection recorded.');
                refreshAll();
              }}
            />
          </Stack>
        )}
      </DataState>
    </Box>
  );
};

export default TripDetailPage;
