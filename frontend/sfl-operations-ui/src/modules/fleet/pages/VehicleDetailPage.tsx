import { useState } from 'react';
import { Link as RouterLink, useNavigate, useParams } from 'react-router';
import { Alert, Box, Button, Divider, Stack, Tab, Tabs, Typography } from '@mui/material';
import { humanise } from 'modules/fleet/api/enums';
import { tripsApi, vehiclesApi } from 'modules/fleet/api/fleetApi';
import {
  ChangeVehicleLifecycleDialog,
  CorrectOdometerDialog,
  EditVehicleDialog,
  RecordServiceDialog,
  RegisterComplianceDocumentDialog,
} from 'modules/fleet/dialogs/vehicleDialogs';
import BlockerList from 'shared/components/BlockerList';
import DataState from 'shared/components/DataState';
import KeyValueGrid from 'shared/components/KeyValueGrid';
import { useNotifier } from 'shared/components/Notifier';
import PageHeader from 'shared/components/PageHeader';
import SectionCard from 'shared/components/SectionCard';
import StatusChip from 'shared/components/StatusChip';
import {
  formatDate,
  formatDateTime,
  formatDaysRemaining,
  formatNumber,
  formatOdometer,
} from 'shared/components/format';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { fleetPaths } from 'shared/layout/navigation';
import IconifyIcon from 'components/base/IconifyIcon';

type TabKey = 'overview' | 'compliance' | 'service' | 'trips';

/**
 * Vehicle detail.
 *
 * Readiness comes from the assignment-preview endpoint rather than a per-vehicle readiness route,
 * because that is the endpoint the service actually exposes and it runs the same policy the
 * assignment itself will run.
 */
