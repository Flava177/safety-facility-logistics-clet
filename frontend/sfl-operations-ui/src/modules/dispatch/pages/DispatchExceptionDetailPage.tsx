import { useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { EXCEPTION_TYPE_DESCRIPTIONS, ExceptionType } from 'modules/dispatch/api/enums';
import { ExceptionAction, dispatchExceptionsApi } from 'modules/dispatch/api/dispatchApi';
import {
  EXCEPTION_RULES,
  exceptionActionAllowed,
  exceptionClosureBlockers,
  exceptionOpen,
  exceptionSlaBreached,
} from 'modules/dispatch/api/workflow';
import { ExceptionActionDialog } from 'modules/dispatch/dialogs/exceptionDialogs';
import { formatDueIn, siteOf } from 'modules/fuel/components/fuelFormat';
import { humanise } from 'modules/fleet/api/enums';
import Alert from 'shared/components/Alert';
import Button from 'shared/components/Button';
import DataState from 'shared/components/DataState';
import Icon from 'shared/components/Icon';
import KeyValueGrid from 'shared/components/KeyValueGrid';
import { useNotifier } from 'shared/components/Notifier';
import PageHeader from 'shared/components/PageHeader';
import SectionCard from 'shared/components/SectionCard';
import StatusChip from 'shared/components/StatusChip';
import { formatDateTime } from 'shared/components/format';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { dispatchPaths } from 'shared/layout/navigation';

/** One sentence per action, naming what landed. */
const CONFIRMATIONS: Record<ExceptionAction, string> = {
  assign: 'Case assigned. The assignee has been notified.',
  reassign: 'Case reassigned. The new assignee has been notified.',
  review: 'Review started.',
  'request-explanation': 'Explanation requested. The case is now awaiting a response.',
  explain: 'Explanation recorded.',
  approve: 'Approved. The case still has to be closed.',
  reject: 'Rejected. The case still has to be closed.',
  escalate: 'Case escalated.',
  hold: 'Case placed on hold.',
  resume: 'Case resumed.',
  cancel: 'Case cancelled.',
  close: 'Case closed. The manifest it was blocking can now close.',
  reopen: 'Case reopened. It blocks its manifest again.',
};

const ACTION_ORDER: ExceptionAction[] = [
  'assign',
  'reassign',
  'review',
  'request-explanation',
  'explain',
  'approve',
  'reject',
  'close',
  'hold',
  'resume',
  'escalate',
  'reopen',
  'cancel',
];

/** Actions the service takes no input for, so they run from the button rather than a dialog. */
const NO_INPUT_ACTIONS: ExceptionAction[] = ['review', 'request-explanation', 'resume'];

/**
 * A dispatch exception case, and every action legal from where it stands.
 *
 * The thing that makes this queue different from the fuel one is what an open case *does*: it blocks
 * the manifest it belongs to from closing. So the screen leads with which consignment is held up,
 * and closure — the action that releases it — is the one the layout points at.
 */
const DispatchExceptionDetailPage = () => {
  const { caseId = '' } = useParams();
  const navigate = useNavigate();
  const { notifySuccess, notifyError } = useNotifier();
  const [dialog, setDialog] = useState<ExceptionAction | null>(null);
  const [working, setWorking] = useState<ExceptionAction | null>(null);

  const exceptionCase = useApiQuery(
    (signal) => dispatchExceptionsApi.findById(caseId, signal),
    [caseId],
  );

  const runDirect = async (action: ExceptionAction) => {
    setWorking(action);
    try {
      await dispatchExceptionsApi.transition(caseId, action, { value: null, evidenceId: null });
      notifySuccess(CONFIRMATIONS[action]);
      exceptionCase.refetch();
    } catch (error) {
      notifyError(error);
    } finally {
      setWorking(null);
    }
  };

  const record = exceptionCase.data;
  const closureBlockers = record ? exceptionClosureBlockers(record) : [];
  const breached = record ? exceptionSlaBreached(record) : false;

  return (
    <div>
      <PageHeader
        title={record?.exceptionNumber ?? 'Exception case'}
        subtitle={record ? humanise(record.type) : undefined}
        crumbs={[
          { label: 'Dispatch', to: dispatchPaths.dashboard },
          { label: 'Exception cases', to: dispatchPaths.exceptions },
          { label: record?.exceptionNumber ?? '…' },
        ]}
        actions={
          <Button
            variant="outline"
            startIcon="arrow-left"
            onClick={() => navigate(dispatchPaths.exceptions)}
          >
            Queue
          </Button>
        }
        meta={
          record && (
            <div className="flex flex-wrap items-center gap-2">
              <StatusChip value={record.status} />
              <StatusChip value={record.severity} />
              {record.securityRelevant && (
                <StatusChip value="SECRET" label="Security relevant" tone="blocked" />
              )}
              {record.escalationLevel > 0 && (
                <StatusChip
                  value="ESCALATED"
                  label={`Escalation level ${record.escalationLevel}`}
                  tone="blocked"
                />
              )}
            </div>
          )
        }
      />

      <DataState
        loading={exceptionCase.initialising}
        error={exceptionCase.error}
        onRetry={exceptionCase.refetch}
        minHeight={300}
      >
        {record && (
          <div className="space-y-5">
            {record.dispatchId && exceptionOpen(record) && (
              <Alert variant="warning" title="This case is blocking a consignment">
                The manifest it belongs to cannot be closed while this case is open. Closing the case
                is what releases it.
                <div className="mt-2">
                  <Button
                    size="sm"
                    variant="outline"
                    endIcon="chevron-right"
                    onClick={() =>
                      navigate(dispatchPaths.manifestDetail(record.dispatchId as string))
                    }
                  >
                    Open the manifest
                  </Button>
                </div>
              </Alert>
            )}
            {breached && (
              <Alert variant="error" title="This case has breached its SLA">
                It was due {formatDateTime(record.slaDueAt)} — {formatDueIn(record.slaDueAt)}.
              </Alert>
            )}
            {record.securityRelevant && exceptionOpen(record) && (
              <Alert variant="warning" title="This case is security relevant">
                Escalating it surfaces the case to the security function as well as to the dispatch
                manager.
              </Alert>
            )}
            {record.status === 'CLOSED' && (
              <Alert variant="success" title="This case is closed">
                {record.closureReason ?? 'No closure reason was recorded.'}
              </Alert>
            )}
            {record.status === 'CANCELLED' && (
              <Alert variant="info" title="This case is cancelled">
                {record.closureReason ?? 'No reason was recorded.'}
              </Alert>
            )}

            <SectionCard title="Actions">
              <div className="flex flex-wrap items-center gap-2">
                {ACTION_ORDER.filter((action) => exceptionActionAllowed(record, action)).map(
                  (action) =>
                    NO_INPUT_ACTIONS.includes(action) ? (
                      <Button
                        key={action}
                        variant="outline"
                        startIcon="play"
                        loading={working === action}
                        onClick={() => runDirect(action)}
                      >
                        {EXCEPTION_RULES[action].label}
                      </Button>
                    ) : (
                      <Button
                        key={action}
                        variant={buttonVariant(action)}
                        startIcon={buttonIcon(action)}
                        onClick={() => setDialog(action)}
                      >
                        {EXCEPTION_RULES[action].label}
                      </Button>
                    ),
                )}
              </div>

              <div className="mt-4 flex flex-wrap items-center gap-2">
                {record.dispatchId && (
                  <Button
                    variant="ghost"
                    startIcon="clipboard-list"
                    endIcon="chevron-right"
                    onClick={() =>
                      navigate(dispatchPaths.manifestDetail(record.dispatchId as string))
                    }
                  >
                    Manifest
                  </Button>
                )}
                {record.courierItemId && (
                  <Button
                    variant="ghost"
                    startIcon="package"
                    endIcon="chevron-right"
                    onClick={() => navigate(dispatchPaths.itemDetail(record.courierItemId as string))}
                  >
                    Courier item
                  </Button>
                )}
              </div>
            </SectionCard>

            <div className="grid gap-5 xl:grid-cols-[1.4fr_1fr]">
              <div className="space-y-5">
                <SectionCard title="Case">
                  <KeyValueGrid
                    items={[
                      { label: 'Case number', value: record.exceptionNumber },
                      { label: 'Site', value: siteOf(record.siteCode) },
                      { label: 'Type', value: humanise(record.type) },
                      { label: 'Severity', value: humanise(record.severity) },
                      {
                        label: 'Security relevant',
                        value: record.securityRelevant ? 'Yes' : 'No',
                      },
                      { label: 'Assignee', value: record.assignee ?? 'Unassigned' },
                      { label: 'SLA due', value: formatDateTime(record.slaDueAt) },
                      { label: 'SLA standing', value: formatDueIn(record.slaDueAt) },
                      { label: 'Escalation level', value: record.escalationLevel },
                      { label: 'Occurrence key', value: record.occurrenceKey, span: 2 },
                      { label: 'Explanation', value: record.explanation ?? '—', span: 2 },
                      {
                        label: 'Decision',
                        value: record.decision ? humanise(record.decision) : '—',
                      },
                      { label: 'Evidence reference', value: record.evidenceId ?? '—' },
                      { label: 'Closure reason', value: record.closureReason ?? '—', span: 2 },
                    ]}
                  />
                </SectionCard>

                <SectionCard title="Why this case exists">
                  <p className="text-theme-sm text-gray-700">
                    {EXCEPTION_TYPE_DESCRIPTIONS[record.type as ExceptionType] ??
                      'This type is recorded by the service but is not described here.'}
                  </p>
                  {record.detectedRules.length > 0 && (
                    <ul className="mt-3 space-y-2.5">
                      {record.detectedRules.map((rule) => (
                        <li key={rule} className="rounded-md border border-gray-200 px-3.5 py-2.5">
                          <p className="text-theme-sm font-semibold text-gray-900">
                            {humanise(rule)}
                          </p>
                        </li>
                      ))}
                    </ul>
                  )}
                  <p className="mt-3 text-theme-xs text-gray-600">
                    The occurrence key is stable per detection, so a repeated detection updates this
                    case rather than raising a second one.
                  </p>
                </SectionCard>
              </div>

              <div className="space-y-5">
                <SectionCard
                  title="Path to closure"
                  subtitle="All three are required before the case can be closed"
                >
                  <ul className="space-y-3">
                    <ClosureStep
                      label="Explanation recorded"
                      satisfied={Boolean(record.explanation)}
                      hint="Request one, then record the response."
                    />
                    <ClosureStep
                      label="Decision recorded"
                      satisfied={Boolean(record.decision)}
                      hint="Approve or reject the case from under review."
                    />
                    <ClosureStep
                      label="Closure evidence"
                      satisfied={record.status === 'CLOSED'}
                      hint="Supplied with the closure itself."
                    />
                  </ul>
                  {exceptionOpen(record) && closureBlockers.length === 0 && (
                    <Alert variant="success" className="mt-4">
                      Everything the service needs is recorded. Closure needs an evidence reference.
                    </Alert>
                  )}
                </SectionCard>

                <SectionCard title="Provenance">
                  <KeyValueGrid
                    columns={2}
                    items={[
                      { label: 'Raised by', value: record.metadata.createdBy ?? '—' },
                      { label: 'Raised at', value: formatDateTime(record.metadata.createdAt) },
                      { label: 'Last change by', value: record.metadata.lastModifiedBy ?? '—' },
                      {
                        label: 'Last change at',
                        value: formatDateTime(record.metadata.lastModifiedAt),
                      },
                      { label: 'Record version', value: record.metadata.version },
                      {
                        label: 'Correlation ID',
                        value: record.metadata.auditCorrelationId ?? '—',
                        span: 2,
                      },
                    ]}
                  />
                  <p className="mt-3 text-theme-xs text-gray-600">
                    The dispatch module exposes no per-record transition history, so this is the
                    case’s own provenance rather than its audit trail.
                  </p>
                </SectionCard>

                <SectionCard title="Where this can go next">
                  <ul className="space-y-2.5">
                    {ACTION_ORDER.map((action) => {
                      const allowed = exceptionActionAllowed(record, action);
                      const rule = EXCEPTION_RULES[action];
                      return (
                        <li key={action} className="flex items-start gap-2.5">
                          <Icon
                            name={allowed ? 'check-circle' : 'close'}
                            size={15}
                            className={
                              allowed
                                ? 'mt-0.5 shrink-0 text-success-700'
                                : 'mt-0.5 shrink-0 text-gray-400'
                            }
                          />
                          <div className="min-w-0">
                            <p
                              className={
                                allowed
                                  ? 'text-theme-sm font-medium text-gray-900'
                                  : 'text-theme-sm text-gray-500'
                              }
                            >
                              {rule.label}
                              {rule.privileged && (
                                <span className="ml-1.5 text-theme-xs font-semibold text-gold-900">
                                  privileged
                                </span>
                              )}
                            </p>
                            <p className="text-theme-xs text-gray-600">
                              {allowed
                                ? `Needs ${rule.permission}.`
                                : rule.from.length === 0
                                  ? 'Available from any state.'
                                  : `From ${rule.from
                                      .map((state) => humanise(state).toLowerCase())
                                      .join(', ')}.`}
                            </p>
                          </div>
                        </li>
                      );
                    })}
                  </ul>
                </SectionCard>
              </div>
            </div>

            {dialog && (
              <ExceptionActionDialog
                open
                exceptionCase={record}
                action={dialog}
                onClose={() => setDialog(null)}
                onSaved={() => {
                  notifySuccess(CONFIRMATIONS[dialog]);
                  exceptionCase.refetch();
                }}
              />
            )}
          </div>
        )}
      </DataState>
    </div>
  );
};

const ClosureStep = ({
  label,
  satisfied,
  hint,
}: {
  label: string;
  satisfied: boolean;
  hint: string;
}) => (
  <li className="flex items-start gap-2.5">
    <Icon
      name={satisfied ? 'check-circle' : 'alert-circle'}
      size={16}
      className={satisfied ? 'mt-0.5 shrink-0 text-success-700' : 'mt-0.5 shrink-0 text-warning-700'}
    />
    <div className="min-w-0">
      <p className="text-theme-sm font-medium text-gray-900">{label}</p>
      {!satisfied && <p className="text-theme-xs text-gray-600">{hint}</p>}
    </div>
  </li>
);

const buttonVariant = (action: ExceptionAction) => {
  switch (action) {
    case 'assign':
      return 'primary' as const;
    case 'close':
      return 'accent' as const;
    case 'cancel':
    case 'reject':
      return 'danger' as const;
    default:
      return 'outline' as const;
  }
};

const buttonIcon = (action: ExceptionAction) => {
  switch (action) {
    case 'assign':
    case 'reassign':
      return 'user-plus' as const;
    case 'explain':
      return 'edit' as const;
    case 'approve':
      return 'check-circle' as const;
    case 'reject':
      return 'close' as const;
    case 'escalate':
      return 'alert-triangle' as const;
    case 'hold':
      return 'stop' as const;
    case 'close':
      return 'lock' as const;
    case 'reopen':
      return 'refresh' as const;
    case 'cancel':
      return 'close' as const;
    default:
      return 'play' as const;
  }
};

export default DispatchExceptionDetailPage;
