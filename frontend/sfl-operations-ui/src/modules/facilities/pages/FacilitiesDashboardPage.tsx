import { useState } from 'react';
import { useNavigate } from 'react-router';
import Alert from 'shared/components/Alert';
import DataState from 'shared/components/DataState';
import DataTable, { Column } from 'shared/components/DataTable';
import PageHeader from 'shared/components/PageHeader';
import SectionCard from 'shared/components/SectionCard';
import SiteSelect, { defaultSite } from 'shared/components/SiteSelect';
import StatCard from 'shared/components/StatCard';
import StatusChip from 'shared/components/StatusChip';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { facilitiesPaths } from 'shared/layout/navigation';
import { getDashboard } from '../api/facilitiesApi';
import type { DashboardExceptionRow } from '../api/dto';
import { canDrillDown } from '../api/workflow';
import { formatDateTime, humaniseCode, scoreTone, severityTone } from '../components/facilitiesFormat';

/**
 * The S152-05 facilities dashboard.
 *
 * Computed live by the service from the source records, which is why the counts always reconcile to
 * the rows behind them — and why every tile here can be opened rather than only believed.
 *
 * The stale-data warning is given the top of the page, not a footnote. SRS-SFL-S152-05 requires that
 * "critical safety and examination-readiness indicators must display stale-data warnings where
 * freshness thresholds are breached", and a dashboard that shows confident numbers over readiness
 * nobody has checked in a fortnight is worse than one that shows nothing: it converts absence of
 * information into apparent good news.
 */
