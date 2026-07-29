import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router';
import {
  ComplianceDocumentResponse,
  DashboardDrilldownRow,
  VehicleResponse,
} from 'modules/fleet/api/dto';
import {
  COMPLIANCE_DOCUMENT_STATUSES,
  COMPLIANCE_DOCUMENT_TYPES,
  ComplianceDocumentStatus,
  ComplianceDocumentType,
  humanise,
} from 'modules/fleet/api/enums';
import { dashboardApi, vehiclesApi } from 'modules/fleet/api/fleetApi';

import Alert from 'shared/components/Alert';
import Button from 'shared/components/Button';
import DataState from 'shared/components/DataState';
import DataTable, { CellStack, Column } from 'shared/components/DataTable';
import { DateField } from 'shared/components/DateField';
import FilterBar from 'shared/components/FilterBar';
import PageHeader from 'shared/components/PageHeader';
import SectionCard from 'shared/components/SectionCard';
import SiteSelect, { defaultSite } from 'shared/components/SiteSelect';
import { EnumSelect } from 'shared/components/fields';
import StatusChip from 'shared/components/StatusChip';
import Tabs from 'shared/components/Tabs';
import { formatDate, formatDaysRemaining, formatNumber } from 'shared/components/format';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { fleetPaths } from 'shared/layout/navigation';

/**
 * How many documents the search asks for.
 *
 * A real limit on a real query now, not a fan-out ceiling. The service clamps at 500.
 */
const SEARCH_LIMIT = 200;

interface DocumentRow {
  document: ComplianceDocumentResponse;
  /**
   * The vehicle the document belongs to, when it could be resolved.
   *
   * The search returns documents, and a document carries a `vehicleId` but no registration number.
   * The site's vehicles are fetched **once** and indexed, so a row can name its vehicle without a
   * request per document. Null when the vehicle is outside the fetched page — the document is still
   * shown, because a compliance exposure does not stop mattering because a lookup missed.
   */
  vehicle: VehicleResponse | null;
}

/** What one search produced, and whether the service had more to give. */
interface DocumentSet {
  rows: DocumentRow[];
  truncated: boolean;
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
 * Documents come from `GET /vehicles/compliance-documents` — one query, filtered and ordered by the
 * service. This screen used to fan out over the first fifty active vehicles in scope and say so on
 * the page: correct for a small fleet and quietly wrong for any other, because a document on the
 * fifty-first vehicle simply was not there.
 *
 * The authoritative expired count is still the dashboard indicator. That is not a hedge — the
 * indicator is computed server-side over the whole scope and reconciled against its source, so it
 * remains the number to plan against even now that the list beside it is complete.
 */
const CompliancePage = () => {
  const navigate = useNavigate();
  const [siteCode, setSiteCode] = useState(defaultSite);
  const [tab, setTab] = useState<TabKey>('expiring');
  const [documentType, setDocumentType] = useState<ComplianceDocumentType | ''>('');
  const [status, setStatus] = useState<ComplianceDocumentStatus | ''>('');
  const [expiringBefore, setExpiringBefore] = useState('');
  const filtered = Boolean(documentType || status || expiringBefore);

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

  /**
   * One search, plus one vehicle page to name the rows.
   *
   * Two requests where there used to be fifty-one, and the answer is the site's whole compliance
   * position rather than the part of it that happened to sit on the first fifty vehicles.
   */
  const documents = useApiQuery(
    async (signal): Promise<DocumentSet> => {
      const [documentList, vehiclePage] = await Promise.all([
        vehiclesApi.searchComplianceDocuments(
          {
            documentType: documentType || undefined,
            status: status || undefined,
            expiringBefore: expiringBefore || undefined,
            size: SEARCH_LIMIT,
          },
          signal,
        ),
        vehiclesApi.search({ siteCode: siteCode || undefined, size: 200 }, signal),
      ]);
      const byId = new Map(vehiclePage.content.map((vehicle) => [vehicle.id, vehicle]));
      const rows = documentList
        // The search is scoped to the actor's own sites, which can be wider than the one site this
        // screen is showing, so the chosen site is applied here.
        .filter((document) => !siteCode || document.siteCode === siteCode)
        .map((document) => ({ document, vehicle: byId.get(document.vehicleId) ?? null }));
      // Measured before the site filter, because the cap applies to what the service returned.
      return { rows, truncated: documentList.length >= SEARCH_LIMIT };
    },
    [siteCode, documentType, status, expiringBefore],
  );

  const rows = documents.data?.rows ?? [];
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
        cell: (row) =>
          row.vehicle ? (
            <CellStack primary={row.vehicle.registrationNumber} secondary={row.vehicle.siteCode} />
          ) : (
            // The document is real even when its vehicle is outside the fetched page; showing the
            // shortened id is more use than hiding the exposure.
            <CellStack
              primary={`Vehicle ${row.document.vehicleId.slice(0, 8)}`}
              secondary={row.document.siteCode}
            />
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

        {documents.data?.truncated && (
          <Alert variant="warning">
            The search returned its maximum of {SEARCH_LIMIT} documents, so there are more than are
            listed here. Narrow it with a document type, a status or an expiry date — the counts
            above are computed server-side over the whole scope and stay right either way.
          </Alert>
        )}

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
              { value: 'all', label: 'All documents', count: documents.data?.rows.length },
            ]}
            value={tab}
            onChange={(value) => setTab(value as TabKey)}
          />

          {tab !== 'service' && (
            <FilterBar
              onReset={() => {
                setDocumentType('');
                setStatus('');
                setExpiringBefore('');
              }}
              resetDisabled={!filtered}
            >
              <EnumSelect
                label="Document type"
                value={documentType}
                options={COMPLIANCE_DOCUMENT_TYPES}
                onChange={(value) => setDocumentType(value as ComplianceDocumentType | '')}
                allowEmpty
              />
              <EnumSelect
                label="Status"
                value={status}
                options={COMPLIANCE_DOCUMENT_STATUSES}
                onChange={(value) => setStatus(value as ComplianceDocumentStatus | '')}
                allowEmpty
              />
              <DateField
                label="Expiring before"
                value={expiringBefore}
                onChange={setExpiringBefore}
                helperText="Includes documents that have already expired."
              />
            </FilterBar>
          )}

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
                    ? 'Nothing in this scope expires within 60 days or is already expired.'
                    : filtered
                      ? 'No document matches these filters.'
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
                  // The document's own `vehicleId` is always present; the resolved vehicle is not.
                  onRowClick={(row) => navigate(fleetPaths.vehicleDetail(row.document.vehicleId))}
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
