import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router';
import {
  ComplianceDocumentResponse,
  DashboardDrilldownRow,
  VehicleResponse,
} from 'modules/fleet/api/dto';
import { humanise } from 'modules/fleet/api/enums';
import { dashboardApi, vehiclesApi } from 'modules/fleet/api/fleetApi';

import Alert from 'shared/components/Alert';
import Button from 'shared/components/Button';
import DataState from 'shared/components/DataState';
import DataTable, { CellStack, Column } from 'shared/components/DataTable';
import PageHeader from 'shared/components/PageHeader';
import SectionCard from 'shared/components/SectionCard';
import SiteSelect, { defaultSite } from 'shared/components/SiteSelect';
import StatusChip from 'shared/components/StatusChip';
import Tabs from 'shared/components/Tabs';
import { formatDate, formatDaysRemaining, formatNumber } from 'shared/components/format';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { fleetPaths } from 'shared/layout/navigation';

/** How many vehicles the cross-fleet view will fan out over. See the gap note on this page. */
const AGGREGATION_LIMIT = 50;

interface DocumentRow {
  document: ComplianceDocumentResponse;
  vehicle: VehicleResponse;
}

type TabKey = 'expiring' | 'service' | 'all';

/** Expiry urgency is the one thing an operator reads first, so it carries a tone of its own. */
const expiryClass = (daysUntilExpiry: number): string => {
  if (daysUntilExpiry < 0) {
    return 'text-error-600';
  }
  return daysUntilExpiry < 30 ? 'text-warning-600' : 'text-gray-500';
};

/**
 * Compliance and service exposure across the fleet.
 *
 * The service has no cross-fleet compliance search — documents are only exposed per vehicle — so
 * this screen fans out over the first {@link AGGREGATION_LIMIT} vehicles in scope and says so.
 * The authoritative expired-document count stays the dashboard indicator, which is computed
 * server-side over the whole scope.
 */
const CompliancePage = () => {
  const navigate = useNavigate();
  const [siteCode, setSiteCode] = useState(defaultSite);
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
  // Kept separate from `visibleRows` so the tab count means the same thing on every tab.
  const expiringRows = rows.filter(
    (row) => row.document.daysUntilExpiry < 60 || row.document.status !== 'ACTIVE',
  );
  const visibleRows = tab === 'expiring' ? expiringRows : rows;

  const documentColumns = useMemo<Column<DocumentRow>[]>(
    () => [
      {
        key: 'vehicle',
        header: 'Vehicle',
        width: 180,
        cell: (row) => (
          <CellStack primary={row.vehicle.registrationNumber} secondary={row.vehicle.siteCode} />
        ),
      },
      {
        key: 'document',
        header: 'Document',
        width: 280,
        cell: (row) => (
          <div className="min-w-0">
            <div className="flex min-w-0 flex-wrap items-center gap-2">
              <span className="font-semibold text-gray-800">
                {humanise(row.document.documentType)}
              </span>
              {row.document.mandatory && (
                <StatusChip value="MANDATORY" label="Mandatory" tone="accent" />
              )}
            </div>
            <div className="truncate text-theme-xs text-gray-500">
              {row.document.documentReference} · {row.document.issuingAuthority}
            </div>
          </div>
        ),
      },
      {
        key: 'expiry',
        header: 'Expires',
        width: 160,
        cell: (row) => (
          <CellStack
            primary={formatDate(row.document.expiresOn)}
            secondary={
              <span className={expiryClass(row.document.daysUntilExpiry)}>
                {formatDaysRemaining(row.document.daysUntilExpiry)}
              </span>
            }
          />
        ),
      },
      {
        key: 'status',
        header: 'Status',
        width: 130,
        cell: (row) => <StatusChip value={row.document.status} />,
      },
    ],
    [],
  );

  const drilldownColumns = useMemo<Column<DashboardDrilldownRow>[]>(
    () => [
      {
        key: 'summary',
        header: 'Record',
        width: 320,
        cell: (row) => <span className="font-medium text-gray-800">{row.summary}</span>,
      },
      {
        key: 'siteCode',
        header: 'Site',
        width: 120,
        align: 'right',
        cell: (row) => <span className="text-theme-xs text-gray-500">{row.siteCode}</span>,
      },
    ],
    [],
  );

  return (
    <div>
      <PageHeader
        title="Compliance & service"
        subtitle="Expiring documents and service exposure across the vehicles in your site scope."
        crumbs={[{ label: 'Fleet', to: fleetPaths.dashboard }, { label: 'Compliance & service' }]}
        actions={
          <Button
            variant="outline"
            startIcon="refresh"
            onClick={() => {
              snapshot.refetch();
              documents.refetch();
              expiredDrilldown.refetch();
              serviceDrilldown.refetch();
            }}
          >
            Refresh
          </Button>
        }
        meta={
          snapshot.data && (
            <div className="flex flex-wrap items-center gap-2">
              <StatusChip
                value={snapshot.data.indicators.expiredCompliance > 0 ? 'EXPIRED' : 'ACTIVE'}
                label={`${snapshot.data.indicators.expiredCompliance} expired (whole scope)`}
              />
              <StatusChip
                value={snapshot.data.indicators.serviceDue > 0 ? 'DUE' : 'IN_SERVICE'}
                label={`${snapshot.data.indicators.serviceDue} service due (whole scope)`}
              />
            </div>
          )
        }
      />

      <div className="space-y-5">
        <SectionCard>
          <SiteSelect
            value={siteCode}
            onChange={setSiteCode}
            allowEmpty
            className="max-w-[280px]"
          />
        </SectionCard>

        <Alert variant="info">
          The service exposes compliance documents per vehicle only. This view aggregates the first{' '}
          {AGGREGATION_LIMIT} active vehicles in scope — the scope-wide counts above come from the
          server-computed dashboard indicators and are authoritative.
        </Alert>

        <SectionCard flush>
          <Tabs
            items={[
              {
                value: 'expiring',
                label: 'Expiring & expired',
                count: documents.data ? expiringRows.length : undefined,
              },
              {
                value: 'service',
                label: 'Service exposure',
                count: serviceDrilldown.data?.length,
              },
              { value: 'all', label: 'All documents', count: documents.data?.length },
            ]}
            value={tab}
            onChange={(value) => setTab(value as TabKey)}
          />

          <div className="p-5">
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
                <DataTable
                  rows={serviceDrilldown.data ?? []}
                  columns={drilldownColumns}
                  getRowId={(row) => `${row.resourceType}-${row.resourceId}`}
                  loading={serviceDrilldown.loading}
                  onRowClick={(row) => navigate(fleetPaths.vehicleDetail(row.resourceId))}
                  dense
                />
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
                <DataTable
                  rows={visibleRows}
                  columns={documentColumns}
                  getRowId={(row) => row.document.id}
                  loading={documents.loading}
                  onRowClick={(row) => navigate(fleetPaths.vehicleDetail(row.vehicle.id))}
                />
              </DataState>
            )}
          </div>
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
            <DataTable
              rows={expiredDrilldown.data ?? []}
              columns={drilldownColumns}
              getRowId={(row) => `${row.resourceType}-${row.resourceId}`}
              loading={expiredDrilldown.loading}
              dense
            />
          </DataState>
        </SectionCard>

        {snapshot.data && (
          <p className="text-theme-xs text-gray-500">
            Reconciliation: {formatNumber(snapshot.data.reconciliation.complianceDocuments)}{' '}
            compliance documents and {formatNumber(snapshot.data.reconciliation.vehicles)} vehicles
            in the current scope.
          </p>
        )}
      </div>
    </div>
  );
};

export default CompliancePage;
