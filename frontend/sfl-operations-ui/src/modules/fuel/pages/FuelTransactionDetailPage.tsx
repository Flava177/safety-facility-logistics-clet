import { useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { FuelAnomalyCase } from 'modules/fuel/api/dto';
import {
  NON_RECONCILIATION_RULES,
  RULE_DESCRIPTIONS,
  ReconciliationRule,
  UNREACHABLE_TRANSACTION_STATUSES,
} from 'modules/fuel/api/enums';
import { fuelAnomaliesApi, fuelTransactionsApi } from 'modules/fuel/api/fuelApi';
import {
  transactionReconcilable,
  transactionReconciled,
  transactionVoidable,
} from 'modules/fuel/api/workflow';
import { VoidTransactionDialog } from 'modules/fuel/dialogs/transactionDialogs';
import RecordProvenance, { DerivedNote } from 'modules/fuel/components/Provenance';
import {
  formatMoney,
  formatQuantity,
  formatUnitPrice,
  siteOf,
} from 'modules/fuel/components/fuelFormat';
import { humanise } from 'modules/fleet/api/enums';
import Alert from 'shared/components/Alert';
import Button from 'shared/components/Button';
import DataState from 'shared/components/DataState';
import DataTable, { CellStack, Column } from 'shared/components/DataTable';
import KeyValueGrid from 'shared/components/KeyValueGrid';
import { useNotifier } from 'shared/components/Notifier';
import PageHeader from 'shared/components/PageHeader';
import SectionCard from 'shared/components/SectionCard';
import StatusChip from 'shared/components/StatusChip';
import { formatDateTime, formatNumber } from 'shared/components/format';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { fleetPaths, fuelPaths } from 'shared/layout/navigation';

/**
 * A fuel transaction, its reconciliation outcome and the cases that outcome raised.
 *
 * The reconciliation panel is the honest half of a screen that should show more. The service
 * evaluates fourteen named rules and stores every outcome in `fuel_reconciliations.rule_results`,
 * but exposes no way to read them (gap 1). What *is* readable is the transaction's own status and
 * the anomaly cases the run created, each carrying the rule that raised it in `detectedRules` — so
 * the panel lists the rules that **failed**, by name, and says plainly that the ones that passed
 * are not available.
 */
const FuelTransactionDetailPage = () => {
  const { transactionId = '' } = useParams();
  const navigate = useNavigate();
  const { notifySuccess, notifyError } = useNotifier();
  const [voiding, setVoiding] = useState(false);
  const [reconciling, setReconciling] = useState(false);

  const transaction = useApiQuery(
    (signal) => fuelTransactionsApi.findById(transactionId, signal),
    [transactionId],
  );

  const siteCode = transaction.data ? siteOf(transaction.data.siteCode) : '';

  /**
   * The anomaly cases raised against this transaction.
   *
   * `GET /anomalies` has no `transactionId` filter (gap 5), so the site's cases are fetched and
   * matched here. The list is bounded by the same unpaged window as everywhere else.
   */
  const anomalies = useApiQuery(
    (signal) =>
      siteCode ? fuelAnomaliesApi.search({ siteCode }, signal) : Promise.resolve(undefined),
    [siteCode],
  );

  const relatedCases = useMemo(
    () => (anomalies.data ?? []).filter((anomaly) => anomaly.transactionId === transactionId),
    [anomalies.data, transactionId],
  );

  /** Every rule name the related cases recorded, de-duplicated. These are the rules that failed. */
  const failedRules = useMemo(() => {
    const rules = new Set<string>();
    relatedCases.forEach((anomaly) => anomaly.detectedRules.forEach((rule) => rules.add(rule)));
    return [...rules];
  }, [relatedCases]);

  const refreshAll = () => {
    transaction.refetch();
    anomalies.refetch();
  };

  const reconcile = async () => {
    setReconciling(true);
    try {
      const result = await fuelTransactionsApi.reconcile(transactionId);
      if (result.status === 'RECONCILED') {
        notifySuccess('Reconciled. Every policy rule passed.');
      } else {
        notifySuccess(
          'Reconciliation completed with exceptions.',
          'One or more rules failed; the cases they raised are listed below.',
        );
      }
      refreshAll();
    } catch (error) {
      // A missing policy, a voided record or a refused permission all land here with the service's
      // own wording — never swallowed, never rewritten.
      notifyError(error);
    } finally {
      setReconciling(false);
    }
  };

  const caseColumns = useMemo<Column<FuelAnomalyCase>[]>(
    () => [
      {
        key: 'case',
        header: 'Case',
        width: 240,
        cell: (row) => (
          <CellStack
            primary={`${row.anomalyNumber} · ${humanise(row.type)}`}
            secondary={row.detectedRules.join(', ') || 'no rule recorded'}
          />
        ),
      },
      {
        key: 'severity',
        header: 'Severity',
        width: 110,
        cell: (row) => <StatusChip value={row.severity} />,
      },
      {
        key: 'material',
        header: 'Material',
        width: 100,
        align: 'center',
        hideBelowLg: true,
        cell: (row) =>
          row.material ? <StatusChip value="HIGH" label="Material" tone="caution" /> : '—',
      },
      {
        key: 'status',
        header: 'Status',
        width: 140,
        align: 'right',
        cell: (row) => <StatusChip value={row.status} />,
      },
    ],
    [],
  );

  const record = transaction.data;

  return (
    <div>
      <PageHeader
        title={record ? `${record.vendorReference} · ${record.fuelProduct}` : 'Fuel transaction'}
        subtitle={record ? `Occurred ${formatDateTime(record.occurredAt)}` : undefined}
        crumbs={[
          { label: 'Fuel', to: fuelPaths.dashboard },
          { label: 'Transactions', to: fuelPaths.transactions },
          { label: record ? record.id.slice(0, 8) : '…' },
        ]}
        actions={
          <Button
            variant="outline"
            startIcon="arrow-left"
            onClick={() => navigate(fuelPaths.transactions)}
          >
            Register
          </Button>
        }
        meta={
          record && (
            <div className="flex flex-wrap items-center gap-2">
              <StatusChip value={record.status} />
              <StatusChip value={record.lifecycle} label={`Lifecycle ${humanise(record.lifecycle).toLowerCase()}`} />
              <StatusChip value={record.sourceSystem} tone="neutral" label={record.sourceSystem} />
            </div>
          )
        }
      />

      <DataState
        loading={transaction.initialising}
        error={transaction.error}
        onRetry={transaction.refetch}
        minHeight={300}
      >
        {record && (
          <div className="space-y-5">
            {record.status === 'VOIDED' && (
              <Alert variant="warning" title="This transaction is voided">
                It is excluded from reconciliation permanently and cannot be changed. The reason
                recorded at the time is held in its comments.
              </Alert>
            )}

            <SectionCard title="Actions">
              <div className="flex flex-wrap items-center gap-2">
                <Button
                  variant="primary"
                  startIcon="scale"
                  loading={reconciling}
                  disabled={!transactionReconcilable(record)}
                  onClick={reconcile}
                >
                  {transactionReconciled(record) ? 'Reconcile again' : 'Reconcile'}
                </Button>
                <Button
                  variant="danger"
                  startIcon="close"
                  disabled={!transactionVoidable(record)}
                  onClick={() => setVoiding(true)}
                >
                  Void
                </Button>
                <Button
                  variant="ghost"
                  startIcon="truck"
                  endIcon="chevron-right"
                  onClick={() => navigate(fleetPaths.vehicleDetail(record.vehicleId))}
                >
                  Vehicle
                </Button>
                <Button
                  variant="ghost"
                  startIcon="driver"
                  endIcon="chevron-right"
                  onClick={() => navigate(fleetPaths.driverDetail(record.driverId))}
                >
                  Driver
                </Button>
                {record.tripId && (
                  <Button
                    variant="ghost"
                    startIcon="route"
                    endIcon="chevron-right"
                    onClick={() => navigate(fleetPaths.tripDetail(record.tripId as string))}
                  >
                    Trip
                  </Button>
                )}
              </div>
              {!transactionReconcilable(record) && record.status !== 'VOIDED' && (
                <p className="mt-3 text-theme-sm text-gray-600">
                  This record’s lifecycle is {humanise(record.lifecycle).toLowerCase()}, so the
                  service will not change its status.
                </p>
              )}
            </SectionCard>

            <div className="grid gap-5 xl:grid-cols-[1.4fr_1fr]">
              <div className="space-y-5">
                <SectionCard title="Transaction">
                  <KeyValueGrid
                    items={[
                      { label: 'Site', value: siteOf(record.siteCode) },
                      { label: 'Vendor', value: record.vendorReference },
                      { label: 'Station', value: record.stationReference ?? '—' },
                      { label: 'Product', value: record.fuelProduct },
                      {
                        label: 'Quantity',
                        value: formatQuantity(record.quantity, record.quantityUnit),
                      },
                      {
                        label: 'Unit price',
                        value: formatUnitPrice(record.unitPrice, record.currency),
                      },
                      {
                        label: 'Total cost',
                        value: formatMoney(record.totalCost, record.currency),
                      },
                      {
                        label: 'Odometer reading',
                        value: `${formatNumber(record.odometerReading)} km`,
                      },
                      { label: 'Occurred at', value: formatDateTime(record.occurredAt) },
                      {
                        label: 'Card reference',
                        value: record.maskedCardReference ?? '—',
                        masked: Boolean(record.maskedCardReference),
                      },
                      {
                        label: 'Receipt evidence',
                        value: record.receiptEvidenceId ?? 'None held',
                      },
                      { label: 'Comments', value: record.comments ?? '—', span: 2 },
                    ]}
                  />
                </SectionCard>

                <SectionCard
                  title="Reconciliation"
                  subtitle="What the policy rules made of this transaction"
                >
                  {!transactionReconciled(record) ? (
                    <Alert variant="info" title="Reconciliation has not run">
                      This transaction is {humanise(record.status).toLowerCase()}. It contributes to
                      the site’s totals but has not been judged against a policy, so no rule outcome
                      exists yet.
                    </Alert>
                  ) : record.status === 'RECONCILED' ? (
                    <Alert variant="success" title="Every rule passed">
                      The transaction was reconciled against the policy in force when it occurred and
                      raised no exception.
                    </Alert>
                  ) : (
                    <div className="space-y-3">
                      <Alert
                        variant="error"
                        title={`${failedRules.length || relatedCases.length} rule${
                          failedRules.length === 1 ? '' : 's'
                        } failed`}
                      >
                        Each failure raised the case listed below. A case stays open until it is
                        explained, decided and closed.
                      </Alert>
                      <ul className="space-y-2.5">
                        {failedRules.map((rule) => (
                          <li key={rule} className="rounded-md border border-gray-200 px-3.5 py-2.5">
                            <p className="text-theme-sm font-semibold text-gray-900">
                              {humanise(rule)}
                            </p>
                            <p className="mt-0.5 text-theme-sm text-gray-700">
                              {RULE_DESCRIPTIONS[rule as ReconciliationRule] ??
                                NON_RECONCILIATION_RULES[rule] ??
                                'This rule is recorded by the service but is not described here.'}
                            </p>
                          </li>
                        ))}
                      </ul>
                    </div>
                  )}
                  {transactionReconciled(record) && (
                    <DerivedNote>
                      The failed rules are read from the anomaly cases this run raised. The service
                      stores every rule’s outcome, including the ones that passed, but exposes no
                      endpoint to read them — so the rules that passed cannot be listed here.
                    </DerivedNote>
                  )}
                </SectionCard>

                <SectionCard
                  title="Anomaly cases"
                  subtitle="Raised against this transaction"
                  flush
                >
                  <DataState
                    loading={anomalies.initialising}
                    error={anomalies.error}
                    empty={relatedCases.length === 0}
                    emptyTitle="No anomaly cases"
                    emptyHint="Nothing has been raised against this transaction."
                    onRetry={anomalies.refetch}
                    minHeight={140}
                  >
                    <DataTable
                      rows={relatedCases}
                      columns={caseColumns}
                      getRowId={(row) => row.id}
                      loading={anomalies.loading}
                      onRowClick={(row) => navigate(fuelPaths.anomalyDetail(row.id))}
                      caption="Fuel anomaly cases raised against this transaction, with the rule that raised each, its severity, materiality and status."
                      dense
                    />
                  </DataState>
                </SectionCard>
              </div>

              <div className="space-y-5">
                <SectionCard title="Provenance" subtitle="Where this record came from">
                  <KeyValueGrid
                    columns={2}
                    items={[
                      { label: 'Source system', value: record.sourceSystem },
                      {
                        label: 'Provider reference',
                        value: record.providerTransactionId ?? '—',
                      },
                      {
                        label: 'Ingested at',
                        value: formatDateTime(record.ingestionTimestamp),
                      },
                      { label: 'Idempotency key', value: record.idempotencyKey ?? '—', span: 2 },
                      {
                        label: 'Correlation ID',
                        value: record.metadata.auditCorrelationId ?? '—',
                        span: 2,
                      },
                    ]}
                  />
                </SectionCard>

                <SectionCard title="History">
                  <RecordProvenance
                    metadata={record.metadata}
                    recordNoun="transaction"
                    milestones={[
                      {
                        label: 'Received from source',
                        at: record.ingestionTimestamp,
                        detail: `Source system ${record.sourceSystem}`,
                      },
                    ]}
                  />
                </SectionCard>

                <SectionCard title="Lifecycle" subtitle="Where this record can go next">
                  <ol className="space-y-2 text-theme-sm text-gray-700">
                    {['RECEIVED', 'RECONCILED', 'EXCEPTION', 'VOIDED'].map((state) => (
                      <li key={state} className="flex items-center gap-2">
                        <StatusChip value={state} />
                        {state === record.status && (
                          <span className="text-theme-xs font-semibold text-teal-800">
                            current
                          </span>
                        )}
                      </li>
                    ))}
                  </ol>
                  <DerivedNote>
                    The status enum also declares{' '}
                    {UNREACHABLE_TRANSACTION_STATUSES.map((state) =>
                      humanise(state).toLowerCase(),
                    ).join(', ')}
                    . No service code path writes them, so a record will not reach them.
                  </DerivedNote>
                </SectionCard>
              </div>
            </div>

            {voiding && (
              <VoidTransactionDialog
                open
                transaction={record}
                onClose={() => setVoiding(false)}
                onSaved={() => {
                  notifySuccess('Transaction voided.');
                  refreshAll();
                }}
              />
            )}
          </div>
        )}
      </DataState>
    </div>
  );
};

export default FuelTransactionDetailPage;
