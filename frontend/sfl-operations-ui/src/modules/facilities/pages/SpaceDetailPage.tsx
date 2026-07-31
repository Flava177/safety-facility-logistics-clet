import { useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import Alert from 'shared/components/Alert';
import Button from 'shared/components/Button';
import DataState from 'shared/components/DataState';
import DataTable, { Column } from 'shared/components/DataTable';
import KeyValueGrid from 'shared/components/KeyValueGrid';
import PageHeader from 'shared/components/PageHeader';
import SectionCard from 'shared/components/SectionCard';
import StatCard from 'shared/components/StatCard';
import StatusChip from 'shared/components/StatusChip';
import { useNotifier } from 'shared/components/Notifier';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { facilitiesPaths } from 'shared/layout/navigation';
import type { FacilityAsset, ReadinessAssessment, ReadinessBlocker } from '../api/dto';
import {
  getSpace,
  getSpaceReadiness,
  listAssessments,
  lockSpaceReadiness,
  resolveBlocker,
  searchAssets,
  unlockSpaceReadiness,
  updateSpaceReadiness,
} from '../api/facilitiesApi';
import { lockAction, unlockAction, canAssessReadiness } from '../api/workflow';
import ReadinessBlockerList from '../components/ReadinessBlockerList';
import ResolveBlockerDialog from '../dialogs/ResolveBlockerDialog';
import SetReadinessDialog from '../dialogs/SetReadinessDialog';
import {
  assetStatusTone,
  formatDateTime,
  humaniseCode,
  orDash,
  readinessTone,
  relativeTime,
  scoreTone,
} from '../components/facilitiesFormat';

/**
 * One space, and everything that decides whether it can be used.
 *
 * Four things are on this screen because an operator standing in front of a blocked hall needs all
 * four to act: the space's own attributes, its readiness with the blockers behind it, the assets in
 * it that might be causing them, and its assessment history. Splitting them across tabs would make
 * the common question — "why is this not ready and what do I do?" — a three-click answer.
 */
const SpaceDetailPage = () => {
  const { roomId = '' } = useParams();
  const navigate = useNavigate();
  const notify = useNotifier();
  const [resolving, setResolving] = useState<ReadinessBlocker | null>(null);
  const [settingReadiness, setSettingReadiness] = useState(false);

  const space = useApiQuery((signal) => getSpace(roomId, signal), [roomId]);
  const readiness = useApiQuery((signal) => getSpaceReadiness(roomId, signal), [roomId]);
  const assets = useApiQuery(
    (signal) => searchAssets({ roomId, size: 50 }, signal),
    [roomId],
  );
  const assessments = useApiQuery(
    (signal) => listAssessments({ roomId, limit: 10 }, signal),
    [roomId],
  );

  const refreshReadiness = () => {
    space.refetch();
    readiness.refetch();
    assets.refetch();
    assessments.refetch();
  };

  const toggleLock = async (lock: boolean) => {
    try {
      await (lock ? lockSpaceReadiness(roomId) : unlockSpaceReadiness(roomId));
      notify.notifySuccess(lock ? 'Space locked for examination use.' : 'Readiness lock released.');
      refreshReadiness();
    } catch (cause) {
      notify.notifyError(cause);
    }
  };

  const assetColumns: Column<FacilityAsset>[] = [
    {
      key: 'assetCode',
      header: 'Code',
      width: 130,
      cell: (asset) => <span className="font-medium text-gray-900">{asset.assetCode}</span>,
    },
    { key: 'name', header: 'Asset', cell: (asset) => asset.name },
    {
      key: 'category',
      header: 'Category',
      hideBelowLg: true,
      cell: (asset) => humaniseCode(asset.category),
    },
    {
      key: 'criticality',
      header: 'Criticality',
      width: 120,
      cell: (asset) => <StatusChip value={asset.criticality} />,
    },
    {
      key: 'status',
      header: 'Condition',
      width: 160,
      cell: (asset) => (
        <StatusChip
          value={asset.operationalStatus}
          tone={assetStatusTone(asset.operationalStatus)}
        />
      ),
    },
  ];

  const assessmentColumns: Column<ReadinessAssessment>[] = [
    {
      key: 'assessedAt',
      header: 'Assessed',
      width: 190,
      cell: (row) => formatDateTime(row.assessedAt),
    },
    { key: 'by', header: 'By', cell: (row) => row.assessedBy },
    {
      key: 'checklist',
      header: 'Checklist',
      hideBelowLg: true,
      cell: (row) => (row.checklistCode ? `${row.checklistCode} v${row.checklistVersion}` : '—'),
    },
    {
      key: 'score',
      header: 'Score',
      align: 'right',
      width: 90,
      cell: (row) => `${row.score}%`,
    },
    {
      key: 'outcome',
      header: 'Outcome',
      width: 130,
      align: 'right',
      cell: (row) => <StatusChip value={row.outcome} tone={readinessTone(row.outcome)} />,
    },
  ];

  return (
    <>
      <DataState
        loading={space.loading}
        error={space.error}
        empty={!space.data}
        onRetry={space.refetch}
        minHeight={280}
      >
        {space.data && (
          <>
            <PageHeader
              title={space.data.name}
              subtitle={`${space.data.roomCode} · ${humaniseCode(space.data.spaceType)} · ${space.data.siteCode}`}
              crumbs={[
                { label: 'Facilities', to: facilitiesPaths.dashboard },
                { label: 'Spaces', to: facilitiesPaths.spaces },
                { label: space.data.roomCode },
              ]}
              actions={
                <div className="flex flex-wrap gap-2">
                  {canAssessReadiness() && (
                    <Button
                      variant="primary"
                      onClick={() =>
                        navigate(`${facilitiesPaths.assessments}?roomId=${space.data!.id}`)
                      }
                    >
                      Assess readiness
                    </Button>
                  )}
                  {/*
                    The manual override. An assessment is the ordinary route and it computes the
                    outcome; this is for the times there is no checklist to answer — a burst pipe,
                    or the space coming back after one. Gated on the same permission, because it
                    reaches the same state by a shorter path.
                  */}
                  {canAssessReadiness() && (
                    <Button variant="outline" onClick={() => setSettingReadiness(true)}>
                      Set readiness
                    </Button>
                  )}
                  {(() => {
                    // Only the applicable half of the lock pair is rendered, and it is disabled
                    // with the reason when the actor or the record forbids it — an offered button
                    // that answers 403 has misled the operator before they clicked it.
                    const action = space.data!.readinessLocked
                      ? unlockAction(space.data!)
                      : lockAction(space.data!);
                    return (
                      <Button
                        variant="outline"
                        disabled={!action.allowed}
                        title={action.reason}
                        onClick={() => toggleLock(!space.data!.readinessLocked)}
                      >
                        {space.data!.readinessLocked ? 'Release lock' : 'Lock for examination'}
                      </Button>
                    );
                  })()}
                </div>
              }
            />

            <div className="space-y-5">
              {space.data.readinessLocked && (
                <Alert variant="info" title="Locked for examination use">
                  Locked by {orDash(space.data.readinessLockedBy)} on{' '}
                  {formatDateTime(space.data.readinessLockedAt)}. Attribute and lifecycle changes are
                  refused until the lock is released; readiness can still be reassessed.
                </Alert>
              )}

              <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
                <StatCard
                  label="Readiness"
                  value={humaniseCode(space.data.readinessStatus)}
                  icon="shield-check"
                  tone={
                    space.data.readinessStatus === 'READY'
                      ? 'good'
                      : space.data.readinessStatus === 'BLOCKED'
                        ? 'critical'
                        : space.data.readinessStatus === 'DEGRADED'
                          ? 'caution'
                          : 'neutral'
                  }
                  caption={`Assessed ${relativeTime(space.data.readinessUpdatedAt)}`}
                />
                <StatCard
                  label="Score"
                  value={readiness.data ? `${readiness.data.score}%` : '—'}
                  icon="gauge"
                  tone={
                    readiness.data
                      ? scoreTone(readiness.data.score) === 'ready'
                        ? 'good'
                        : scoreTone(readiness.data.score) === 'caution'
                          ? 'caution'
                          : 'critical'
                      : 'neutral'
                  }
                  caption="Weighted checklist result"
                />
                <StatCard
                  label="Bookable"
                  value={space.data.availableForBooking ? 'Yes' : 'No'}
                  icon="calendar"
                  tone={space.data.availableForBooking ? 'good' : 'caution'}
                  caption={space.data.bookable ? 'Flagged bookable' : 'Not a bookable space'}
                />
                <StatCard
                  label="Examination"
                  value={space.data.availableForExamination ? 'Ready' : 'Not ready'}
                  icon="clipboard"
                  tone={space.data.availableForExamination ? 'good' : 'critical'}
                  caption={
                    space.data.examinationCapable
                      ? 'Capable of hosting an examination'
                      : 'Not examination-capable'
                  }
                />
              </div>

              <SectionCard
                title="Open blockers"
                subtitle={readiness.data?.summary}
                actions={
                  <Button variant="ghost" size="sm" onClick={refreshReadiness}>
                    Refresh
                  </Button>
                }
              >
                <DataState
                  loading={readiness.loading}
                  error={readiness.error}
                  onRetry={readiness.refetch}
                  minHeight={80}
                >
                  <ReadinessBlockerList
                    blockers={readiness.data?.openBlockers ?? []}
                    clearMessage="No open blockers. This space is clear."
                    onResolve={canAssessReadiness() ? setResolving : undefined}
                  />
                </DataState>
              </SectionCard>

              <SectionCard title="Space record">
                <KeyValueGrid
                  items={[
                    { label: 'Code', value: space.data.roomCode },
                    { label: 'Type', value: humaniseCode(space.data.spaceType) },
                    { label: 'Capacity', value: orDash(space.data.capacity) },
                    {
                      label: 'Area',
                      value: space.data.areaSqm ? `${space.data.areaSqm} m²` : '—',
                    },
                    { label: 'Cost centre', value: orDash(space.data.costCentre) },
                    { label: 'Lifecycle', value: humaniseCode(space.data.lifecycleStatus) },
                    { label: 'Created by', value: space.data.metadata.createdBy },
                    {
                      label: 'Last changed',
                      value: `${formatDateTime(space.data.metadata.lastModifiedAt)} by ${space.data.metadata.lastModifiedBy}`,
                    },
                    { label: 'Version', value: String(space.data.metadata.version) },
                  ]}
                />
              </SectionCard>

              <SectionCard
                title="Assets in this space"
                subtitle="Fixed plant whose condition feeds this space's readiness"
              >
                <DataState
                  loading={assets.loading}
                  error={assets.error}
                  onRetry={assets.refetch}
                  minHeight={80}
                >
                  <DataTable
                    rows={assets.data?.items ?? []}
                    columns={assetColumns}
                    getRowId={(asset) => asset.id}
                    onRowClick={(asset) => navigate(facilitiesPaths.assetDetail(asset.id))}
                    emptyMessage="No assets are registered in this space."
                    dense
                  />
                </DataState>
              </SectionCard>

              <SectionCard title="Assessment history" subtitle="Most recent first">
                <DataState
                  loading={assessments.loading}
                  error={assessments.error}
                  onRetry={assessments.refetch}
                  minHeight={80}
                >
                  <DataTable
                    rows={assessments.data ?? []}
                    columns={assessmentColumns}
                    getRowId={(row) => row.id}
                    onRowClick={(row) => navigate(facilitiesPaths.assessmentDetail(row.id))}
                    emptyMessage="This space has never been assessed."
                    dense
                  />
                </DataState>
              </SectionCard>
            </div>
          </>
        )}
      </DataState>

      {resolving && (
        <ResolveBlockerDialog
          blocker={resolving}
          onClose={() => setResolving(null)}
          onResolved={async (notes) => {
            await resolveBlocker(resolving.id, { resolutionNotes: notes });
            setResolving(null);
            notify.notifySuccess('Blocker resolved. Readiness has been re-derived.');
            refreshReadiness();
          }}
        />
      )}

      {settingReadiness && space.data && (
        <SetReadinessDialog
          space={space.data}
          // The blockers the dialog reasons about are the ones already on screen, so the count in a
          // refusal is the count the operator can see above it.
          openBlockers={readiness.data?.openBlockers ?? []}
          onClose={() => setSettingReadiness(false)}
          onSubmit={async (status, notes) => {
            await updateSpaceReadiness(roomId, { status, notes });
            setSettingReadiness(false);
            notify.notifySuccess(`Readiness set to ${humaniseCode(status).toLowerCase()}.`);
            refreshReadiness();
          }}
        />
      )}
    </>
  );
};

export default SpaceDetailPage;
