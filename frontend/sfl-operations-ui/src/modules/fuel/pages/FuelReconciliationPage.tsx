import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router';
import { FuelPolicy, FuelTransaction, rulePassed } from 'modules/fuel/api/dto';
import { RECONCILIATION_RULES, RULE_DESCRIPTIONS } from 'modules/fuel/api/enums';
import { fuelPoliciesApi, fuelTransactionsApi } from 'modules/fuel/api/fuelApi';
import { transactionReconcilable } from 'modules/fuel/api/workflow';
import { useClampPage, useServerPage } from 'modules/fuel/components/useServerPage';
import { formatMoney, formatQuantity } from 'modules/fuel/components/fuelFormat';
import { humanise } from 'modules/fleet/api/enums';
import Alert from 'shared/components/Alert';
import Button from 'shared/components/Button';
import DataState from 'shared/components/DataState';
import DataTable, { CellStack, Column } from 'shared/components/DataTable';
import FilterBar from 'shared/components/FilterBar';
import Icon from 'shared/components/Icon';
import { useNotifier } from 'shared/components/Notifier';
import PageHeader from 'shared/components/PageHeader';
import SectionCard from 'shared/components/SectionCard';
import SiteSelect, { defaultSite } from 'shared/components/SiteSelect';
import StatCard from 'shared/components/StatCard';
import StatusChip from 'shared/components/StatusChip';
import { EnumSelect } from 'shared/components/fields';
import { formatDate, formatDateTime, formatNumber } from 'shared/components/format';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { fuelPaths } from 'shared/layout/navigation';

/** What a reconciliation run can be asked to cover. */
const SCOPES = ['RECEIVED', 'EXCEPTION'] as const;
type Scope = (typeof SCOPES)[number];

const SCOPE_LABELS: Record<Scope, string> = {
  RECEIVED: 'Not yet reconciled',
  EXCEPTION: 'Previously in exception',
};

/** The outcome of one transaction in a run, with the rules the service actually recorded. */
interface RunOutcome {
  transaction: FuelTransaction;
  status: 'RECONCILED' | 'EXCEPTION' | 'REFUSED';
  failedRules: string[];
  passedCount: number;
  policyVersion: number | null;
  message?: string;
}

/**
 * Run reconciliation and read what it decided.
 *
 * There is no `POST /reconciliations/run` — the inventory document lists one, but the only entry
 * point the service has is `POST /transactions/{id}/reconcile`, one transaction at a time (gap 1).
 * So a "run" here is exactly that: the selected transactions, reconciled in sequence, with each
 * outcome reported as it lands. That is honest about what is happening and it means a failure on one
 * record does not abandon the rest.
 *
 * The per-rule results the service stores are not readable, so the outcomes below give the verdict
 * and link to the cases the run raised — where the failing rule *is* recorded, in `detectedRules`.
 */
