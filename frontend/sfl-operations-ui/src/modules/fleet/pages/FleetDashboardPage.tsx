import { useMemo, useState } from 'react';
import { Link as RouterLink } from 'react-router';
import { Alert, AlertTitle, Box, Button, Chip, Stack, Typography } from '@mui/material';
import { OPERATING_MODES, OperatingMode } from 'modules/fleet/api/enums';
import { dashboardApi, tripsApi, workflowApi } from 'modules/fleet/api/fleetApi';
import ExceptionsChart from 'modules/fleet/charts/ExceptionsChart';
import ReadinessChart from 'modules/fleet/charts/ReadinessChart';
import DrilldownDrawer from 'modules/fleet/components/DrilldownDrawer';
import IndicatorTile from 'modules/fleet/components/IndicatorTile';
import { sflActor } from 'shared/api/config';
import DataState from 'shared/components/DataState';
import PageHeader from 'shared/components/PageHeader';
import SectionCard from 'shared/components/SectionCard';
import StatusChip from 'shared/components/StatusChip';
import { EnumSelect, TextInput } from 'shared/components/fields';
import { formatDateTime } from 'shared/components/format';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { fleetPaths } from 'shared/layout/navigation';
import IconifyIcon from 'components/base/IconifyIcon';

const firstSite = sflActor.sites.split(',')[0]?.trim() ?? '';

/**
 * The Fleet operations workspace — the first screen of the console.
 *
 * Everything here is a live indicator from `GET /dashboards/operations`, including the service's
 * own `stale` flag and warning list, which are shown rather than hidden: an operator acting on
 * stale numbers is the failure mode this dashboard exists to prevent.
 */
