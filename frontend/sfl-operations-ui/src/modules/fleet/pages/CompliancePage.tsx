import { useState } from 'react';
import { Link as RouterLink } from 'react-router';
import { Alert, Box, Button, Stack, Tab, Tabs, Typography } from '@mui/material';
import { ComplianceDocumentResponse, VehicleResponse } from 'modules/fleet/api/dto';
import { humanise } from 'modules/fleet/api/enums';
import { dashboardApi, vehiclesApi } from 'modules/fleet/api/fleetApi';
import { sflActor } from 'shared/api/config';
import DataState from 'shared/components/DataState';
import PageHeader from 'shared/components/PageHeader';
import SectionCard from 'shared/components/SectionCard';
import StatusChip from 'shared/components/StatusChip';
import { TextInput } from 'shared/components/fields';
import { formatDate, formatDaysRemaining, formatNumber } from 'shared/components/format';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { fleetPaths } from 'shared/layout/navigation';
import IconifyIcon from 'components/base/IconifyIcon';

const firstSite = sflActor.sites.split(',')[0]?.trim() ?? '';

/** How many vehicles the cross-fleet view will fan out over. See the gap note on this page. */
const AGGREGATION_LIMIT = 50;

interface DocumentRow {
  document: ComplianceDocumentResponse;
  vehicle: VehicleResponse;
}

type TabKey = 'expiring' | 'service' | 'all';

/**
 * Compliance and service exposure across the fleet.
 *
 * The service has no cross-fleet compliance search — documents are only exposed per vehicle — so
 * this screen fans out over the first {@link AGGREGATION_LIMIT} vehicles in scope and says so.
 * The authoritative expired-document count stays the dashboard indicator, which is computed
 * server-side over the whole scope.
 */
