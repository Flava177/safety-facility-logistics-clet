import { useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import Alert from 'shared/components/Alert';
import Button from 'shared/components/Button';
import DataState from 'shared/components/DataState';
import DataTable, { Column } from 'shared/components/DataTable';
import KeyValueGrid from 'shared/components/KeyValueGrid';
import PageHeader from 'shared/components/PageHeader';
import SectionCard from 'shared/components/SectionCard';
import StatusChip from 'shared/components/StatusChip';
import { useNotifier } from 'shared/components/Notifier';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { facilitiesPaths } from 'shared/layout/navigation';
import type { Building } from '../api/dto';
import { changeOperatingMode, getSite, listBuildings } from '../api/facilitiesApi';
import { changeOperatingModeAction } from '../api/workflow';
import OperatingModeDialog from '../dialogs/OperatingModeDialog';
import { formatDateTime, humaniseCode, orDash } from '../components/facilitiesFormat';

/**
 * One site: its record, its operating mode, and the buildings beneath it.
 *
 * The operating-mode control is the only consequential action on the screen, so it is the only one
 * given prominence — and it is hidden outright, not merely disabled, for an actor without
 * `FACILITIES_OPERATING_MODE_CHANGE`. A greyed-out button invites the question "how do I get this?"
 * from someone whose role is deliberately not meant to have it.
 */
const SiteDetailPage = () => {
  const { siteId = '' } = useParams();
  const navigate = useNavigate();
  const notify = useNotifier();
  const [changingMode, setChangingMode] = useState(false);

  const site = useApiQuery((signal) => getSite(siteId, signal), [siteId]);
  const buildings = useApiQuery(
    (signal) => (site.data ? listBuildings(site.data.siteCode, signal) : Promise.resolve([])),
    [site.data?.siteCode],
  );

  const columns: Column<Building>[] = [
    {
      key: 'buildingCode',
      header: 'Code',
      width: 140,
      cell: (building) => <span className="font-medium text-gray-900">{building.buildingCode}</span>,
    },
    { key: 'name', header: 'Building', cell: (building) => building.name },
    {
      key: 'description',
      header: 'Description',
      hideBelowLg: true,
      cell: (building) => <span className="text-gray-600">{orDash(building.description)}</span>,
    },
    {
      key: 'lifecycle',
      header: 'Lifecycle',
      width: 120,
      align: 'right',
      cell: (building) => <StatusChip value={building.lifecycleStatus} />,
    },
  ];

  const modeAction = site.data ? changeOperatingModeAction(site.data) : { allowed: false };

  return (
    <>
      <DataState
        loading={site.loading}
        error={site.error}
        empty={!site.data}
        onRetry={site.refetch}
        minHeight={280}
      >
        {site.data && (
          <>
            <PageHeader
              title={site.data.name}
              subtitle={site.data.siteCode}
              crumbs={[
                { label: 'Facilities', to: facilitiesPaths.dashboard },
                { label: 'Sites', to: facilitiesPaths.sites },
                { label: site.data.siteCode },
              ]}
              actions={
                modeAction.allowed ? (
                  <Button
                    variant={site.data.operatingMode === 'EXAMINATION' ? 'outline' : 'accent'}
                    onClick={() => setChangingMode(true)}
                  >
                    {site.data.operatingMode === 'EXAMINATION'
                      ? 'Stand down examination mode'
                      : 'Declare examination mode'}
                  </Button>
                ) : undefined
              }
            />

            <div className="space-y-5">
              {site.data.operatingMode === 'EXAMINATION' && (
                <Alert variant="warning" title="This centre is in examination mode">
                  Declared by {orDash(site.data.operatingModeChangedBy)} on{' '}
                  {formatDateTime(site.data.operatingModeChangedAt)}. Readiness is assessed against
                  the examination standard and the staleness threshold is tighter.
                </Alert>
              )}

              <SectionCard title="Site record">
                <KeyValueGrid
                  items={[
                    { label: 'Code', value: site.data.siteCode },
                    { label: 'Name', value: site.data.name },
                    { label: 'Description', value: orDash(site.data.description), span: 2 },
                    {
                      label: 'Operating mode',
                      value: (
                        <StatusChip
                          value={site.data.operatingMode}
                          tone={site.data.operatingMode === 'EXAMINATION' ? 'accent' : 'neutral'}
                        />
                      ),
                    },
                    {
                      label: 'Lifecycle',
                      value: <StatusChip value={site.data.lifecycleStatus} />,
                    },
                    { label: 'Created by', value: site.data.metadata.createdBy },
                    {
                      label: 'Last changed',
                      value: `${formatDateTime(site.data.metadata.lastModifiedAt)} by ${site.data.metadata.lastModifiedBy}`,
                    },
                    { label: 'Version', value: String(site.data.metadata.version) },
                    {
                      label: 'Source channel',
                      value: humaniseCode(site.data.metadata.sourceChannel),
                    },
                  ]}
                />
              </SectionCard>

              <SectionCard title="Buildings" subtitle="What stands on this site">
                <DataState
                  loading={buildings.loading}
                  error={buildings.error}
                  onRetry={buildings.refetch}
                  minHeight={80}
                >
                  <DataTable
                    rows={buildings.data ?? []}
                    columns={columns}
                    getRowId={(building) => building.id}
                    emptyMessage="No buildings are registered on this site."
                    dense
                  />
                </DataState>
              </SectionCard>

              <SectionCard
                title="Spaces"
                subtitle="Open the space register filtered to this site"
                actions={
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => navigate(facilitiesPaths.spaces)}
                  >
                    Open space register
                  </Button>
                }
              >
                <p className="text-theme-sm text-gray-600">
                  Spaces are searched rather than listed here — an estate of any size is not
                  browsable, and readiness is the thing worth filtering on.
                </p>
              </SectionCard>
            </div>
          </>
        )}
      </DataState>

      {changingMode && site.data && (
        <OperatingModeDialog
          site={site.data}
          onClose={() => setChangingMode(false)}
          onChanged={async (mode, reason) => {
            await changeOperatingMode(site.data!.id, { operatingMode: mode, reason });
            setChangingMode(false);
            notify.notifySuccess(
              mode === 'EXAMINATION'
                ? 'Examination mode declared for this centre.'
                : 'Centre returned to routine operations.',
            );
            site.refetch();
          }}
        />
      )}
    </>
  );
};

export default SiteDetailPage;
