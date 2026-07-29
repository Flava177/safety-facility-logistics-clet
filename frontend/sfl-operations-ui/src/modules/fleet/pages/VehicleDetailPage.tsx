import { useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { TripResponse } from 'modules/fleet/api/dto';
import { humanise } from 'modules/fleet/api/enums';
import { tripsApi, vehiclesApi } from 'modules/fleet/api/fleetApi';
import {
  ChangeVehicleLifecycleDialog,
  CorrectOdometerDialog,
  EditVehicleDialog,
  RecordServiceDialog,
  RegisterComplianceDocumentDialog,
} from 'modules/fleet/dialogs/vehicleDialogs';
import Alert from 'shared/components/Alert';
import BlockerList from 'shared/components/BlockerList';
import Button from 'shared/components/Button';
import DataState from 'shared/components/DataState';
import DataTable, { CellStack, Column } from 'shared/components/DataTable';
import KeyValueGrid from 'shared/components/KeyValueGrid';
import { useNotifier } from 'shared/components/Notifier';
import PageHeader from 'shared/components/PageHeader';
import SectionCard from 'shared/components/SectionCard';
import StatusChip from 'shared/components/StatusChip';
import Tabs from 'shared/components/Tabs';
import { cn } from 'shared/components/cn';
import {
  formatDate,
  formatDateTime,
  formatDaysRemaining,
  formatNumber,
  formatOdometer,
} from 'shared/components/format';
import { VehicleLocationResponse } from 'modules/fleet/api/dto';
import { RecordStandaloneInspectionDialog } from 'modules/fleet/dialogs/inspectionDialogs';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { fleetPaths } from 'shared/layout/navigation';

type TabKey = 'overview' | 'compliance' | 'service' | 'trips' | 'movement';

/** A record row inside a tab panel — bordered, two columns, wraps on narrow viewports. */
const recordRow =
  'flex flex-col justify-between gap-3 rounded-xl border border-gray-200 p-3 sm:flex-row';

/**
 * Vehicle detail.
 *
 * Readiness comes from `GET /vehicles/{id}/readiness` — the same `FleetReadinessService` policy the
 * assignment itself runs, now with a door of its own. It used to be fetched through
 * `trips/assignment-preview` with only a `vehicleId`, which gave the right answer through an endpoint
 * shaped for a question nobody was asking here.
 *
 * Movement is a **vendor projection**, so the panel shows `recordedAt` on every row and does not
 * decide on the reader's behalf how stale is too stale — that depends on what is being asked.
 */
const VehicleDetailPage = () => {
  const { vehicleId = '' } = useParams();
  const navigate = useNavigate();
  const { notifySuccess } = useNotifier();
  const [tab, setTab] = useState<TabKey>('overview');
  const [dialog, setDialog] = useState<
    'edit' | 'lifecycle' | 'compliance' | 'service' | 'odometer' | 'inspection' | null
  >(null);

  const vehicle = useApiQuery((signal) => vehiclesApi.findById(vehicleId, signal), [vehicleId]);
  const readiness = useApiQuery((signal) => vehiclesApi.readiness(vehicleId, signal), [vehicleId]);
  const movement = useApiQuery((signal) => vehiclesApi.movement(vehicleId, 25, signal), [vehicleId]);
  const compliance = useApiQuery(
    (signal) => vehiclesApi.complianceDocuments(vehicleId, signal),
    [vehicleId],
  );
  const service = useApiQuery(
    (signal) => vehiclesApi.serviceHistory(vehicleId, signal),
    [vehicleId],
  );
  const trips = useApiQuery(
    (signal) => tripsApi.search({ vehicleId, size: 10 }, signal),
    [vehicleId],
  );

  /**
   * Movement snapshots.
   *
   * Coordinates are shown to five decimal places — about a metre, which is finer than any fleet
   * telematics feed is honest to and coarse enough not to imply survey accuracy. `recordedAt` leads
   * the row because it is the only thing that says whether the position still means anything.
   */
  const movementColumns = useMemo<Column<VehicleLocationResponse>[]>(
    () => [
      {
        key: 'recordedAt',
        header: 'Recorded',
        width: 180,
        cell: (row) => (
          <CellStack
            primary={formatDateTime(row.recordedAt)}
            secondary={row.sourceSystem ?? 'source not recorded'}
          />
        ),
      },
      {
        key: 'position',
        header: 'Position',
        width: 200,
        cell: (row) =>
          row.latitude !== null && row.longitude !== null ? (
            <span className="font-mono text-theme-xs">
              {row.latitude.toFixed(5)}, {row.longitude.toFixed(5)}
            </span>
          ) : (
            <span className="text-gray-500">Not reported</span>
          ),
      },
      {
        key: 'odometer',
        header: 'Odometer',
        width: 130,
        align: 'right',
        cell: (row) =>
          row.odometerValue === null ? (
            <span className="text-gray-500">—</span>
          ) : (
            formatOdometer(row.odometerValue)
          ),
      },
      {
        key: 'correlation',
        header: 'Correlation',
        width: 160,
        hideBelowLg: true,
        cell: (row) => (
          <span className="font-mono text-theme-xs text-gray-600">
            {row.correlationId ? row.correlationId.slice(0, 8) : '—'}
          </span>
        ),
      },
    ],
    [],
  );

  const refreshAll = () => {
    vehicle.refetch();
    readiness.refetch();
    movement.refetch();
    compliance.refetch();
    service.refetch();
  };

  const tripColumns = useMemo<Column<TripResponse>[]>(
    () => [
      {
        key: 'trip',
        header: 'Trip',
        width: 260,
        cell: (row) => (
          <CellStack primary={row.tripNumber} secondary={`${row.origin} → ${row.destination}`} />
        ),
      },
      {
        key: 'plannedStart',
        header: 'Planned start',
        width: 200,
        cell: (row) => <CellStack primary={formatDateTime(row.plannedStart)} secondary={row.purpose} />,
      },
      {
        key: 'status',
        header: 'Status',
        width: 130,
        cell: (row) => <StatusChip value={row.status} />,
      },
    ],
    [],
  );

  return (
    <div>
      <PageHeader
        title={vehicle.data?.registrationNumber ?? 'Vehicle'}
        subtitle={
          vehicle.data
            ? `${vehicle.data.make} ${vehicle.data.model} · ${humanise(vehicle.data.category)} · ${vehicle.data.siteCode}`
            : undefined
        }
        crumbs={[
          { label: 'Fleet', to: fleetPaths.dashboard },
          { label: 'Vehicle register', to: fleetPaths.vehicles },
          { label: vehicle.data?.registrationNumber ?? '…' },
        ]}
        actions={
          <>
            <Button
              variant="outline"
              startIcon="arrow-left"
              onClick={() => navigate(fleetPaths.vehicles)}
            >
              Register
            </Button>
            <Button variant="outline" startIcon="gauge" onClick={() => setDialog('odometer')}>
              Correct odometer
            </Button>
            <Button variant="outline" startIcon="activity" onClick={() => setDialog('lifecycle')}>
              Lifecycle
            </Button>
            <Button variant="primary" startIcon="edit" onClick={() => setDialog('edit')}>
              Edit
            </Button>
          </>
        }
        meta={
          vehicle.data && (
            <div className="flex flex-wrap items-center gap-2">
              <StatusChip value={vehicle.data.lifecycleStatus} />
              <StatusChip value={vehicle.data.serviceStatus} />
              <StatusChip value={vehicle.data.availabilityStatus} />
              {readiness.data && <StatusChip value={readiness.data.status} />}
            </div>
          )
        }
      />

      <DataState
        loading={vehicle.initialising}
        error={vehicle.error}
        onRetry={vehicle.refetch}
        minHeight={320}
      >
        {vehicle.data && (
          <div className="space-y-5">
            <SectionCard
              title="Readiness"
              subtitle="Assessed with the same policy the assignment will use"
              actions={
                <>
                  <Button
                    size="sm"
                    variant="outline"
                    startIcon="shield-check"
                    onClick={() => setDialog('inspection')}
                  >
                    Record inspection
                  </Button>
                  <Button size="sm" variant="ghost" startIcon="refresh" onClick={readiness.refetch}>
                    Re-assess
                  </Button>
                </>
              }
            >
              <DataState
                loading={readiness.initialising}
                error={readiness.error}
                onRetry={readiness.refetch}
                minHeight={80}
              >
                {readiness.data && (
                  <div className="space-y-3">
                    <div className="flex flex-wrap items-center gap-2">
                      <StatusChip value={readiness.data.status} />
                      <span className="text-theme-xs text-gray-500">
                        Assessed {formatDateTime(readiness.data.assessedAt)}
                      </span>
                    </div>
                    <BlockerList
                      blockers={readiness.data.blockers}
                      clearMessage="No readiness blockers. This vehicle can be assigned."
                    />
                  </div>
                )}
              </DataState>
            </SectionCard>

            <SectionCard flush>
              <Tabs
                items={[
                  { value: 'overview', label: 'Overview' },
                  { value: 'compliance', label: 'Compliance', count: compliance.data?.length },
                  { value: 'service', label: 'Service history', count: service.data?.history.length },
                  { value: 'trips', label: 'Trips', count: trips.data?.content.length },
                  { value: 'movement', label: 'Movement', count: movement.data?.length },
                ]}
                value={tab}
                onChange={(value) => setTab(value as TabKey)}
                className="px-2"
              />

              <div className="p-5">
                {tab === 'overview' && (
                  <div className="space-y-5">
                    <KeyValueGrid
                      items={[
                        { label: 'Registration number', value: vehicle.data.registrationNumber },
                        {
                          label: 'VIN',
                          value: vehicle.data.vin ?? '—',
                          masked: vehicle.data.vinMasked,
                        },
                        { label: 'Category', value: humanise(vehicle.data.category) },
                        { label: 'Capacity', value: `${vehicle.data.capacity} seats` },
                        { label: 'Site', value: vehicle.data.siteCode },
                        { label: 'Responsible unit', value: vehicle.data.responsibleUnit },
                        { label: 'Operational owner', value: vehicle.data.operationalOwner },
                        {
                          label: 'Acquisition reference',
                          value: vehicle.data.acquisitionReference ?? '—',
                        },
                        {
                          label: 'Odometer',
                          value: formatOdometer(
                            vehicle.data.odometerValue,
                            vehicle.data.odometerUnit,
                          ),
                        },
                        {
                          label: 'Odometer source',
                          value: humanise(vehicle.data.odometerSource),
                        },
                        {
                          label: 'Odometer recorded',
                          value: formatDateTime(vehicle.data.odometerRecordedAt),
                        },
                        {
                          label: 'Operating modes',
                          value: (vehicle.data.allowedOperatingModes ?? [])
                            .map(humanise)
                            .join(', '),
                        },
                        {
                          label: 'Emergency only',
                          value: vehicle.data.emergencyOnly ? 'Yes' : 'No',
                        },
                        {
                          label: 'Current trip',
                          value: vehicle.data.currentTripId ? (
                            <Link
                              to={fleetPaths.tripDetail(vehicle.data.currentTripId)}
                              className="text-brand-500 transition hover:text-brand-700 hover:underline"
                            >
                              Open trip
                            </Link>
                          ) : (
                            '—'
                          ),
                        },
                        { label: 'Record version', value: vehicle.data.version },
                      ]}
                    />
                    <div className="h-px bg-gray-200" />
                    <KeyValueGrid
                      columns={4}
                      items={[
                        { label: 'Created by', value: vehicle.data.createdBy ?? '—' },
                        { label: 'Created at', value: formatDateTime(vehicle.data.createdAt) },
                        { label: 'Last modified by', value: vehicle.data.lastModifiedBy ?? '—' },
                        {
                          label: 'Last modified at',
                          value: formatDateTime(vehicle.data.lastModifiedAt),
                        },
                      ]}
                    />
                  </div>
                )}

                {tab === 'compliance' && (
                  <div className="space-y-4">
                    <div className="flex justify-end">
                      <Button
                        size="sm"
                        variant="accent"
                        startIcon="plus"
                        onClick={() => setDialog('compliance')}
                      >
                        Register document
                      </Button>
                    </div>
                    <DataState
                      loading={compliance.initialising}
                      error={compliance.error}
                      empty={(compliance.data?.length ?? 0) === 0}
                      emptyTitle="No compliance documents"
                      emptyHint="Mandatory documents are missing — the vehicle carries a blocking readiness blocker until they are registered."
                      onRetry={compliance.refetch}
                      minHeight={160}
                    >
                      <div className="space-y-2.5">
                        {compliance.data?.map((document) => (
                          <div key={document.id} className={recordRow}>
                            <div className="min-w-0">
                              <div className="flex flex-wrap items-center gap-2">
                                <p className="text-theme-sm font-semibold text-gray-800">
                                  {humanise(document.documentType)}
                                </p>
                                {document.mandatory && (
                                  <StatusChip value="MANDATORY" label="Mandatory" tone="accent" />
                                )}
                              </div>
                              <p className="mt-0.5 text-theme-xs text-gray-500">
                                {document.documentReference} · {document.issuingAuthority} · issued{' '}
                                {formatDate(document.issuedOn)}
                              </p>
                            </div>
                            <div className="flex shrink-0 items-center gap-3">
                              <div className="sm:text-right">
                                <p className="text-theme-sm font-medium text-gray-700">
                                  {formatDate(document.expiresOn)}
                                </p>
                                <p
                                  className={cn(
                                    'text-theme-xs',
                                    document.daysUntilExpiry < 0
                                      ? 'text-error-600'
                                      : document.daysUntilExpiry < 30
                                        ? 'text-warning-600'
                                        : 'text-gray-500',
                                  )}
                                >
                                  {formatDaysRemaining(document.daysUntilExpiry)}
                                </p>
                              </div>
                              <StatusChip value={document.status} />
                            </div>
                          </div>
                        ))}
                      </div>
                    </DataState>
                  </div>
                )}

                {tab === 'service' && (
                  <div className="space-y-4">
                    <div className="flex justify-end">
                      <Button
                        size="sm"
                        variant="accent"
                        startIcon="plus"
                        onClick={() => setDialog('service')}
                      >
                        Record service
                      </Button>
                    </div>
                    <DataState
                      loading={service.initialising}
                      error={service.error}
                      onRetry={service.refetch}
                      minHeight={160}
                    >
                      {service.data && (
                        <div className="space-y-4">
                          <KeyValueGrid
                            columns={4}
                            items={[
                              {
                                label: 'Current service status',
                                value: <StatusChip value={service.data.currentServiceStatus} />,
                              },
                              { label: 'Next due on', value: formatDate(service.data.nextDueOn) },
                              {
                                label: 'Next due odometer',
                                value: formatNumber(service.data.nextDueOdometer),
                              },
                              {
                                label: 'Current odometer',
                                value: formatNumber(service.data.currentOdometer),
                              },
                            ]}
                          />
                          <div className="h-px bg-gray-200" />
                          {service.data.history.length === 0 ? (
                            <p className="text-theme-sm text-gray-500">
                              No service events recorded yet.
                            </p>
                          ) : (
                            <div className="space-y-2.5">
                              {service.data.history.map((record) => (
                                <div key={record.id} className={recordRow}>
                                  <div className="min-w-0">
                                    <p className="text-theme-sm font-semibold text-gray-800">
                                      {humanise(record.serviceType)} ·{' '}
                                      {formatDate(record.performedOn)}
                                    </p>
                                    <p className="mt-0.5 text-theme-xs text-gray-500">
                                      {record.workSummary}
                                    </p>
                                  </div>
                                  <div className="flex shrink-0 items-center gap-3">
                                    <span className="text-theme-xs text-gray-500">
                                      {formatNumber(record.odometerAtService)} km
                                    </span>
                                    <StatusChip value={record.outcome} />
                                  </div>
                                </div>
                              ))}
                            </div>
                          )}
                        </div>
                      )}
                    </DataState>
                  </div>
                )}

                {tab === 'trips' && (
                  <DataState
                    loading={trips.initialising}
                    error={trips.error}
                    empty={(trips.data?.content.length ?? 0) === 0}
                    emptyTitle="No trips recorded"
                    emptyHint="This vehicle has not been assigned to a trip."
                    onRetry={trips.refetch}
                    minHeight={160}
                  >
                    <div className="-mx-5">
                      <DataTable
                        rows={trips.data?.content ?? []}
                        columns={tripColumns}
                        getRowId={(row) => row.id}
                        onRowClick={(row) => navigate(fleetPaths.tripDetail(row.id))}
                        dense
                      />
                      {/* The query asks for ten rows, so the panel is a recent extract, not the whole history. */}
                      <p className="px-5 pt-3 text-theme-xs text-gray-500">
                        The ten most recent trips for this vehicle. Use the trip queue for the full
                        history.
                      </p>
                    </div>
                  </DataState>
                )}
                {tab === 'movement' && (
                  <DataState
                    loading={movement.initialising}
                    error={movement.error}
                    empty={(movement.data?.length ?? 0) === 0}
                    emptyTitle="No movement recorded"
                    emptyHint="No telematics provider has reported a position for this vehicle."
                    onRetry={movement.refetch}
                    minHeight={160}
                  >
                    <div className="-mx-5">
                      <DataTable
                        rows={movement.data ?? []}
                        columns={movementColumns}
                        getRowId={(row) => row.id}
                        dense
                      />
                      <p className="px-5 pt-3 text-theme-xs text-gray-500">
                        The twenty-five most recent snapshots. This is a vendor projection — SFL
                        records what a telematics provider reported and when, and does not correct
                        it. Judge freshness from the recorded time: a position from last week is not
                        wrong, it is just old.
                      </p>
                    </div>
                  </DataState>
                )}
              </div>
            </SectionCard>

            {vehicle.data.lifecycleStatus === 'ARCHIVED' && (
              <Alert variant="info">
                Archived records are immutable outside an authorised restoration workflow. Edits
                will be refused with FLEET_ARCHIVED_RECORD_IMMUTABLE.
              </Alert>
            )}

            {/*
             * Mounted only while open. These forms are seeded from the vehicle record, and a
             * dialog that stays mounted keeps the values it was first given — so after a save and
             * refetch the edit form would still be offering the superseded make, capacity and
             * odometer back to the service.
             */}
            {/*
              * A periodic inspection needs no trip, which is exactly why the action lives here on
              * the vehicle rather than only on a trip. Recording one can change readiness — a
              * critical finding takes the vehicle out of service — so the readiness card refetches.
              */}
            {dialog === 'inspection' && (
              <RecordStandaloneInspectionDialog
                open
                vehicle={vehicle.data}
                onClose={() => setDialog(null)}
                onSaved={() => {
                  notifySuccess(
                    'Inspection recorded.',
                    'Readiness has been re-assessed against it.',
                  );
                  refreshAll();
                }}
              />
            )}

            {dialog === 'edit' && (
              <EditVehicleDialog
                open
                vehicle={vehicle.data}
                onClose={() => setDialog(null)}
                onSaved={() => {
                  notifySuccess('Vehicle updated.');
                  refreshAll();
                }}
              />
            )}
            {dialog === 'lifecycle' && (
              <ChangeVehicleLifecycleDialog
                open
                vehicle={vehicle.data}
                onClose={() => setDialog(null)}
                onSaved={() => {
                  notifySuccess('Lifecycle status changed.');
                  refreshAll();
                }}
              />
            )}
            {dialog === 'compliance' && (
              <RegisterComplianceDocumentDialog
                open
                vehicleId={vehicle.data.id}
                onClose={() => setDialog(null)}
                onSaved={() => {
                  notifySuccess('Compliance document registered.');
                  refreshAll();
                }}
              />
            )}
            {dialog === 'service' && (
              <RecordServiceDialog
                open
                vehicleId={vehicle.data.id}
                currentOdometer={vehicle.data.odometerValue}
                onClose={() => setDialog(null)}
                onSaved={() => {
                  notifySuccess('Service event recorded.');
                  refreshAll();
                }}
              />
            )}
            {dialog === 'odometer' && (
              <CorrectOdometerDialog
                open
                vehicle={vehicle.data}
                onClose={() => setDialog(null)}
                onSaved={() => {
                  notifySuccess('Odometer corrected.');
                  refreshAll();
                }}
              />
            )}
          </div>
        )}
      </DataState>
    </div>
  );
};

export default VehicleDetailPage;