const CompliancePage = () => {
  const [siteCode, setSiteCode] = useState(firstSite);
  const [tab, setTab] = useState<TabKey>('expiring');

  const snapshot = useApiQuery(
    (signal) => dashboardApi.operations({ siteCode: siteCode || undefined }, signal),
    [siteCode],
  );

  const expiredDrilldown = useApiQuery(
    (signal) =>
      dashboardApi.drilldown('EXPIRED_COMPLIANCE', { siteCode: siteCode || undefined }, signal),
    [siteCode],
  );

  const serviceDrilldown = useApiQuery(
    (signal) => dashboardApi.drilldown('SERVICE_DUE', { siteCode: siteCode || undefined }, signal),
    [siteCode],
  );

  const documents = useApiQuery(
    async (signal): Promise<DocumentRow[]> => {
      const page = await vehiclesApi.search(
        { siteCode: siteCode || undefined, status: 'ACTIVE', size: AGGREGATION_LIMIT },
        signal,
      );
      const perVehicle = await Promise.all(
        page.content.map(async (vehicle) => {
          try {
            const documentList = await vehiclesApi.complianceDocuments(vehicle.id, signal);
            return documentList.map((document) => ({ document, vehicle }));
          } catch {
            // A vehicle the caller cannot read is skipped rather than failing the whole view.
            return [];
          }
        }),
      );
      return perVehicle
        .flat()
        .sort((left, right) => left.document.daysUntilExpiry - right.document.daysUntilExpiry);
    },
    [siteCode],
  );

  const rows = documents.data ?? [];
  const visibleRows =
    tab === 'expiring'
      ? rows.filter((row) => row.document.daysUntilExpiry < 60 || row.document.status !== 'ACTIVE')
      : rows;

  return (
    <Box>
      <PageHeader
        title="Compliance & service"
        subtitle="Expiring documents and service exposure across the vehicles in your site scope."
        crumbs={[{ label: 'Fleet', to: fleetPaths.dashboard }, { label: 'Compliance & service' }]}
        actions={
          <Button
            variant="soft"
            color="neutral"
            onClick={() => {
              snapshot.refetch();
              documents.refetch();
              expiredDrilldown.refetch();
              serviceDrilldown.refetch();
            }}
            startIcon={<IconifyIcon icon="material-symbols:refresh-rounded" />}
          >
            Refresh
          </Button>
        }
        meta={
          snapshot.data && (
            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
              <StatusChip
                value={snapshot.data.indicators.expiredCompliance > 0 ? 'EXPIRED' : 'ACTIVE'}
                label={`${snapshot.data.indicators.expiredCompliance} expired (whole scope)`}
              />
              <StatusChip
                value={snapshot.data.indicators.serviceDue > 0 ? 'DUE' : 'IN_SERVICE'}
                label={`${snapshot.data.indicators.serviceDue} service due (whole scope)`}
              />
            </Stack>
          )
        }
      />

      <Stack spacing={2.5}>
        <SectionCard>
          <TextInput
            label="Site code"
            value={siteCode}
            onChange={setSiteCode}
            sx={{ maxWidth: 280 }}
          />
        </SectionCard>

        <Alert severity="info">
          The service exposes compliance documents per vehicle only. This view aggregates the first{' '}
          {AGGREGATION_LIMIT} active vehicles in scope — the scope-wide counts above come from the
          server-computed dashboard indicators and are authoritative.
        </Alert>

        <SectionCard flush>
          <Tabs
            value={tab}
            onChange={(_event, value: TabKey) => setTab(value)}
            sx={{ px: 2, borderBottom: 1, borderColor: 'divider' }}
            variant="scrollable"
            allowScrollButtonsMobile
          >
            <Tab label="Expiring & expired" value="expiring" />
            <Tab label="Service exposure" value="service" />
            <Tab label="All documents" value="all" />
          </Tabs>

          <Box sx={{ p: 2.5 }}>
            {tab === 'service' ? (
              <DataState
                loading={serviceDrilldown.initialising}
                error={serviceDrilldown.error}
                empty={(serviceDrilldown.data?.length ?? 0) === 0}
                emptyTitle="No vehicles due for service"
                emptyHint="Nothing in this scope is due or overdue."
                onRetry={serviceDrilldown.refetch}
                minHeight={180}
              >
                <Stack spacing={1.25}>
                  {serviceDrilldown.data?.map((row) => (
                    <Stack
                      key={row.resourceId}
                      component={RouterLink}
                      to={fleetPaths.vehicleDetail(row.resourceId)}
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
                      <Typography variant="body2" fontWeight={600}>
                        {row.summary}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        {row.siteCode}
                      </Typography>
                    </Stack>
                  ))}
                </Stack>
              </DataState>
            ) : (
              <DataState
                loading={documents.initialising}
                error={documents.error}
                empty={visibleRows.length === 0}
                emptyTitle={
                  tab === 'expiring' ? 'Nothing expiring soon' : 'No compliance documents'
                }
                emptyHint={
                  tab === 'expiring'
                    ? 'No document in the aggregated set expires within 60 days.'
                    : 'Register compliance documents from a vehicle record.'
                }
                onRetry={documents.refetch}
                minHeight={200}
              >
                <Stack spacing={1.25}>
                  {visibleRows.map((row) => (
                    <Stack
                      key={row.document.id}
                      component={RouterLink}
                      to={fleetPaths.vehicleDetail(row.vehicle.id)}
                      direction={{ xs: 'column', sm: 'row' }}
                      justifyContent="space-between"
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
                        <Stack
                          direction="row"
                          spacing={1}
                          alignItems="center"
                          flexWrap="wrap"
                          useFlexGap
                        >
                          <Typography variant="body2" fontWeight={700}>
                            {row.vehicle.registrationNumber}
                          </Typography>
                          <Typography variant="body2" color="text.secondary">
                            {humanise(row.document.documentType)}
                          </Typography>
                          {row.document.mandatory && (
                            <StatusChip value="MANDATORY" label="Mandatory" tone="accent" />
                          )}
                        </Stack>
                        <Typography variant="caption" color="text.secondary">
                          {row.document.documentReference} · {row.document.issuingAuthority} ·{' '}
                          {row.vehicle.siteCode}
                        </Typography>
                      </Box>
                      <Stack
                        direction="row"
                        spacing={1.5}
                        alignItems="center"
                        sx={{ flexShrink: 0 }}
                      >
                        <Box sx={{ textAlign: { sm: 'right' } }}>
                          <Typography variant="body2" fontWeight={600}>
                            {formatDate(row.document.expiresOn)}
                          </Typography>
                          <Typography
                            variant="caption"
                            color={
                              row.document.daysUntilExpiry < 0
                                ? 'error.main'
                                : row.document.daysUntilExpiry < 30
                                  ? 'warning.main'
                                  : 'text.secondary'
                            }
                          >
                            {formatDaysRemaining(row.document.daysUntilExpiry)}
                          </Typography>
                        </Box>
                        <StatusChip value={row.document.status} />
                      </Stack>
                    </Stack>
                  ))}
                </Stack>
              </DataState>
            )}
          </Box>
        </SectionCard>

        <SectionCard
          title="Expired documents (whole scope)"
          subtitle="Server-computed drilldown behind the dashboard indicator"
        >
          <DataState
            loading={expiredDrilldown.initialising}
            error={expiredDrilldown.error}
            empty={(expiredDrilldown.data?.length ?? 0) === 0}
            emptyTitle="No expired documents"
            emptyHint="Nothing in your scope is past its expiry date."
            onRetry={expiredDrilldown.refetch}
            minHeight={140}
          >
            <Stack spacing={1}>
              {expiredDrilldown.data?.map((row) => (
                <Stack
                  key={row.resourceId}
                  direction="row"
                  justifyContent="space-between"
                  spacing={1.5}
                  sx={{ p: 1.25, border: 1, borderColor: 'divider', borderRadius: 1.5 }}
                >
                  <Typography variant="body2">{row.summary}</Typography>
                  <Typography variant="caption" color="text.secondary">
                    {row.siteCode}
                  </Typography>
                </Stack>
              ))}
            </Stack>
          </DataState>
        </SectionCard>

        {snapshot.data && (
          <Typography variant="caption" color="text.secondary">
            Reconciliation: {formatNumber(snapshot.data.reconciliation.complianceDocuments)}{' '}
            compliance documents and {formatNumber(snapshot.data.reconciliation.vehicles)} vehicles
            in the current scope.
          </Typography>
        )}
      </Stack>
    </Box>
  );
};

export default CompliancePage;