const FuelReconciliationPage = () => {
  const navigate = useNavigate();
  const { notifySuccess, notifyError } = useNotifier();

  const [siteCode, setSiteCode] = useState(defaultSite);
  const [scope, setScope] = useState<Scope>('RECEIVED');
  const [running, setRunning] = useState(false);
  const [outcomes, setOutcomes] = useState<RunOutcome[]>([]);

  const filterKey = siteCode + '|' + scope;
  const paging = useServerPage(filterKey);

  const candidates = useApiQuery(
    (signal) =>
      fuelTransactionsApi.search(
        { siteCode, status: scope, page: paging.page, size: paging.size },
        signal,
      ),
    [filterKey, paging.page, paging.size],
  );

  useClampPage(paging.page, candidates.data?.totalPages, paging.setPage);

  /** The policies a run would actually resolve against, asked of the service. */
  const policies = useApiQuery(
    (signal) => fuelPoliciesApi.search({ siteCode, inForceOnly: true, size: 50 }, signal),
    [siteCode],
  );

  const activePolicies = useMemo(() => policies.data?.content ?? [], [policies.data]);

  /**
   * Reads back the run the service just recorded, so the outcome names real rules.
   *
   * The reconciliation record carries the full per-rule map and the policy version it applied.
   * Before that read existed, the failing rules had to be inferred from the anomaly cases the run
   * raised — which could only ever show failures, never what passed.
   */
  const outcomeFor = async (transaction: FuelTransaction): Promise<RunOutcome> => {
    const result = await fuelTransactionsApi.reconcile(transaction.id);
    const runs = await fuelTransactionsApi.reconciliations(transaction.id);
    const latest = runs[0];
    const rules = Object.entries(latest?.ruleResults ?? {});
    return {
      transaction: result,
      status: result.status === 'RECONCILED' ? 'RECONCILED' : 'EXCEPTION',
      failedRules: rules.filter(([, outcome]) => !rulePassed(outcome)).map(([rule]) => rule),
      passedCount: rules.filter(([, outcome]) => rulePassed(outcome)).length,
      policyVersion: latest?.policyVersion ?? null,
    };
  };

  /**
   * Reconciles every candidate in sequence.
   *
   * Sequential rather than parallel, deliberately: each run advances the vehicle's accepted odometer
   * through `FleetOdometerPort` and reads the previous transaction for the consumption and
   * cost-variance rules, so running them concurrently would have them race over the same vehicle
   * state and produce outcomes that depend on scheduling.
   */
  const runAll = async () => {
    const targets = (candidates.data?.content ?? []).filter(transactionReconcilable);
    if (targets.length === 0) {
      return;
    }
    setRunning(true);
    setOutcomes([]);
    const results: RunOutcome[] = [];

    for (const transaction of targets) {
      try {
        results.push(await outcomeFor(transaction));
      } catch (error) {
        results.push({
          transaction,
          status: 'REFUSED',
          failedRules: [],
          passedCount: 0,
          policyVersion: null,
          message: error instanceof Error ? error.message : 'The service refused this transaction.',
        });
      }
      setOutcomes([...results]);
    }

    setRunning(false);
    const reconciled = results.filter((outcome) => outcome.status === 'RECONCILED').length;
    const exceptions = results.filter((outcome) => outcome.status === 'EXCEPTION').length;
    const refused = results.filter((outcome) => outcome.status === 'REFUSED').length;

    if (refused === results.length) {
      notifyError(
        undefined,
        `The service refused all ${refused} transactions. See the outcomes below.`,
      );
    } else {
      notifySuccess(
        `Reconciled ${reconciled}, raised ${exceptions} exception${exceptions === 1 ? '' : 's'}${
          refused > 0 ? `, ${refused} refused` : ''
        }.`,
      );
    }
    candidates.refetch();
  };

  const runOne = async (transaction: FuelTransaction) => {
    try {
      const outcome = await outcomeFor(transaction);
      setOutcomes((current) => [
        outcome,
        ...current.filter((entry) => entry.transaction.id !== transaction.id),
      ]);
      notifySuccess(
        outcome.status === 'RECONCILED'
          ? `Reconciled. All ${outcome.passedCount} policy rules passed.`
          : `Reconciliation completed with ${outcome.failedRules.length} failed rule${outcome.failedRules.length === 1 ? '' : 's'}.`,
      );
      candidates.refetch();
    } catch (error) {
      notifyError(error);
    }
  };

  const candidateColumns = useMemo<Column<FuelTransaction>[]>(
    () => [
      {
        key: 'transaction',
        header: 'Transaction',
        width: 260,
        cell: (row) => (
          <CellStack
            primary={`${row.vendorReference} · ${row.fuelProduct}`}
            secondary={`${formatDateTime(row.occurredAt)} · ${row.sourceSystem}`}
          />
        ),
      },
      {
        key: 'quantity',
        header: 'Quantity',
        width: 120,
        align: 'right',
        cell: (row) => formatQuantity(row.quantity, row.quantityUnit),
      },
      {
        key: 'cost',
        header: 'Cost',
        width: 130,
        align: 'right',
        cell: (row) => formatMoney(row.totalCost, row.currency),
      },
      {
        key: 'status',
        header: 'Status',
        width: 120,
        cell: (row) => <StatusChip value={row.status} />,
      },
      {
        key: 'action',
        header: 'Run',
        width: 120,
        align: 'right',
        cell: (row) => (
          <Button
            size="sm"
            variant="outline"
            startIcon="scale"
            disabled={running || !transactionReconcilable(row)}
            onClick={() => runOne(row)}
          >
            Reconcile
          </Button>
        ),
      },
    ],
    // `running` and `runOne` both change what the button does, so the column set is rebuilt with
    // them rather than capturing a stale closure.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [running],
  );

  const outcomeColumns = useMemo<Column<RunOutcome>[]>(
    () => [
      {
        key: 'transaction',
        header: 'Transaction',
        width: 260,
        cell: (row) => (
          <CellStack
            primary={`${row.transaction.vendorReference} · ${row.transaction.fuelProduct}`}
            secondary={formatDateTime(row.transaction.occurredAt)}
          />
        ),
      },
      {
        key: 'outcome',
        header: 'Outcome',
        width: 150,
        cell: (row) =>
          row.status === 'REFUSED' ? (
            <StatusChip value="REJECTED" label="Refused" tone="blocked" />
          ) : (
            <StatusChip value={row.status} />
          ),
      },
      {
        key: 'rules',
        header: 'Rules',
        width: 300,
        cell: (row) => {
          if (row.status === 'REFUSED') {
            return <span className="text-error-800">{row.message}</span>;
          }
          if (row.failedRules.length === 0) {
            return (
              <span className="text-gray-600">
                All {row.passedCount} rules passed
                {row.policyVersion !== null ? ` · policy version ${row.policyVersion}` : ''}
              </span>
            );
          }
          return (
            <span>
              <span className="font-medium text-error-800">
                {row.failedRules.map((rule) => humanise(rule)).join(', ')}
              </span>
              <span className="text-gray-600">
                {' '}
                · {row.passedCount} passed
                {row.policyVersion !== null ? ` · policy version ${row.policyVersion}` : ''}
              </span>
            </span>
          );
        },
      },
      {
        key: 'link',
        header: '',
        width: 110,
        align: 'right',
        cell: (row) => (
          <Button
            size="sm"
            variant="ghost"
            endIcon="chevron-right"
            onClick={() => navigate(fuelPaths.transactionDetail(row.transaction.id))}
          >
            Open
          </Button>
        ),
      },
    ],
    [navigate],
  );

  const runnable = (candidates.data?.content ?? []).filter(transactionReconcilable).length;

  return (
    <div>
      <PageHeader
        title="Reconciliation"
        subtitle="Judge transactions against the policy that was in force when they occurred."
        crumbs={[{ label: 'Fuel', to: fuelPaths.dashboard }, { label: 'Reconciliation' }]}
        actions={
          <Button
            variant="primary"
            startIcon="scale"
            loading={running}
            disabled={runnable === 0 || activePolicies.length === 0}
            onClick={runAll}
          >
            {runnable === 0 ? 'Nothing to run' : `Reconcile ${runnable}`}
          </Button>
        }
      />

      <SectionCard flush>
        <FilterBar>
          <SiteSelect value={siteCode} onChange={setSiteCode} required />
          <EnumSelect
            label="Scope"
            value={scope}
            options={SCOPES}
            onChange={(value) => setScope((value || 'RECEIVED') as Scope)}
            renderOptionLabel={(option) => SCOPE_LABELS[option]}
            helperText="Which transactions this run will cover."
          />
        </FilterBar>
      </SectionCard>

      <div className="mt-5 space-y-5">
        {policies.data && activePolicies.length === 0 && (
          <Alert variant="error" title="No active fuel policy at this site">
            Reconciliation reads the policy in force when a transaction occurred, and refuses the run
            outright when it cannot find one. Create a policy covering the period first.
            <div className="mt-2">
              <Button
                size="sm"
                variant="outline"
                endIcon="chevron-right"
                onClick={() => navigate(fuelPaths.policies)}
              >
                Fuel policies
              </Button>
            </div>
          </Alert>
        )}

        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <StatCard
            label="Awaiting a run"
            value={formatNumber(candidates.data?.totalElements ?? 0)}
            icon="scale"
            tone={(candidates.data?.totalElements ?? 0) > 0 ? 'caution' : 'neutral'}
            caption={SCOPE_LABELS[scope].toLowerCase()}
          />
          <StatCard
            label="Active policies"
            value={formatNumber(activePolicies.length)}
            icon="shield-check"
            tone={activePolicies.length === 0 ? 'critical' : 'neutral'}
            caption="Available to judge against"
            onClick={() => navigate(fuelPaths.policies)}
          />
          <StatCard
            label="Reconciled in this run"
            value={formatNumber(
              outcomes.filter((outcome) => outcome.status === 'RECONCILED').length,
            )}
            icon="check-circle"
            tone="good"
            caption="Passed every rule"
          />
          <StatCard
            label="Exceptions in this run"
            value={formatNumber(outcomes.filter((outcome) => outcome.status === 'EXCEPTION').length)}
            icon="alert-circle"
            tone={
              outcomes.some((outcome) => outcome.status === 'EXCEPTION') ? 'critical' : 'neutral'
            }
            caption="Raised at least one case"
            onClick={() => navigate(fuelPaths.anomalies)}
          />
        </div>

        {outcomes.length > 0 && (
          <SectionCard
            title="Run outcomes"
            subtitle={`${outcomes.length} transaction${outcomes.length === 1 ? '' : 's'} processed`}
            actions={
              <Button size="sm" variant="ghost" startIcon="close" onClick={() => setOutcomes([])}>
                Clear
              </Button>
            }
            flush
          >
            <DataTable
              rows={outcomes}
              columns={outcomeColumns}
              getRowId={(row) => row.transaction.id}
              caption="The outcome of each transaction in the most recent reconciliation run, with the rules that failed."
              dense
            />
          </SectionCard>
        )}

        <SectionCard
          title="Transactions in scope"
          subtitle="Reconcile them together, or one at a time"
          flush
        >
          <DataState
            loading={candidates.initialising}
            error={candidates.error}
            empty={(candidates.data?.totalElements ?? 0) === 0}
            emptyTitle="Nothing in scope"
            emptyHint={`No transaction at ${siteCode} is ${SCOPE_LABELS[scope].toLowerCase()}.`}
            onRetry={candidates.refetch}
            minHeight={220}
          >
            <DataTable
              rows={candidates.data?.content ?? []}
              columns={candidateColumns}
              getRowId={(row) => row.id}
              loading={candidates.loading}
              caption="Fuel transactions eligible for reconciliation, with quantity, cost, current status and a control to run each."
              page={candidates.data?.page ?? paging.page}
              pageSize={candidates.data?.size ?? paging.size}
              totalElements={candidates.data?.totalElements ?? 0}
              onPageChange={paging.setPage}
              onPageSizeChange={paging.setSize}
            />
          </DataState>
        </SectionCard>

        <div className="grid gap-5 xl:grid-cols-2">
          <SectionCard
            title="The rules a run applies"
            subtitle="In the order the service evaluates them"
          >
            <ol className="space-y-2.5">
              {RECONCILIATION_RULES.map((rule) => (
                <li key={rule} className="flex items-start gap-2.5">
                  <Icon name="scale" size={14} className="mt-1 shrink-0 text-gray-500" />
                  <div className="min-w-0">
                    <p className="text-theme-sm font-medium text-gray-900">{humanise(rule)}</p>
                    <p className="text-theme-xs text-gray-600">{RULE_DESCRIPTIONS[rule]}</p>
                  </div>
                </li>
              ))}
            </ol>
            <p className="mt-3 text-theme-xs text-gray-600">
              Transcribed from the service’s reconciliation routine. Three of them only run when the
              policy supplies the relevant limit, and two only when a previous transaction exists for
              the vehicle. Which ones actually ran is recorded against each transaction and shown on
              its detail screen.
            </p>
          </SectionCard>

          <SectionCard
            title="Policies in force"
            subtitle="A run reads the one covering the transaction’s own timestamp"
          >
            <DataState
              loading={policies.initialising}
              error={policies.error}
              empty={activePolicies.length === 0}
              emptyTitle="No policy in force"
              emptyHint="Reconciliation cannot run without one."
              onRetry={policies.refetch}
              minHeight={180}
            >
              <ul className="space-y-3">
                {activePolicies.map((policy: FuelPolicy) => (
                  <li
                    key={policy.id}
                    className="rounded-md border border-gray-200 px-3.5 py-3"
                  >
                    <div className="flex flex-wrap items-baseline justify-between gap-2">
                      <p className="text-theme-sm font-semibold text-gray-900">
                        {policy.name}
                        <span className="ml-1.5 font-normal text-gray-600">
                          version {policy.policyVersion}
                        </span>
                      </p>
                      <Button
                        size="sm"
                        variant="ghost"
                        endIcon="chevron-right"
                        onClick={() => navigate(fuelPaths.policyDetail(policy.id))}
                      >
                        Open
                      </Button>
                    </div>
                    <p className="mt-0.5 text-theme-xs text-gray-600">
                      From {formatDate(policy.effectiveFrom)}
                      {policy.effectiveTo
                        ? ` to ${formatDate(policy.effectiveTo)}`
                        : ', with no end date'}{' '}
                      · max {policy.maxPerTransaction} per transaction · SLA{' '}
                      {policy.anomalySlaHours} hours
                    </p>
                  </li>
                ))}
              </ul>
            </DataState>
          </SectionCard>
        </div>
      </div>
    </div>
  );
};

export default FuelReconciliationPage;
