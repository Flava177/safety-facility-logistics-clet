import { useState } from 'react';
import { useNavigate } from 'react-router';
import Alert from 'shared/components/Alert';
import DataState from 'shared/components/DataState';
import DataTable, { CellStack, Column } from 'shared/components/DataTable';
import FilterBar from 'shared/components/FilterBar';
import PageHeader from 'shared/components/PageHeader';
import SiteSelect, { defaultSite } from 'shared/components/SiteSelect';
import StatusChip from 'shared/components/StatusChip';
import { DateTimeField } from 'shared/components/DateField';
import { useNotifier } from 'shared/components/Notifier';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { bookingPaths } from 'shared/layout/navigation';
import { formatDateTime, orDash } from 'modules/facilities/components/facilitiesFormat';
import { setupTasksApi } from '../api/bookingApi';
import type { SetupTask } from '../api/dto';
import { canResolveSetupTask } from '../api/workflow';
import ControlButton from '../components/ControlButton';
import { fromLocalInput, setupTaskTone } from '../components/bookingFormat';
import ResolveSetupTaskDialog from '../dialogs/ResolveSetupTaskDialog';

/**
 * The room-turnaround queue — SRS-SFL-S159-02.
 *
 * ## Ordered by when the room is needed, not when the task was raised
 *
 * That ordering is the service's, and it is the whole value of the screen: a task for this afternoon
 * matters more than one raised last week for next month, and a created-at ordering gets that
 * backwards every single time.
 *
 * ## Why these are not S153 work orders
 *
 * Routing them there would buy the queue, the SLA and the closure evidence for free. It would also
 * put a twenty-minute chair rearrangement in the same queue as a failed standby generator, where the
 * generator ends up on page four. The two queues are separate because the work is not comparable.
 */
const SetupTaskQueuePage = () => {
  const navigate = useNavigate();
  const notify = useNotifier();
  const [siteCode, setSiteCode] = useState(defaultSite);
  const [dueBefore, setDueBefore] = useState('');
  const [resolving, setResolving] = useState<SetupTask | null>(null);

  const tasks = useApiQuery(
    (signal) =>
      setupTasksApi.queue(
        {
          siteCode: siteCode || undefined,
          dueBefore: dueBefore ? fromLocalInput(dueBefore) : undefined,
          limit: 200,
        },
        signal,
      ),
    [siteCode, dueBefore],
  );

  const rows = tasks.data ?? [];
  const overdue = rows.filter((task) => task.overdue && task.status === 'PENDING').length;

  const columns: Column<SetupTask>[] = [
    {
      key: 'description',
      header: 'Task',
      cell: (task) => (
        <CellStack primary={task.description} secondary={task.siteCode} />
      ),
    },
    {
      key: 'dueBy',
      header: 'Room needed by',
      width: 200,
      cell: (task) => (
        <span className={task.overdue && task.status === 'PENDING' ? 'font-medium text-error-800' : undefined}>
          {task.dueBy ? formatDateTime(task.dueBy) : 'No time set'}
        </span>
      ),
    },
    {
      key: 'assignedTo',
      header: 'Assigned',
      hideBelowLg: true,
      cell: (task) => orDash(task.assignedTo),
    },
    {
      key: 'status',
      header: 'Status',
      width: 120,
      cell: (task) => <StatusChip value={task.status} tone={setupTaskTone(task.status)} />,
    },
    {
      key: 'resolve',
      header: 'Resolve',
      align: 'right',
      width: 150,
      cell: (task) => (
        <ControlButton
          state={canResolveSetupTask(task)}
          size="sm"
          variant="outline"
          onClick={() => setResolving(task)}
        >
          Resolve
        </ControlButton>
      ),
    },
  ];

  return (
    <>
      <PageHeader
        title="Room turnaround"
        subtitle="What has to happen to a room before its next booking, most urgent first"
      />

      <FilterBar onReset={() => setDueBefore('')} resetDisabled={!dueBefore}>
        <SiteSelect value={siteCode} onChange={setSiteCode} allowEmpty emptyLabel="All sites" />
        <DateTimeField
          label="Needed before"
          value={dueBefore}
          onChange={setDueBefore}
          placeholder="Next two days"
          helperText="Blank shows the service's default window."
        />
      </FilterBar>

      {overdue > 0 && (
        <Alert variant="warning" title={`${overdue} past when the room was needed`} className="mb-5">
          The booking each belongs to may already have started. Resolving one still records the
          outcome.
        </Alert>
      )}

      <DataState
        loading={tasks.loading}
        error={tasks.error}
        empty={rows.length === 0}
        emptyTitle="Nothing to set up"
        emptyHint="No booking in this window takes a resource that needs setting up."
        onRetry={tasks.refetch}
      >
        <DataTable
          rows={rows}
          columns={columns}
          getRowId={(task) => task.id}
          onRowClick={(task) => navigate(bookingPaths.bookingDetail(task.bookingId))}
          caption="Room turnaround queue"
        />
      </DataState>

      {resolving && (
        <ResolveSetupTaskDialog
          task={resolving}
          onClose={() => setResolving(null)}
          onSubmit={async (body) => {
            await setupTasksApi.resolve(resolving.id, body);
            setResolving(null);
            notify.notifySuccess(
              body.outcome === 'DONE' ? 'Marked done.' : 'Marked skipped, with your reason.',
            );
            tasks.refetch();
          }}
        />
      )}
    </>
  );
};

export default SetupTaskQueuePage;