const VehicleDetailPage = () => {
  const { vehicleId = '' } = useParams();
  const navigate = useNavigate();
  const { notifySuccess } = useNotifier();
  const [tab, setTab] = useState<TabKey>('overview');
  const [dialog, setDialog] = useState<
    'edit' | 'lifecycle' | 'compliance' | 'service' | 'odometer' | null
  >(null);

  const vehicle = useApiQuery((signal) => vehiclesApi.findById(vehicleId, signal), [vehicleId]);
  const readiness = useApiQuery(
    (signal) => tripsApi.assignmentPreview({ vehicleId }, signal),
    [vehicleId],
  );
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

  const refreshAll = () => {
    vehicle.refetch();
    readiness.refetch();
    compliance.refetch();
    service.refetch();
  };

  return (
    <Box>
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
              variant="soft"
              color="neutral"
              onClick={() => navigate(fleetPaths.vehicles)}
              startIcon={<IconifyIcon icon="material-symbols:arrow-back-rounded" />}
            >
              Register
            </Button>
            <Button variant="soft" color="neutral" onClick={() => setDialog('odometer')}>
              Correct odometer
            </Button>
            <Button variant="soft" color="neutral" onClick={() => setDialog('lifecycle')}>
              Lifecycle
            </Button>
            <Button
              variant="contained"
              color="secondary"
              onClick={() => setDialog('edit')}
              startIcon={<IconifyIcon icon="material-symbols:edit-outline-rounded" />}
            >
              Edit
            </Button>
          </>
        }
        meta={
          vehicle.data && (
            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
              <StatusChip value={vehicle.data.lifecycleStatus} />
              <StatusChip value={vehicle.data.serviceStatus} />
              <StatusChip value={vehicle.data.availabilityStatus} />
              {readiness.data && <StatusChip value={readiness.data.status} />}
            </Stack>
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
          <Stack spacing={2.5}>
            <SectionCard
              title="Readiness"
              subtitle="Assessed with the same policy the assignment will use"
              actions={
                <Button variant="text" size="small" onClick={readiness.refetch}>
                  Re-assess
                </Button>
              }
            >
              <DataState
                loading={readiness.initialising}
                error={readiness.error}
                onRetry={readiness.refetch}
                minHeight={80}
              >
                {readiness.data && (
                  <Stack spacing={1.5}>
                    <Stack
                      direction="row"
                      spacing={1}
                      alignItems="center"
                      flexWrap="wrap"
                      useFlexGap
                    >
                      <StatusChip value={readiness.data.status} />
                      <Typography variant="caption" color="text.secondary">
                        Assessed {formatDateTime(readiness.data.assessedAt)}
                      </Typography>
                    </Stack>
                    <BlockerList
                      blockers={readiness.data.blockers}
                      clearMessage="No readiness blockers. This vehicle can be assigned."
                    />
                  </Stack>
                )}
              </DataState>
            </SectionCard>

            <SectionCard flush>
              <Tabs
                value={tab}
                onChange={(_event, value: TabKey) => setTab(value)}
                sx={{ px: 2, borderBottom: 1, borderColor: 'divider' }}
                variant="scrollable"
                allowScrollButtonsMobile
              >
                <Tab label="Overview" value="overview" />
                <Tab label="Compliance" value="compliance" />
                <Tab label="Service history" value="service" />
                <Tab label="Trips" value="trips" />
              </Tabs>

              <Box sx={{ p: 2.5 }}>
                {tab === 'overview' && (
                  <Stack spacing={2.5}>
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
                            <RouterLink to={fleetPaths.tripDetail(vehicle.data.currentTripId)}>
                              Open trip
                            </RouterLink>
                          ) : (
                            '—'
                          ),
                        },
                        { label: 'Record version', value: vehicle.data.version },
                      ]}
                    />
                    <Divider />
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
                  </Stack>
                )}

                {tab === 'compliance' && (
                  <Stack spacing={2}>
                    <Stack direction="row" justifyContent="flex-end">
                      <Button
                        variant="soft"
                        color="secondary"
                        size="small"
                        onClick={() => setDialog('compliance')}
                        startIcon={<IconifyIcon icon="material-symbols:add-rounded" />}
                      >
                        Register document
                      </Button>
                    </Stack>
                    <DataState
                      loading={compliance.initialising}
                      error={compliance.error}
                      empty={(compliance.data?.length ?? 0) === 0}
                      emptyTitle="No compliance documents"
                      emptyHint="Mandatory documents are missing — the vehicle carries a blocking readiness blocker until they are registered."
                      onRetry={compliance.refetch}
                      minHeight={160}
                    >
                      <Stack spacing={1.25}>
                        {compliance.data?.map((document) => (
                          <Stack
                            key={document.id}
                            direction={{ xs: 'column', sm: 'row' }}
                            spacing={1.5}
                            justifyContent="space-between"
                            sx={{ p: 1.5, border: 1, borderColor: 'divider', borderRadius: 1.5 }}
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
                                  {humanise(document.documentType)}
                                </Typography>
                                {document.mandatory && (
                                  <StatusChip value="MANDATORY" label="Mandatory" tone="accent" />
                                )}
                              </Stack>
                              <Typography variant="caption" color="text.secondary">
                                {document.documentReference} · {document.issuingAuthority} · issued{' '}
                                {formatDate(document.issuedOn)}
                              </Typography>
                            </Box>
                            <Stack
                              direction="row"
                              spacing={1}
                              alignItems="center"
                              sx={{ flexShrink: 0 }}
                            >
                              <Box sx={{ textAlign: { sm: 'right' } }}>
                                <Typography variant="body2" fontWeight={600}>
                                  {formatDate(document.expiresOn)}
                                </Typography>
                                <Typography
                                  variant="caption"
                                  color={
                                    document.daysUntilExpiry < 0
                                      ? 'error.main'
                                      : document.daysUntilExpiry < 30
                                        ? 'warning.main'
                                        : 'text.secondary'
                                  }
                                >
                                  {formatDaysRemaining(document.daysUntilExpiry)}
                                </Typography>
                              </Box>
                              <StatusChip value={document.status} />
                            </Stack>
                          </Stack>
                        ))}
                      </Stack>
                    </DataState>
                  </Stack>
                )}

                {tab === 'service' && (
                  <Stack spacing={2}>
                    <Stack direction="row" justifyContent="flex-end">
                      <Button
                        variant="soft"
                        color="secondary"
                        size="small"
                        onClick={() => setDialog('service')}
                        startIcon={<IconifyIcon icon="material-symbols:add-rounded" />}
                      >
                        Record service
                      </Button>
                    </Stack>
                    <DataState
                      loading={service.initialising}
                      error={service.error}
                      onRetry={service.refetch}
                      minHeight={160}
                    >
                      {service.data && (
                        <Stack spacing={2}>
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
                          <Divider />
                          {service.data.history.length === 0 ? (
                            <Typography variant="body2" color="text.secondary">
                              No service events recorded yet.
                            </Typography>
                          ) : (
                            <Stack spacing={1.25}>
                              {service.data.history.map((record) => (
                                <Stack
                                  key={record.id}
                                  direction={{ xs: 'column', sm: 'row' }}
                                  spacing={1.5}
                                  justifyContent="space-between"
                                  sx={{
                                    p: 1.5,
                                    border: 1,
                                    borderColor: 'divider',
                                    borderRadius: 1.5,
                                  }}
                                >
                                  <Box sx={{ minWidth: 0 }}>
                                    <Typography variant="body2" fontWeight={700}>
                                      {humanise(record.serviceType)} ·{' '}
                                      {formatDate(record.performedOn)}
                                    </Typography>
                                    <Typography variant="caption" color="text.secondary">
                                      {record.workSummary}
                                    </Typography>
                                  </Box>
                                  <Stack
                                    direction="row"
                                    spacing={1}
                                    alignItems="center"
                                    sx={{ flexShrink: 0 }}
                                  >
                                    <Typography variant="caption" color="text.secondary">
                                      {formatNumber(record.odometerAtService)} km
                                    </Typography>
                                    <StatusChip value={record.outcome} />
                                  </Stack>
                                </Stack>
                              ))}
                            </Stack>
                          )}
                        </Stack>
                      )}
                    </DataState>
                  </Stack>
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
                    <Stack spacing={1.25}>
                      {trips.data?.content.map((trip) => (
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
                              {formatDateTime(trip.plannedStart)} · {trip.purpose}
                            </Typography>
                          </Box>
                          <StatusChip value={trip.status} />
                        </Stack>
                      ))}
                    </Stack>
                  </DataState>
                )}
              </Box>
            </SectionCard>

            {vehicle.data.lifecycleStatus === 'ARCHIVED' && (
              <Alert severity="info">
                Archived records are immutable outside an authorised restoration workflow. Edits
                will be refused with FLEET_ARCHIVED_RECORD_IMMUTABLE.
              </Alert>
            )}

            <EditVehicleDialog
              open={dialog === 'edit'}
              vehicle={vehicle.data}
              onClose={() => setDialog(null)}
              onSaved={() => {
                notifySuccess('Vehicle updated.');
                refreshAll();
              }}
            />
            <ChangeVehicleLifecycleDialog
              open={dialog === 'lifecycle'}
              vehicle={vehicle.data}
              onClose={() => setDialog(null)}
              onSaved={() => {
                notifySuccess('Lifecycle status changed.');
                refreshAll();
              }}
            />
            <RegisterComplianceDocumentDialog
              open={dialog === 'compliance'}
              vehicleId={vehicle.data.id}
              onClose={() => setDialog(null)}
              onSaved={() => {
                notifySuccess('Compliance document registered.');
                refreshAll();
              }}
            />
            <RecordServiceDialog
              open={dialog === 'service'}
              vehicleId={vehicle.data.id}
              currentOdometer={vehicle.data.odometerValue}
              onClose={() => setDialog(null)}
              onSaved={() => {
                notifySuccess('Service event recorded.');
                refreshAll();
              }}
            />
            <CorrectOdometerDialog
              open={dialog === 'odometer'}
              vehicle={vehicle.data}
              onClose={() => setDialog(null)}
              onSaved={() => {
                notifySuccess('Odometer corrected.');
                refreshAll();
              }}
            />
          </Stack>
        )}
      </DataState>
    </Box>
  );
};

export default VehicleDetailPage;
