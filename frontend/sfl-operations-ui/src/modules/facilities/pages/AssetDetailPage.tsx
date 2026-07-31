import { useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import Alert from 'shared/components/Alert';
import Button from 'shared/components/Button';
import DataState from 'shared/components/DataState';
import KeyValueGrid from 'shared/components/KeyValueGrid';
import PageHeader from 'shared/components/PageHeader';
import SectionCard from 'shared/components/SectionCard';
import StatCard from 'shared/components/StatCard';
import StatusChip from 'shared/components/StatusChip';
import { useNotifier } from 'shared/components/Notifier';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { facilitiesPaths } from 'shared/layout/navigation';
import { changeAssetStatus, getAsset, getSpace } from '../api/facilitiesApi';
import { changeAssetStatusAction } from '../api/workflow';
import AssetStatusDialog from '../dialogs/AssetStatusDialog';
import {
  formatDate,
  formatDateTime,
  humaniseCode,
  orDash,
  relativeTime,
} from '../components/facilitiesFormat';

/**
 * One facility asset.
 *
 * The screen leads with the space it serves, because that is the consequence an operator is actually
 * managing: an asset's condition matters here only insofar as it decides whether a hall can be used.
 * An asset attached to no space says so plainly rather than leaving a blank.
 */
const AssetDetailPage = () => {
  const { assetId = '' } = useParams();
  const navigate = useNavigate();
  const notify = useNotifier();
  const [changingStatus, setChangingStatus] = useState(false);

  const asset = useApiQuery((signal) => getAsset(assetId, signal), [assetId]);
  const space = useApiQuery(
    (signal) => (asset.data?.roomId ? getSpace(asset.data.roomId, signal) : Promise.resolve(null)),
    [asset.data?.roomId],
  );

  const statusAction = asset.data ? changeAssetStatusAction(asset.data) : { allowed: false };
  const today = new Date();
  const overdue =
    asset.data?.serviceDueOn !== null &&
    asset.data?.serviceDueOn !== undefined &&
    new Date(asset.data.serviceDueOn) < today;

  return (
    <>
      <DataState
        loading={asset.loading}
        error={asset.error}
        empty={!asset.data}
        onRetry={asset.refetch}
        minHeight={280}
      >
        {asset.data && (
          <>
            <PageHeader
              title={asset.data.name}
              subtitle={`${asset.data.assetCode} · ${humaniseCode(asset.data.category)} · ${asset.data.siteCode}`}
              crumbs={[
                { label: 'Facilities', to: facilitiesPaths.dashboard },
                { label: 'Assets', to: facilitiesPaths.assets },
                { label: asset.data.assetCode },
              ]}
              actions={
                <Button
                  variant="primary"
                  disabled={!statusAction.allowed}
                  title={statusAction.reason}
                  onClick={() => setChangingStatus(true)}
                >
                  Change condition
                </Button>
              }
            />

            <div className="space-y-5">
              {asset.data.impairsReadiness && (
                <Alert variant="error" title="This asset is impairing a space">
                  {asset.data.assetCode} is {humaniseCode(asset.data.operationalStatus).toLowerCase()}{' '}
                  and is raising a readiness blocker
                  {space.data ? ` on ${space.data.roomCode} — ${space.data.name}` : ''}. Returning it
                  to service resolves that blocker.
                </Alert>
              )}

              <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
                <StatCard
                  label="Condition"
                  value={humaniseCode(asset.data.operationalStatus)}
                  icon="wrench"
                  tone={
                    asset.data.operationalStatus === 'OPERATIONAL'
                      ? 'good'
                      : asset.data.operationalStatus === 'OUT_OF_SERVICE'
                        ? 'critical'
                        : asset.data.operationalStatus === 'DECOMMISSIONED'
                          ? 'neutral'
                          : 'caution'
                  }
                  caption={
                    asset.data.statusChangedAt
                      ? `Changed ${relativeTime(asset.data.statusChangedAt)}`
                      : 'Never changed'
                  }
                />
                <StatCard
                  label="Criticality"
                  value={humaniseCode(asset.data.criticality)}
                  icon="alert-triangle"
                  tone={
                    asset.data.criticality === 'CRITICAL'
                      ? 'critical'
                      : asset.data.criticality === 'HIGH'
                        ? 'caution'
                        : 'neutral'
                  }
                  caption="Sets the severity of any blocker it raises"
                />
                <StatCard
                  label="Service due"
                  value={formatDate(asset.data.serviceDueOn)}
                  icon="clock"
                  tone={overdue ? 'critical' : asset.data.serviceDueOn ? 'neutral' : 'neutral'}
                  caption={
                    asset.data.serviceIntervalDays
                      ? `Every ${asset.data.serviceIntervalDays} days`
                      : 'Not on a service schedule'
                  }
                />
                <StatCard
                  label="Warranty"
                  value={formatDate(asset.data.warrantyExpiresOn)}
                  icon="shield-check"
                  tone="neutral"
                  caption={asset.data.manufacturer ?? 'No manufacturer recorded'}
                />
              </div>

              <SectionCard
                title="Location"
                subtitle="The space whose readiness this asset's condition feeds"
                actions={
                  space.data ? (
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => navigate(facilitiesPaths.spaceDetail(space.data!.id))}
                    >
                      Open space
                    </Button>
                  ) : undefined
                }
              >
                {space.data ? (
                  <KeyValueGrid
                    items={[
                      { label: 'Space', value: `${space.data.roomCode} — ${space.data.name}` },
                      {
                        label: 'Space readiness',
                        value: <StatusChip value={space.data.readinessStatus} />,
                      },
                      { label: 'Location code', value: orDash(asset.data.locationCode) },
                    ]}
                  />
                ) : (
                  <Alert variant="info">
                    This asset is not attached to a space, so its condition raises no readiness
                    blocker. Relocate it to a space if it should.
                  </Alert>
                )}
              </SectionCard>

              <SectionCard title="Asset record">
                <KeyValueGrid
                  items={[
                    { label: 'Code', value: asset.data.assetCode },
                    { label: 'Category', value: humaniseCode(asset.data.category) },
                    { label: 'Manufacturer', value: orDash(asset.data.manufacturer) },
                    { label: 'Model', value: orDash(asset.data.modelNumber) },
                    { label: 'Serial', value: orDash(asset.data.serialNumber) },
                    { label: 'Custodian', value: orDash(asset.data.custodian) },
                    { label: 'Installed', value: formatDate(asset.data.installedOn) },
                    { label: 'Last serviced', value: formatDate(asset.data.lastServicedOn) },
                    { label: 'Lifecycle', value: humaniseCode(asset.data.lifecycleStatus) },
                    { label: 'Status notes', value: orDash(asset.data.statusNotes), span: 2 },
                    {
                      label: 'AVAMP reference',
                      value: orDash(asset.data.assetReferenceId),
                    },
                    {
                      label: 'Last changed',
                      value: `${formatDateTime(asset.data.metadata.lastModifiedAt)} by ${asset.data.metadata.lastModifiedBy}`,
                    },
                  ]}
                />
              </SectionCard>
            </div>
          </>
        )}
      </DataState>

      {changingStatus && asset.data && (
        <AssetStatusDialog
          asset={asset.data}
          onClose={() => setChangingStatus(false)}
          onChanged={async (status, notes) => {
            await changeAssetStatus(asset.data!.id, { operationalStatus: status, notes });
            setChangingStatus(false);
            notify.notifySuccess(
              'Condition changed. The readiness of the space it serves has been re-derived.',
            );
            asset.refetch();
            space.refetch();
          }}
        />
      )}
    </>
  );
};

export default AssetDetailPage;
