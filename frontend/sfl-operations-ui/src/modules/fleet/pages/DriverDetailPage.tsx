import { useState } from 'react';
import { Link as RouterLink, useNavigate, useParams } from 'react-router';
import { Box, Button, Divider, Stack, Typography } from '@mui/material';
import { VEHICLE_CATEGORIES, VehicleCategory, humanise } from 'modules/fleet/api/enums';
import { driversApi, tripsApi } from 'modules/fleet/api/fleetApi';
import { UpdateDriverDialog } from 'modules/fleet/dialogs/driverDialogs';
import BlockerList from 'shared/components/BlockerList';
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
import IconifyIcon from 'components/base/IconifyIcon';

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
    <Box>
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
              variant="soft"
              color="neutral"
              onClick={() => navigate(fleetPaths.drivers)}
              startIcon={<IconifyIcon icon="material-symbols:arrow-back-rounded" />}
            >
              Register
            </Button>
            <Button
              variant="contained"
              color="secondary"
              onClick={() => setEditOpen(true)}
              startIcon={<IconifyIcon icon="material-symbols:edit-outline-rounded" />}
            >
              Update driver
            </Button>
          </>
        }
        meta={
          driver.data && (
            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
              <StatusChip value={driver.data.lifecycleStatus} />
              <StatusChip value={driver.data.eligibilityStatus} />
              <StatusChip
                value={driver.data.licenceClass}
                label={`Class ${driver.data.licenceClass}`}
                tone="neutral"
              />
            </Stack>
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
          <Stack spacing={2.5}>
            <SectionCard
              title="Eligibility"
              subtitle="Blockers the service will apply at assignment time"
              actions={
                <EnumSelect
                  label="Against category"
                  value={category}
                  options={VEHICLE_CATEGORIES}
                  onChange={setCategory}
                  allowEmpty
                  emptyLabel="Any category"
                  sx={{ minWidth: 200 }}
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
                  <Stack spacing={1.5}>
                    <Stack
                      direction="row"
                      spacing={1}
                      alignItems="center"
                      flexWrap="wrap"
                      useFlexGap
                    >
                      <StatusChip value={eligibility.data.status} />
                      <Typography variant="caption" color="text.secondary">
                        Assessed {formatDateTime(eligibility.data.assessedAt)}
                        {eligibility.data.assessedForCategory
                          ? ` for ${humanise(eligibility.data.assessedForCategory)}`
                          : ''}
                      </Typography>
                    </Stack>
                    <BlockerList
                      blockers={eligibility.data.blockers}
                      clearMessage="No eligibility blockers. This driver can be assigned."
                    />
                  </Stack>
                )}
              </DataState>
            </SectionCard>

            <SectionCard title="Profile">
              <Stack spacing={2.5}>
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
                <Divider />
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
              </Stack>
            </SectionCard>

            <SectionCard
              title="Assignments"
              subtitle="Current and recent trips for this driver"
              actions={
                <Button component={RouterLink} to={fleetPaths.trips} variant="text" size="small">
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
                <Stack spacing={1.25}>
                  {assignments.data?.content.map((trip) => (
                    <Stack
                      key={trip.id}
                      component={RouterLink}
                      to={fleetPaths.tripDetail(trip.id)}
                      direction="row"
                      justifyContent="space-between"
                      alignItems="center"
                      spacing={1.5}
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
                      <Box sx={{ minWidth: 0 }}>
                        <Typography variant="body2" fontWeight={700} noWrap>
                          {trip.tripNumber} · {trip.origin} → {trip.destination}
                        </Typography>
                        <Typography variant="caption" color="text.secondary" noWrap>
                          {formatDateTime(trip.plannedStart)} → {formatDateTime(trip.plannedEnd)}
                        </Typography>
                      </Box>
                      <StatusChip value={trip.status} />
                    </Stack>
                  ))}
                </Stack>
              </DataState>
            </SectionCard>

            <UpdateDriverDialog
              open={editOpen}
              driver={driver.data}
              onClose={() => setEditOpen(false)}
              onSaved={() => {
                notifySuccess('Driver updated.');
                driver.refetch();
                eligibility.refetch();
              }}
            />
          </Stack>
        )}
      </DataState>
    </Box>
  );
};

export default DriverDetailPage;
