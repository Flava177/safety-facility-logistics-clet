import { useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { Alert, Box, Button, Stack, Typography } from '@mui/material';
import { WorkflowItemResponse } from 'modules/fleet/api/dto';
import { humanise } from 'modules/fleet/api/enums';
import { workflowApi } from 'modules/fleet/api/fleetApi';
import {
  AddCommentDialog,
  AssignWorkflowItemDialog,
  CloseWorkflowItemDialog,
  ReasonTransitionDialog,
} from 'modules/fleet/dialogs/workflowDialogs';
import DataState from 'shared/components/DataState';
import KeyValueGrid from 'shared/components/KeyValueGrid';
import { useNotifier } from 'shared/components/Notifier';
import PageHeader from 'shared/components/PageHeader';
import SectionCard from 'shared/components/SectionCard';
import StatusChip from 'shared/components/StatusChip';
import WorkflowTimeline, { TimelineEntry } from 'shared/components/WorkflowTimeline';
import { formatDateTime } from 'shared/components/format';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { fleetPaths } from 'shared/layout/navigation';
import IconifyIcon from 'components/base/IconifyIcon';

type DialogKey =
  | 'assign'
  | 'close'
  | 'comment'
  | 'escalate'
  | 'cancel'
  | 'reopen'
  | 'hold'
  | 'resume'
  | null;

const live = (item: WorkflowItemResponse) => !['CLOSED', 'CANCELLED'].includes(item.status);

/**
 * Workflow item detail with its immutable history.
 *
 * Transitions and comments are merged into one timeline in sequence order — that ordering is the
 * audit record, so it is presented rather than re-sorted by kind.
 */
const WorkflowDetailPage = () => {
  const { itemId = '' } = useParams();
  const navigate = useNavigate();
  const { notifyError, notifySuccess } = useNotifier();
  const [dialog, setDialog] = useState<DialogKey>(null);

  const item = useApiQuery((signal) => workflowApi.findById(itemId, signal), [itemId]);
  const history = useApiQuery((signal) => workflowApi.history(itemId, signal), [itemId]);

  const refreshAll = () => {
    item.refetch();
    history.refetch();
  };

  const [starting, setStarting] = useState(false);

  const startItem = async () => {
    setStarting(true);
    try {
      await workflowApi.start(itemId, { expectedVersion: item.data?.version });
      notifySuccess('Item moved to in progress.');
      refreshAll();
    } catch (error) {
      // A refused transition is never silent — the service's own wording is shown.
      notifyError(error);
    } finally {
      setStarting(false);
    }
  };

  const timeline: TimelineEntry[] = [
    ...(history.data?.transitions ?? []).map<TimelineEntry>((transition) => ({
      id: `t-${transition.id}`,
      title: `${humanise(transition.action)}${
        transition.toStatus ? ` → ${humanise(transition.toStatus)}` : ''
      }`,
      detail: transition.reason,
      actor: transition.actorId,
      occurredAt: transition.occurredAt,
      tone:
        transition.action === 'ESCALATED' || transition.action === 'CANCELLED'
          ? 'danger'
          : transition.action === 'CLOSED'
            ? 'accent'
            : 'default',
    })),
    ...(history.data?.comments ?? []).map<TimelineEntry>((comment) => ({
      id: `c-${comment.id}`,
      title: 'Comment',
      detail: comment.body,
      actor: comment.author,
      occurredAt: comment.occurredAt,
    })),
  ].sort((left, right) => left.occurredAt.localeCompare(right.occurredAt));

  return (
    <Box>
      <PageHeader
        title={item.data?.workflowNumber ?? 'Workflow item'}
        subtitle={item.data?.title}
        crumbs={[
          { label: 'Fleet', to: fleetPaths.dashboard },
          { label: 'Workflow queue', to: fleetPaths.workflow },
          { label: item.data?.workflowNumber ?? '…' },
        ]}
        actions={
          <Button
            variant="soft"
            color="neutral"
            onClick={() => navigate(fleetPaths.workflow)}
            startIcon={<IconifyIcon icon="material-symbols:arrow-back-rounded" />}
          >
            Queue
          </Button>
        }
        meta={
          item.data && (
            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
              <StatusChip value={item.data.status} />
              <StatusChip value={item.data.priority} />
              <StatusChip value={item.data.severity} />
              <StatusChip value={item.data.workflowType} tone="neutral" />
            </Stack>
          )
        }
      />

      <DataState
        loading={item.initialising}
        error={item.error}
        onRetry={item.refetch}
        minHeight={300}
      >
        {item.data && (
          <Stack spacing={2.5}>
            {item.data.slaBreached && (
              <Alert severity="error">
                This item has breached its configured SLA and has been escalated.
              </Alert>
            )}

            <SectionCard title="Actions">
              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                {live(item.data) && (
                  <Button variant="contained" color="secondary" onClick={() => setDialog('assign')}>
                    {item.data.assignee ? 'Reassign' : 'Assign'}
                  </Button>
                )}
                {['OPEN', 'ASSIGNED', 'REOPENED'].includes(item.data.status) && (
                  <Button variant="soft" color="neutral" onClick={startItem} disabled={starting}>
                    Start work
                  </Button>
                )}
                {['ASSIGNED', 'IN_PROGRESS', 'OPEN'].includes(item.data.status) && (
                  <Button variant="soft" color="neutral" onClick={() => setDialog('hold')}>
                    Hold
                  </Button>
                )}
                {item.data.status === 'ON_HOLD' && (
                  <Button variant="soft" color="neutral" onClick={() => setDialog('resume')}>
                    Resume
                  </Button>
                )}
                {live(item.data) && (
                  <Button variant="soft" color="error" onClick={() => setDialog('escalate')}>
                    Escalate
                  </Button>
                )}
                {live(item.data) && (
                  <Button variant="contained" color="secondary" onClick={() => setDialog('close')}>
                    Close
                  </Button>
                )}
                {live(item.data) && (
                  <Button variant="soft" color="error" onClick={() => setDialog('cancel')}>
                    Cancel
                  </Button>
                )}
                {item.data.status === 'CLOSED' && (
                  <Button variant="soft" color="neutral" onClick={() => setDialog('reopen')}>
                    Reopen
                  </Button>
                )}
                <Button variant="text" onClick={() => setDialog('comment')}>
                  Add comment
                </Button>
              </Stack>
            </SectionCard>

            <Box
              sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', lg: '1.3fr 1fr' } }}
            >
              <SectionCard title="Item">
                <Stack spacing={2}>
                  <KeyValueGrid
                    items={[
                      { label: 'Workflow number', value: item.data.workflowNumber },
                      { label: 'Type', value: humanise(item.data.workflowType) },
                      { label: 'Site', value: item.data.siteCode },
                      { label: 'Operating mode', value: humanise(item.data.operatingMode) },
                      { label: 'Assignee', value: item.data.assignee ?? 'Unassigned' },
                      { label: 'Escalation level', value: item.data.escalationLevel },
                      { label: 'SLA due', value: formatDateTime(item.data.slaDueAt) },
                      { label: 'Response due', value: formatDateTime(item.data.responseDueAt) },
                      { label: 'First response', value: formatDateTime(item.data.firstResponseAt) },
                      {
                        label: 'Related record',
                        value: item.data.relatedRecordType
                          ? `${item.data.relatedRecordType} ${item.data.relatedRecordId ?? ''}`
                          : '—',
                        span: 2,
                      },
                      { label: 'Description', value: item.data.description, span: 2 },
                      ...(item.data.holdReason
                        ? [{ label: 'Hold reason', value: item.data.holdReason, span: 2 as const }]
                        : []),
                      ...(item.data.closureReason
                        ? [
                            {
                              label: 'Closure reason',
                              value: item.data.closureReason,
                              span: 2 as const,
                            },
                            {
                              label: 'Closure evidence',
                              value: item.data.closureEvidenceId ?? '—',
                            },
                            { label: 'Closed by', value: item.data.closedBy ?? '—' },
                            { label: 'Closed at', value: formatDateTime(item.data.closedAt) },
                          ]
                        : []),
                      { label: 'Raised by', value: item.data.createdBy ?? '—' },
                      { label: 'Raised at', value: formatDateTime(item.data.createdAt) },
                      { label: 'Record version', value: item.data.version },
                    ]}
                  />
                </Stack>
              </SectionCard>

              <SectionCard
                title="History"
                subtitle="Append-only transitions and comments"
                actions={
                  <Button variant="text" size="small" onClick={history.refetch}>
                    Refresh
                  </Button>
                }
              >
                <DataState
                  loading={history.initialising}
                  error={history.error}
                  onRetry={history.refetch}
                  minHeight={140}
                >
                  <WorkflowTimeline entries={timeline} />
                </DataState>
              </SectionCard>
            </Box>

            {!live(item.data) && (
              <Typography variant="body2" color="text.secondary">
                This item is {humanise(item.data.status).toLowerCase()}. Its history is immutable.
              </Typography>
            )}

            <AssignWorkflowItemDialog
              open={dialog === 'assign'}
              item={item.data}
              onClose={() => setDialog(null)}
              onSaved={() => {
                notifySuccess('Assignment updated.');
                refreshAll();
              }}
            />
            <CloseWorkflowItemDialog
              open={dialog === 'close'}
              item={item.data}
              onClose={() => setDialog(null)}
              onSaved={() => {
                notifySuccess('Item closed.');
                refreshAll();
              }}
            />
            <AddCommentDialog
              open={dialog === 'comment'}
              item={item.data}
              onClose={() => setDialog(null)}
              onSaved={() => {
                notifySuccess('Comment added.');
                refreshAll();
              }}
            />
            {(dialog === 'escalate' ||
              dialog === 'cancel' ||
              dialog === 'reopen' ||
              dialog === 'hold' ||
              dialog === 'resume') && (
              <ReasonTransitionDialog
                open
                transition={dialog}
                item={item.data}
                onClose={() => setDialog(null)}
                onSaved={() => {
                  notifySuccess('Transition applied.');
                  refreshAll();
                }}
              />
            )}
          </Stack>
        )}
      </DataState>
    </Box>
  );
};

export default WorkflowDetailPage;
