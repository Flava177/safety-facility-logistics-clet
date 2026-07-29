import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { DriverResponse } from 'modules/fleet/api/dto';
import { describeDriverEligibility } from 'modules/fleet/api/driverEligibility';
import { VEHICLE_CATEGORIES, VehicleCategory, humanise } from 'modules/fleet/api/enums';
import { driversApi, tripsApi } from 'modules/fleet/api/fleetApi';
import { UpdateDriverDialog } from 'modules/fleet/dialogs/driverDialogs';
import Alert from 'shared/components/Alert';
import BlockerList from 'shared/components/BlockerList';
import Button from 'shared/components/Button';
import DataState from 'shared/components/DataState';
import KeyValueGrid from 'shared/components/KeyValueGrid';
import { useNotifier } from 'shared/components/Notifier';
import PageHeader from 'shared/components/PageHeader';
import SectionCard from 'shared/components/SectionCard';
import StatusChip from 'shared/components/StatusChip';
import { EnumSelect } from 'shared/components/fields';
import { formatDate, formatDateTime, formatDaysRemaining } from 'shared/components/format';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { fleetPaths } from 'shared/layout/navigation';

/**
 * Why this driver's status is what it is, read off the record itself.
 *
 * An eligible driver gets nothing rather than an empty panel: a heading with no findings under it
 * reads as a question the dashboard failed to answer.
 */
const EligibilitySummary = ({ driver }: { driver: DriverResponse }) => {
  const reasons =
    driver.eligibilityStatus === 'ELIGIBLE' ? [] : describeDriverEligibility(driver);
  if (reasons.length === 0) {
    return null;
  }

  const conditional = driver.eligibilityStatus === 'CONDITIONAL';
  return (
    <Alert
      variant={conditional ? 'warning' : 'error'}
      title={conditional ? 'Assignable with conditions' : 'Not eligible for assignment'}
    >
      <ul className="mt-1 list-disc space-y-1 pl-4">
        {reasons.map((reason) => (
          <li key={reason}>{reason}</li>
        ))}
      </ul>
    </Alert>
  );
};

/**
 * Driver detail.
 *
 * The eligibility panel is deliberately parameterised by vehicle category: a class B licence that
 * is fine for a saloon is not fine for a bus, and the service answers that question directly.
 */
