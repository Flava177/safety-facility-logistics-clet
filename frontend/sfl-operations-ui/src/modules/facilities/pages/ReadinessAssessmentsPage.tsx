import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import Button from 'shared/components/Button';
import DataState from 'shared/components/DataState';
import DataTable, { Column } from 'shared/components/DataTable';
import FilterBar from 'shared/components/FilterBar';
import PageHeader from 'shared/components/PageHeader';
import SiteSelect, { defaultSite } from 'shared/components/SiteSelect';
import StatusChip from 'shared/components/StatusChip';
import { useNotifier } from 'shared/components/Notifier';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { facilitiesPaths } from 'shared/layout/navigation';
import type { ReadinessAssessment } from '../api/dto';
import {
  getSpace,
  listAssessments,
  listChecklists,
  submitAssessment,
} from '../api/facilitiesApi';
import { canAssessReadiness } from '../api/workflow';
import SubmitAssessmentDialog from '../dialogs/SubmitAssessmentDialog';
import { formatDateTime, readinessTone, scoreTone } from '../components/facilitiesFormat';

/**
 * The assessment register, and the entry point for taking a new one.
 *
 * Reached with `?roomId=` from a space, which is how the field workflow starts: an assessor opens the
 * space they are standing in and taps through to assess it. Without the parameter it is a register of
 * what has been assessed recently across the site.
 */
const ReadinessAssessmentsPage = () => {
  const navigate = useNavigate();
  const notify = useNotifier();
  const [params, setParams] = useSearchParams();
  const roomId = params.get('roomId') ?? '';

  const [siteCode, setSiteCode] = useState<string>(defaultSite);
  const [assessing, setAssessing] = useState(false);

  const assessments = useApiQuery(
    (signal) =>
      listAssessments(
        { siteCode: roomId ? undefined : siteCode || undefined, roomId: roomId || undefined, limit: 50 },
        signal,
      ),
    [siteCode, roomId],
  );

  /** Only loaded when a space is in context — the dialog needs both to offer a checklist. */
  const space = useApiQuery(
    (signal) => (roomId ? getSpace(roomId, signal) : Promise.resolve(null)),
    [roomId],
  );
  const checklists = useApiQuery(
    (signal) => (space.data ? listChecklists(space.data.siteCode, signal) : Promise.resolve([])),
    [space.data?.siteCode],
  );

  const columns: Column<ReadinessAssessment>[] = [
    {
      key: 'assessedAt',
      header: 'Assessed',
      width: 190,
      cell: (row) => formatDateTime(row.assessedAt),
    },
    { key: 'assessedBy', header: 'By', cell: (row) => row.assessedBy },
    {
      key: 'checklist',
      header: 'Checklist',
      hideBelowLg: true,
      cell: (row) => (row.checklistCode ? `${row.checklistCode} v${row.checklistVersion}` : '—'),
    },
    {
      key: 'mode',
      header: 'Mode',
      width: 130,
      hideBelowLg: true,
      cell: (row) => (
        <StatusChip
          value={row.operatingMode}
          tone={row.operatingMode === 'EXAMINATION' ? 'accent' : 'neutral'}
        />
      ),
    },
    {
      key: 'score',
      header: 'Score',
      align: 'right',
      width: 90,
      cell: (row) => (
        <span
          className={
            scoreTone(row.score) === 'blocked'
              ? 'font-medium text-error-800'
              : scoreTone(row.score) === 'caution'
                ? 'font-medium text-warning-700'
                : 'text-gray-700'
          }
        >
          {row.score}%
        </span>
      ),
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
      <PageHeader
        title="Readiness assessments"
        subtitle={
          space.data
            ? `${space.data.roomCode} — ${space.data.name}`
            : 'Every inspection recorded against a space'
        }
        crumbs={
          space.data
            ? [
                { label: 'Facilities', to: facilitiesPaths.dashboard },
                { label: 'Spaces', to: facilitiesPaths.spaces },
                { label: space.data.roomCode, to: facilitiesPaths.spaceDetail(space.data.id) },
                { label: 'Assessments' },
              ]
            : undefined
        }
        actions={
          space.data && canAssessReadiness() ? (
            <Button variant="primary" onClick={() => setAssessing(true)}>
              New assessment
            </Button>
          ) : undefined
        }
      />

      {!roomId && (
        <FilterBar>
          <SiteSelect value={siteCode} onChange={setSiteCode} allowEmpty emptyLabel="All sites" />
        </FilterBar>
      )}

      {roomId && (
        <div className="mb-4">
          <Button variant="link" size="sm" onClick={() => setParams({})}>
            Show every space instead
          </Button>
        </div>
      )}

      <DataState
        loading={assessments.loading}
        error={assessments.error}
        empty={!assessments.data || assessments.data.length === 0}
        emptyTitle="Nothing assessed yet"
        emptyHint={
          space.data
            ? 'This space has never been assessed. An unassessed space reports as UNKNOWN, not ready.'
            : 'No assessment has been recorded for this site.'
        }
        onRetry={assessments.refetch}
      >
        {assessments.data && (
          <DataTable
            rows={assessments.data}
            columns={columns}
            getRowId={(row) => row.id}
            onRowClick={(row) => navigate(facilitiesPaths.assessmentDetail(row.id))}
            caption="Readiness assessments"
          />
        )}
      </DataState>

      {assessing && space.data && (
        <SubmitAssessmentDialog
          space={space.data}
          checklists={checklists.data ?? []}
          onClose={() => setAssessing(false)}
          onSubmitted={async (request) => {
            const result = await submitAssessment(request);
            setAssessing(false);
            notify.notifySuccess(
              `Assessment recorded — ${result.outcome.toLowerCase()} at ${result.score}%.`,
              result.outcome === 'BLOCKED'
                ? 'Critical checks failed, so the space is blocked until they are resolved.'
                : undefined,
            );
            assessments.refetch();
            space.refetch();
          }}
        />
      )}
    </>
  );
};

export default ReadinessAssessmentsPage;