const FleetDashboardPage = () => {
  const [siteCode, setSiteCode] = useState(firstSite);
  const [operatingMode, setOperatingMode] = useState<OperatingMode | ''>('');
  const [drilldown, setDrilldown] = useState<string | null>(null);

  const snapshot = useApiQuery(
    (signal) =>
      dashboardApi.operations(
        { siteCode: siteCode || undefined, operatingMode: operatingMode || undefined },
        signal,
      ),
    [siteCode, operatingMode],
  );

  const activeTrips = useApiQuery(
    (signal) =>
      tripsApi.search(
        { siteCode: siteCode || undefined, status: 'IN_PROGRESS', size: 8, sort: 'plannedStart' },
        signal,
      ),
    [siteCode],
  );

  const openExceptions = useApiQuery(
    (signal) =>
      workflowApi.search({ siteCode: siteCode || undefined, escalatedOnly: true, size: 6 }, signal),
    [siteCode],
  );

  const indicators = snapshot.data?.indicators;

  const readinessSlices = useMemo(() => {
    if (!indicators || !snapshot.data) {
      return [];
    }
    const total = snapshot.data.reconciliation.vehicles;
    const available = indicators.vehiclesAvailable;
    const blocked = indicators.readinessBlockers;
    const other = Math.max(total - available - blocked, 0);
    return [
      { name: 'Available', value: available, tone: 'ready' as const },
      { name: 'Committed', value: other, tone: 'caution' as const },
      { name: 'Readiness blocked', value: blocked, tone: 'blocked' as const },
    ];
  }, [indicators, snapshot.data]);

  const exceptionBars = useMemo(() => {
    if (!indicators) {
      return [];
    }
    return [
      { label: 'Expired compliance', value: indicators.expiredCompliance, critical: true },
      { label: 'Service due / overdue', value: indicators.serviceDue },
      { label: 'Assignment conflicts', value: indicators.assignmentConflicts, critical: true },
      { label: 'Readiness blockers', value: indicators.readinessBlockers, critical: true },
      { label: 'Open workflow items', value: indicators.openWorkflowItems },
      { label: 'Escalated items', value: indicators.escalatedWorkflowItems, critical: true },
      {
        label: 'Integration dead letters',
        value: indicators.integrationDeadLetters,
        critical: true,
      },
    ];
  }, [indicators]);

  return (
    <Box>
      <PageHeader
        title="Fleet operations"
        subtitle="Readiness, active movements and open exceptions across your site scope."
        actions={
          <>
            <Button
              variant="soft"
              color="neutral"
              onClick={snapshot.refetch}
              startIcon={<IconifyIcon icon="material-symbols:refresh-rounded" />}
            >
              Refresh
            </Button>
            <Button
              component={RouterLink}
              to={fleetPaths.trips}
              variant="contained"
              color="secondary"
              startIcon={<IconifyIcon icon="material-symbols:add-rounded" />}
            >
              Plan a trip
            </Button>
          </>
        }
        meta={
          snapshot.data && (
            <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
              <Chip
                size="small"
                variant="soft"
                color={snapshot.data.stale ? 'warning' : 'success'}
                label={`Snapshot ${formatDateTime(snapshot.data.generatedAt)}`}
              />
              <Chip
                size="small"
                variant="soft"
                color="neutral"
                label={`Scope ${snapshot.data.scopeKey}`}
              />
            </Stack>
          )
        }
      />

      <SectionCard flush>
        <Stack
          direction={{ xs: 'column', md: 'row' }}
          spacing={1.5}
          sx={{ p: 2, borderBottom: 1, borderColor: 'divider' }}
        >
          <TextInput
            label="Site code"
            value={siteCode}
            onChange={setSiteCode}
            placeholder="All sites in scope"
            sx={{ maxWidth: { md: 240 } }}
          />
          <EnumSelect
            label="Operating mode"
            value={operatingMode}
            options={OPERATING_MODES}
            onChange={setOperatingMode}
            allowEmpty
            sx={{ maxWidth: { md: 240 } }}
          />
        </Stack>
      </SectionCard>

      <Box sx={{ mt: 2.5 }}>
        <DataState
          loading={snapshot.initialising}
          error={snapshot.error}
          onRetry={snapshot.refetch}
          minHeight={320}
        >
          {snapshot.data && (
            <Stack spacing={2.5}>
              {snapshot.data.warnings.length > 0 && (
                <Alert severity={snapshot.data.stale ? 'warning' : 'info'}>
                  <AlertTitle sx={{ mb: 0.25 }}>
                    {snapshot.data.stale
                      ? 'Dashboard data may be out of date'
                      : 'Operational notice'}
                  </AlertTitle>
                  {snapshot.data.warnings.map((warning) => (
                    <Typography key={warning} variant="body2">
                      {warning}
                    </Typography>
                  ))}
                </Alert>
              )}

              <Box
                sx={{
                  display: 'grid',
                  gap: 2,
                  gridTemplateColumns: {
                    xs: 'repeat(1, minmax(0, 1fr))',
                    sm: 'repeat(2, minmax(0, 1fr))',
                    lg: 'repeat(4, minmax(0, 1fr))',
                  },
                }}
              >
                <IndicatorTile
                  label="Vehicles available"
                  value={snapshot.data.indicators.vehiclesAvailable}
                  icon="material-symbols:local-shipping-outline-rounded"
                  tone="good"
                  caption={`${snapshot.data.reconciliation.vehicles} in register`}
                />
                <IndicatorTile
                  label="Readiness blocked"
                  value={snapshot.data.indicators.readinessBlockers}
                  icon="material-symbols:error-outline-rounded"
                  tone={snapshot.data.indicators.readinessBlockers > 0 ? 'critical' : 'good'}
                  caption="Cannot be assigned"
                  onDrilldown={() => setDrilldown('READINESS_BLOCKERS')}
                />
                <IndicatorTile
                  label="Expired compliance"
                  value={snapshot.data.indicators.expiredCompliance}
                  icon="material-symbols:verified-user-outline-rounded"
                  tone={snapshot.data.indicators.expiredCompliance > 0 ? 'critical' : 'good'}
                  caption="Documents past expiry"
                  onDrilldown={() => setDrilldown('EXPIRED_COMPLIANCE')}
                />
                <IndicatorTile
                  label="Service due"
                  value={snapshot.data.indicators.serviceDue}
                  icon="material-symbols:handyman-outline"
                  tone={snapshot.data.indicators.serviceDue > 0 ? 'caution' : 'good'}
                  caption="Due or overdue"
                  onDrilldown={() => setDrilldown('SERVICE_DUE')}
                />
                <IndicatorTile
                  label="Assignment conflicts"
                  value={snapshot.data.indicators.assignmentConflicts}
                  icon="material-symbols:warning-outline-rounded"
                  tone={snapshot.data.indicators.assignmentConflicts > 0 ? 'critical' : 'good'}
                  caption="Double-booked vehicle or driver"
                  onDrilldown={() => setDrilldown('ASSIGNMENT_CONFLICTS')}
                />
                <IndicatorTile
                  label="Open workflow items"
                  value={snapshot.data.indicators.openWorkflowItems}
                  icon="material-symbols:pending-actions-rounded"
                  tone="neutral"
                  caption="Live queue"
                />
                <IndicatorTile
                  label="Escalated items"
                  value={snapshot.data.indicators.escalatedWorkflowItems}
                  icon="material-symbols:report-outline-rounded"
                  tone={snapshot.data.indicators.escalatedWorkflowItems > 0 ? 'critical' : 'good'}
                  caption="Past SLA or escalated"
                />
                <IndicatorTile
                  label="Integration dead letters"
                  value={snapshot.data.indicators.integrationDeadLetters}
                  icon="material-symbols:cloud"
                  tone={snapshot.data.indicators.integrationDeadLetters > 0 ? 'critical' : 'good'}
                  caption="Awaiting replay"
                />
              </Box>

              <Box
                sx={{
                  display: 'grid',
                  gap: 2,
                  gridTemplateColumns: { xs: '1fr', lg: '1fr 1.4fr' },
                }}
              >
                <SectionCard title="Fleet availability" subtitle="Vehicles in the current scope">
                  <ReadinessChart
                    slices={readinessSlices}
                    centreLabel="Available now"
                    centreValue={snapshot.data.indicators.vehiclesAvailable}
                  />
                </SectionCard>

                <SectionCard title="Open exceptions" subtitle="What needs attention today">
                  <ExceptionsChart bars={exceptionBars} />
                </SectionCard>
              </Box>

              <Box
                sx={{
                  display: 'grid',
                  gap: 2,
                  gridTemplateColumns: { xs: '1fr', lg: '1fr 1fr' },
                }}
              >
                <SectionCard
                  title="Active trips"
                  subtitle="Currently in progress"
                  actions={
                    <Button
                      component={RouterLink}
                      to={fleetPaths.trips}
                      variant="text"
                      size="small"
                    >
                      View queue
                    </Button>
                  }
                >
                  <DataState
                    loading={activeTrips.initialising}
                    error={activeTrips.error}
                    empty={(activeTrips.data?.content.length ?? 0) === 0}
                    emptyTitle="No active trips"
                    emptyHint="Nothing is on the road in this scope right now."
                    onRetry={activeTrips.refetch}
                    minHeight={160}
                  >
                    <Stack spacing={1.25}>
                      {activeTrips.data?.content.map((trip) => (
                        <Stack
                          key={trip.id}
                          component={RouterLink}
                          to={fleetPaths.tripDetail(trip.id)}
                          direction="row"
                          alignItems="center"
                          justifyContent="space-between"
                          spacing={1}
                          sx={{
                            textDecoration: 'none',
                            color: 'inherit',
                            p: 1.25,
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
                              Started {formatDateTime(trip.actualStart ?? trip.plannedStart)} ·{' '}
                              {trip.purpose}
                            </Typography>
                          </Box>
                          <StatusChip value={trip.status} />
                        </Stack>
                      ))}
                    </Stack>
                  </DataState>
                </SectionCard>

                <SectionCard
                  title="Escalated workflow"
                  subtitle="Items past SLA or manually escalated"
                  actions={
                    <Button
                      component={RouterLink}
                      to={fleetPaths.workflow}
                      variant="text"
                      size="small"
                    >
                      View queue
                    </Button>
                  }
                >
                  <DataState
                    loading={openExceptions.initialising}
                    error={openExceptions.error}
                    empty={(openExceptions.data?.content.length ?? 0) === 0}
                    emptyTitle="Nothing escalated"
                    emptyHint="No workflow item has breached its SLA in this scope."
                    onRetry={openExceptions.refetch}
                    minHeight={160}
                  >
                    <Stack spacing={1.25}>
                      {openExceptions.data?.content.map((item) => (
                        <Stack
                          key={item.id}
                          component={RouterLink}
                          to={fleetPaths.workflowDetail(item.id)}
                          direction="row"
                          alignItems="center"
                          justifyContent="space-between"
                          spacing={1}
                          sx={{
                            textDecoration: 'none',
                            color: 'inherit',
                            p: 1.25,
                            border: 1,
                            borderColor: 'divider',
                            borderRadius: 1.5,
                            '&:hover': { borderColor: 'secondary.main' },
                          }}
                        >
                          <Box sx={{ minWidth: 0 }}>
                            <Typography variant="body2" fontWeight={700} noWrap>
                              {item.workflowNumber} · {item.title}
                            </Typography>
                            <Typography variant="caption" color="text.secondary" noWrap>
                              SLA due {formatDateTime(item.slaDueAt)} · level {item.escalationLevel}
                            </Typography>
                          </Box>
                          <StatusChip value={item.priority} />
                        </Stack>
                      ))}
                    </Stack>
                  </DataState>
                </SectionCard>
              </Box>
            </Stack>
          )}
        </DataState>
      </Box>

      <DrilldownDrawer
        indicator={drilldown}
        siteCode={siteCode || undefined}
        onClose={() => setDrilldown(null)}
      />
    </Box>
  );
};

export default FleetDashboardPage;