const FacilitiesDashboardPage = () => {
  const navigate = useNavigate();
  const [siteCode, setSiteCode] = useState<string>(defaultSite);

  const { data, loading, error, refetch } = useApiQuery(
    (signal) => getDashboard(siteCode || undefined, signal),
    [siteCode],
  );

  const drilldown = canDrillDown();

  const exceptionColumns: Column<DashboardExceptionRow>[] = [
    {
      key: 'code',
      header: 'Space',
      cell: (row) => <span className="font-medium text-gray-900">{row.code}</span>,
    },
    { key: 'label', header: 'Name', cell: (row) => row.label },
    {
      key: 'reason',
      header: 'Why',
      cell: (row) => <span className="text-gray-600">{row.reason}</span>,
    },
    {
      key: 'severity',
      header: 'Severity',
      align: 'right',
      width: 120,
      cell: (row) => (
        <StatusChip
          value={row.severity}
          tone={severityTone(row.severity as 'CRITICAL' | 'MAJOR' | 'MINOR' | 'ADVISORY')}
        />
      ),
    },
  ];

  /** A drilldown row opens its space. Only offered when the actor may see the underlying record. */
  const openSpace = drilldown
    ? (row: DashboardExceptionRow) => navigate(facilitiesPaths.spaceDetail(row.id))
    : undefined;

  return (
    <>
      <PageHeader
        title="Facilities dashboard"
        subtitle="Readiness, blockers and examination risk across the estate"
        meta={
          data ? (
            <span className="text-theme-xs text-gray-500">
              {data.operatingMode === 'EXAMINATION' ? 'Examination mode' : 'Routine operations'} ·
              generated {formatDateTime(data.generatedAt)}
            </span>
          ) : undefined
        }
        actions={
          <SiteSelect value={siteCode} onChange={setSiteCode} allowEmpty emptyLabel="All sites" />
        }
      />

      <DataState loading={loading} error={error} empty={!data} onRetry={refetch} minHeight={320}>
        {data && (
          <div className="space-y-5">
            {/*
              Two banners, in this order, because they answer different questions and the second is
              worthless without the first: "can I trust these numbers?" then "what do they say?"
            */}
            {data.stale && data.staleWarning && (
              <Alert variant="warning" title="Readiness data is stale">
                {data.staleWarning}
              </Alert>
            )}

            {data.operatingMode === 'EXAMINATION' && (
              <Alert variant="info" title="This centre is in examination mode">
                Readiness is assessed against the examination standard and the staleness threshold is
                tighter. Spaces must be READY outright to host an examination.
              </Alert>
            )}

            <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
              <StatCard
                label="Site readiness"
                value={`${data.readinessScore}%`}
                icon="gauge"
                tone={
                  scoreTone(data.readinessScore) === 'ready'
                    ? 'good'
                    : scoreTone(data.readinessScore) === 'caution'
                      ? 'caution'
                      : 'critical'
                }
                caption={`${data.spaces.ready} of ${data.spaces.total} spaces ready`}
              />
              <StatCard
                label="Blocked spaces"
                value={data.spaces.blocked}
                icon="alert-triangle"
                tone={data.spaces.blocked > 0 ? 'critical' : 'good'}
                caption={`${data.spaces.degraded} degraded, ${data.spaces.unknown} unassessed`}
              />
              <StatCard
                label="Critical blockers"
                value={data.blockers.critical}
                icon="alert-circle"
                tone={data.blockers.critical > 0 ? 'critical' : 'good'}
                caption={
                  data.blockers.criticalBeyondEscalationWindow > 0
                    ? `${data.blockers.criticalBeyondEscalationWindow} past the escalation window`
                    : `${data.blockers.total} open in total`
                }
              />
              <StatCard
                label="Examination-ready"
                value={`${data.spaces.availableForExamination}/${data.spaces.examinationCapable}`}
                icon="shield-check"
                tone={
                  data.spaces.availableForExamination < data.spaces.examinationCapable
                    ? 'caution'
                    : 'good'
                }
                caption="Capable spaces currently usable"
              />
            </div>

            <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
              <StatCard
                label="Impaired assets"
                value={data.assets.impaired}
                icon="wrench"
                tone={data.assets.criticalImpaired > 0 ? 'critical' : 'neutral'}
                caption={`${data.assets.criticalImpaired} critical, of ${data.assets.total} assets`}
              />
              <StatCard
                label="Service overdue"
                value={data.assets.serviceOverdue}
                icon="clock"
                tone={data.assets.serviceOverdue > 0 ? 'caution' : 'good'}
                caption={`${data.assets.serviceDueSoon} due soon`}
              />
              <StatCard
                label="Open faults"
                value={data.maintenance.openFaults}
                icon="alert-circle"
                tone={data.maintenance.openFaults > 0 ? 'caution' : 'good'}
                caption={`${data.maintenance.openWorkOrders} work orders open`}
              />
              <StatCard
                label="Bookable now"
                value={`${data.spaces.availableForBooking}/${data.spaces.bookable}`}
                icon="calendar"
                tone={data.spaces.availableForBooking < data.spaces.bookable ? 'caution' : 'good'}
                caption="Bookable spaces currently available"
              />
            </div>

            {!drilldown && (
              <Alert variant="info">
                You can see these totals but not the records behind them. Drilling into an exception
                needs the facilities dashboard drilldown permission.
              </Alert>
            )}

            <SectionCard
              title="Examination readiness risk"
              subtitle="Examination-capable spaces with something standing between them and use"
            >
              <DataTable
                rows={data.examinationRisks}
                columns={exceptionColumns}
                getRowId={(row) => row.id}
                onRowClick={openSpace}
                emptyMessage="No examination-capable space is at risk."
                dense
              />
            </SectionCard>

            <SectionCard
              title="Unavailable spaces"
              subtitle="Bookable spaces that cannot currently be booked"
            >
              <DataTable
                rows={data.unavailableSpaces}
                columns={exceptionColumns}
                getRowId={(row) => row.id}
                onRowClick={openSpace}
                emptyMessage="Every bookable space is available."
                dense
              />
            </SectionCard>

            <SectionCard
              title="Stale readiness"
              subtitle="Spaces not reassessed inside the configured window, or never assessed"
            >
              <DataTable
                rows={data.staleReadiness}
                columns={[
                  exceptionColumns[0],
                  exceptionColumns[1],
                  {
                    key: 'reason',
                    header: 'Last assessed',
                    cell: (row) => <span className="text-gray-600">{row.reason}</span>,
                  },
                  {
                    key: 'severity',
                    header: '',
                    align: 'right',
                    width: 120,
                    cell: (row) => (
                      <StatusChip
                        value={row.severity === 'MAJOR' ? 'NEVER ASSESSED' : 'OVERDUE'}
                        tone={row.severity === 'MAJOR' ? 'blocked' : 'caution'}
                        label={humaniseCode(row.severity === 'MAJOR' ? 'NEVER_ASSESSED' : 'OVERDUE')}
                      />
                    ),
                  },
                ]}
                getRowId={(row) => row.id}
                onRowClick={openSpace}
                emptyMessage="Every space has been assessed inside the window."
                dense
              />
            </SectionCard>
          </div>
        )}
      </DataState>
    </>
  );
};

export default FacilitiesDashboardPage;