const DriverDetailPage = () => {
  const { driverId = '' } = useParams();
  const navigate = useNavigate();
  const { notifySuccess } = useNotifier();
  const [category, setCategory] = useState<VehicleCategory | ''>('');
  const [editOpen, setEditOpen] = useState(false);

  const driver = useApiQuery((signal) => driversApi.findById(driverId, signal), [driverId]);
  const eligibility = useApiQuery(
    (signal) =>
      driversApi.eligibility(driverId, { vehicleCategory: category || undefined }, signal),
    [driverId, category],
  );
  const assignments = useApiQuery(
    (signal) => tripsApi.search({ driverId, size: 10 }, signal),
    [driverId],
  );

  return (
    <div>
      <PageHeader
        title={driver.data?.displayName ?? 'Driver'}
        subtitle={
          driver.data
            ? `${driver.data.staffReference} · ${driver.data.siteCode} · ${driver.data.responsibleUnit}`
            : undefined
        }
        crumbs={[
          { label: 'Fleet', to: fleetPaths.dashboard },
          { label: 'Driver register', to: fleetPaths.drivers },
          { label: driver.data?.displayName ?? '…' },
        ]}
        actions={
          <>
            <Button
              variant="outline"
              startIcon="arrow-left"
              onClick={() => navigate(fleetPaths.drivers)}
            >
              Register
            </Button>
            <Button variant="primary" startIcon="edit" onClick={() => setEditOpen(true)}>
              Update driver
            </Button>
          </>
        }
        meta={
          driver.data && (
            <div className="flex flex-wrap items-center gap-2">
              <StatusChip value={driver.data.lifecycleStatus} />
              <StatusChip value={driver.data.eligibilityStatus} />
              <StatusChip
                value={driver.data.licenceClass}
                label={`Class ${driver.data.licenceClass}`}
                tone="neutral"
              />
            </div>
          )
        }
      />

      <DataState
        loading={driver.initialising}
        error={driver.error}
        onRetry={driver.refetch}
        minHeight={300}
      >
        {driver.data && (
          <div className="space-y-5">
            <EligibilitySummary driver={driver.data} />

            <SectionCard
              title="Eligibility"
              subtitle="Blockers the service will apply at assignment time"
              actions={
                <EnumSelect
                  label="Against category"
                  value={category}
                  options={VEHICLE_CATEGORIES}
                  onChange={(value) => setCategory(value)}
                  allowEmpty
                  emptyLabel="Any category"
                  className="w-52"
                />
              }
            >
              <DataState
                loading={eligibility.initialising}
                error={eligibility.error}
                onRetry={eligibility.refetch}
                minHeight={80}
              >
                {eligibility.data && (
                  <div className="space-y-3">
                    <div className="flex flex-wrap items-center gap-2">
                      <StatusChip value={eligibility.data.status} />
                      <span className="text-theme-xs text-gray-500">
                        Assessed {formatDateTime(eligibility.data.assessedAt)}
                        {eligibility.data.assessedForCategory
                          ? ` for ${humanise(eligibility.data.assessedForCategory)}`
                          : ''}
                      </span>
                    </div>
                    <BlockerList
                      blockers={eligibility.data.blockers}
                      clearMessage="No eligibility blockers. This driver can be assigned."
                    />
                  </div>
                )}
              </DataState>
            </SectionCard>

            <SectionCard title="Profile">
              <div className="space-y-5">
                <KeyValueGrid
                  items={[
                    { label: 'Staff reference', value: driver.data.staffReference },
                    {
                      label: 'Licence number',
                      value: driver.data.licenceNumber ?? '—',
                      masked: driver.data.licenceNumberMasked,
                    },
                    { label: 'Licence class', value: `Class ${driver.data.licenceClass}` },
                    {
                      label: 'Licence expires',
                      value: `${formatDate(driver.data.licenceExpiresOn)} · ${formatDaysRemaining(driver.data.daysUntilLicenceExpiry)}`,
                    },
                    {
                      label: 'Medical clearance expires',
                      value: formatDate(driver.data.medicalClearanceExpiresOn),
                    },
                    { label: 'Site', value: driver.data.siteCode },
                    { label: 'Responsible unit', value: driver.data.responsibleUnit },
                    { label: 'Suspension reason', value: driver.data.suspensionReason ?? '—' },
                    { label: 'Record version', value: driver.data.version },
                  ]}
                />
                <hr className="border-gray-200" />
                <KeyValueGrid
                  columns={4}
                  items={[
                    { label: 'Created by', value: driver.data.createdBy ?? '—' },
                    { label: 'Created at', value: formatDateTime(driver.data.createdAt) },
                    { label: 'Last modified by', value: driver.data.lastModifiedBy ?? '—' },
                    {
                      label: 'Last modified at',
                      value: formatDateTime(driver.data.lastModifiedAt),
                    },
                  ]}
                />
              </div>
            </SectionCard>

            <SectionCard
              title="Assignments"
              subtitle="Current and recent trips for this driver"
              actions={
                <Button
                  variant="ghost"
                  size="sm"
                  endIcon="chevron-right"
                  onClick={() => navigate(fleetPaths.trips)}
                >
                  Trip queue
                </Button>
              }
            >
              <DataState
                loading={assignments.initialising}
                error={assignments.error}
                empty={(assignments.data?.content.length ?? 0) === 0}
                emptyTitle="No assignments"
                emptyHint="This driver has not been assigned to a trip."
                onRetry={assignments.refetch}
                minHeight={140}
              >
                <div className="space-y-2.5">
                  {assignments.data?.content.map((trip) => (
                    <Link
                      key={trip.id}
                      to={fleetPaths.tripDetail(trip.id)}
                      className="flex items-center justify-between gap-3 rounded-xl border border-gray-200 p-3 transition hover:border-brand-400 hover:bg-brand-25"
                    >
                      <div className="min-w-0">
                        <p className="truncate text-theme-sm font-semibold text-gray-800">
                          {trip.tripNumber} · {trip.origin} → {trip.destination}
                        </p>
                        <p className="truncate text-theme-xs text-gray-500">
                          {formatDateTime(trip.plannedStart)} → {formatDateTime(trip.plannedEnd)}
                        </p>
                      </div>
                      <StatusChip value={trip.status} />
                    </Link>
                  ))}
                </div>
              </DataState>
            </SectionCard>

            {/* Mounted only while open: the form is seeded from the driver record, and a mounted
                dialog would keep offering the values it was first given after a save and refetch. */}
            {editOpen && (
              <UpdateDriverDialog
                open
                driver={driver.data}
                onClose={() => setEditOpen(false)}
                onSaved={() => {
                  notifySuccess('Driver updated.');
                  driver.refetch();
                  eligibility.refetch();
                }}
              />
            )}
          </div>
        )}
      </DataState>
    </div>
  );
};

export default DriverDetailPage;
