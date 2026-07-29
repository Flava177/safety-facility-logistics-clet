import { useMemo } from 'react';
import { useNavigate, useParams } from 'react-router';
import { CHANNEL_DESCRIPTIONS } from 'modules/emergency/api/enums';
import { activationsApi, emergencyRecordsApi } from 'modules/emergency/api/emergencyApi';
import { ActivationStatusChip } from 'modules/emergency/components/EmergencyFields';
import { DerivedNote } from 'modules/fuel/components/Provenance';
import { siteOf } from 'modules/fuel/components/fuelFormat';
import { humanise } from 'modules/fleet/api/enums';
import Alert from 'shared/components/Alert';
import Button from 'shared/components/Button';
import DataState from 'shared/components/DataState';
import DataTable, { CellStack, Column } from 'shared/components/DataTable';
import KeyValueGrid from 'shared/components/KeyValueGrid';
import PageHeader from 'shared/components/PageHeader';
import SectionCard from 'shared/components/SectionCard';
import StatusChip from 'shared/components/StatusChip';
import { formatDateTime } from 'shared/components/format';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { emergencyPaths } from 'shared/layout/navigation';
import type { NotificationActivation } from 'modules/emergency/api/dto';

/**
 * One notification template.
 *
 * `GET /templates/{id}` is the only detail endpoint on this service — scenarios, audience groups
 * and recipient zones have none — so this is the only record that can be linked to and returned to
 * directly. Everything else is read out of its site's list.
 *
 * The activations that used it are counted here from the site's activation register rather than
 * queried, because no endpoint answers "what has this template been used for". That question is
 * exactly the one an operator asks before marking something break-glass eligible, so it is worth
 * answering from what is available and captioning honestly.
 */
const EmergencyTemplateDetailPage = () => {
  const { templateId = '' } = useParams();
  const navigate = useNavigate();

  const query = useApiQuery(
    (signal) => emergencyRecordsApi.template(templateId, signal),
    [templateId],
  );

  const template = query.data;
  const siteCode = template ? siteOf(template.siteCode) : '';

  /**
   * The activations that cite this template.
   *
   * `templateId` reaches the service now, so this is a real answer to "what has this message been
   * used for" rather than the site's window sieved down — which quietly missed older activations
   * and made a never-used template indistinguishable from a busy one at a busy site.
   */
  const activations = useApiQuery(
    (signal) =>
      siteCode
        ? activationsApi.search({ siteCode, templateId, size: 50 }, signal)
        : Promise.resolve(undefined),
    [siteCode, templateId],
  );

  const usedBy = useMemo(() => activations.data?.content ?? [], [activations.data]);

  const columns = useMemo<Column<NotificationActivation>[]>(
    () => [
      {
        key: 'activation',
        header: 'Activation',
        width: 240,
        cell: (row) => (
          <CellStack
            primary={row.activationNumber}
            secondary={row.incidentReference ?? 'No incident reference'}
          />
        ),
      },
      {
        key: 'mode',
        header: 'Mode',
        width: 130,
        cell: (row) => <StatusChip value={row.mode} />,
      },
      {
        key: 'when',
        header: 'Composed',
        width: 170,
        hideBelowLg: true,
        cell: (row) => formatDateTime(row.metadata.createdAt),
      },
      {
        key: 'status',
        header: 'Status',
        width: 170,
        align: 'right',
        cell: (row) => <ActivationStatusChip status={row.status} />,
      },
    ],
    [],
  );

  return (
    <div>
      <DataState
        loading={query.initialising}
        error={query.error}
        onRetry={query.refetch}
        minHeight={320}
      >
        {template && (
          <>
            <PageHeader
              title={template.title}
              subtitle={`${template.templateCode} · ${siteCode}`}
              crumbs={[
                { label: 'Emergency', to: emergencyPaths.dashboard },
                { label: 'Templates and scenarios', to: emergencyPaths.templates },
                { label: template.templateCode },
              ]}
              meta={
                <span className="flex flex-wrap items-center gap-2">
                  <StatusChip value={template.lifecycle} size="md" />
                  {template.breakGlassEligible && (
                    <StatusChip
                      value="BREAK_GLASS"
                      label="Break-glass eligible"
                      tone="blocked"
                      size="md"
                    />
                  )}
                </span>
              }
              actions={
                <Button
                  variant="outline"
                  startIcon="arrow-left"
                  onClick={() => navigate(emergencyPaths.templates)}
                >
                  Back to templates
                </Button>
              }
            />

            <div className="space-y-5">
              {template.breakGlassEligible && (
                <Alert variant="warning" title="This template can be sent without approval">
                  An authorised role may broadcast it during a declared emergency with nobody
                  reviewing it first. Whatever it says below is what would go out.
                </Alert>
              )}

              <SectionCard title="Message">
                <p className="whitespace-pre-wrap text-theme-sm leading-relaxed text-gray-900">
                  {template.body}
                </p>
              </SectionCard>

              <SectionCard title="Channels">
                <ul className="space-y-2.5">
                  {template.channels.map((channel) => (
                    <li key={channel} className="text-theme-sm">
                      <span className="font-medium text-gray-900">{humanise(channel)}</span>
                      <span className="text-gray-600"> — {CHANNEL_DESCRIPTIONS[channel]}</span>
                    </li>
                  ))}
                </ul>
              </SectionCard>

              <SectionCard
                title="Activations that used this template"
                subtitle="What this message has actually been sent for"
                flush
              >
                <DataState
                  loading={activations.initialising}
                  error={activations.error}
                  onRetry={activations.refetch}
                  empty={usedBy.length === 0}
                  emptyTitle="Never used"
                  emptyHint="No activation at this site cites this template."
                  minHeight={180}
                >
                  <DataTable
                    rows={usedBy}
                    columns={columns}
                    getRowId={(row) => row.id}
                    onRowClick={(row) => navigate(emergencyPaths.activationDetail(row.id))}
                    caption="Activations at this site that cite this template, with mode, composition time and status."
                    dense
                  />
                </DataState>
                <div className="px-5 pb-4">
                  <DerivedNote>
                    Matched here from the site's activation register — the service has no endpoint
                    that answers what a template has been used for, and the register itself returns
                    an unpaged window, so an older activation may not appear.
                  </DerivedNote>
                </div>
              </SectionCard>

              <SectionCard title="Provenance">
                <KeyValueGrid
                  items={[
                    { label: 'Template code', value: template.templateCode },
                    { label: 'Site', value: siteCode },
                    { label: 'Lifecycle', value: humanise(template.lifecycle) },
                    { label: 'Created by', value: template.metadata.createdBy },
                    { label: 'Created at', value: formatDateTime(template.metadata.createdAt) },
                    { label: 'Last changed by', value: template.metadata.lastModifiedBy },
                    {
                      label: 'Last changed at',
                      value: formatDateTime(template.metadata.lastModifiedAt),
                    },
                    { label: 'Source channel', value: humanise(template.metadata.sourceChannel) },
                    { label: 'Version', value: template.metadata.version },
                  ]}
                />
              </SectionCard>
            </div>
          </>
        )}
      </DataState>
    </div>
  );
};

export default EmergencyTemplateDetailPage;
