import { useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import {
  CustodyHandover,
  DispatchExceptionCase,
  DispatchManifestItem,
  DispatchReceipt,
  ReturnReconciliation,
} from 'modules/dispatch/api/dto';
import { CUSTODY_HOPS, HOP_DESCRIPTIONS } from 'modules/dispatch/api/enums';
import {
  custodyApi,
  dispatchExceptionsApi,
  manifestsApi,
  receiptsApi,
  returnsApi,
} from 'modules/dispatch/api/dispatchApi';
import {
  exceptionOpen,
  manifestActionAllowed,
  manifestClosureBlockers,
  manifestReceivable,
  manifestReturnReconcilable,
  parseCustodyGap,
} from 'modules/dispatch/api/workflow';
import {
  AddManifestItemDialog,
  AssignTripDialog,
  CloseManifestDialog,
  SealManifestDialog,
} from 'modules/dispatch/dialogs/manifestDialogs';
import {
  ConfirmReceiptDialog,
  ReconcileReturnDialog,
  RecordHandoverDialog,
} from 'modules/dispatch/dialogs/custodyDialogs';
import { shortId, siteOf } from 'modules/fuel/components/fuelFormat';
import { humanise } from 'modules/fleet/api/enums';
import Alert from 'shared/components/Alert';
import Button from 'shared/components/Button';
import DataState from 'shared/components/DataState';
import DataTable, { CellStack, Column } from 'shared/components/DataTable';
import Icon from 'shared/components/Icon';
import KeyValueGrid from 'shared/components/KeyValueGrid';
import { useNotifier } from 'shared/components/Notifier';
import PageHeader from 'shared/components/PageHeader';
import SectionCard from 'shared/components/SectionCard';
import StatusChip from 'shared/components/StatusChip';
import Tabs from 'shared/components/Tabs';
import { formatDateTime, formatNumber } from 'shared/components/format';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { fleetPaths } from 'shared/layout/navigation';
import { dispatchPaths } from 'shared/layout/navigation';

type DialogKey =
  | 'addItem'
  | 'seal'
  | 'assignTrip'
  | 'close'
  | 'handover'
  | 'receipt'
  | 'return'
  | null;

/**
 * One consignment, end to end.
 *
 * Custody, receipts and the return leg have no register of their own and no meaning apart from the
 * manifest they belong to, so they are tabs here rather than three more sidebar entries an operator
 * would cross-reference by hand. That is also what makes closure legible: the blockers live in those
 * tabs, and this page can state them all in one place before offering the action.
 */
const ManifestDetailPage = () => {
  const { manifestId = '' } = useParams();
  const navigate = useNavigate();
  const { notifySuccess, notifyError } = useNotifier();
  const [dialog, setDialog] = useState<DialogKey>(null);
  const [working, setWorking] = useState<string | null>(null);
  const [tab, setTab] = useState('items');

  const manifest = useApiQuery(
    (signal) => manifestsApi.findById(manifestId, signal),
    [manifestId],
  );
  const items = useApiQuery((signal) => manifestsApi.items(manifestId, signal), [manifestId]);
  const handovers = useApiQuery((signal) => custodyApi.handovers(manifestId, signal), [manifestId]);
  const gaps = useApiQuery((signal) => custodyApi.gaps(manifestId, signal), [manifestId]);
  const receipts = useApiQuery((signal) => receiptsApi.list(manifestId, signal), [manifestId]);
  const returns = useApiQuery((signal) => returnsApi.list(manifestId, signal), [manifestId]);

  const site = manifest.data ? siteOf(manifest.data.siteCode) : '';

  /**
   * Exception cases against this consignment.
   *
   * `GET /exceptions` has no `dispatchId` filter, so the site's cases are fetched and matched here.
   * Recorded as gap 2 — with more open cases than the window holds, a case against this manifest
   * could be missed, which is why the closure panel says where its count came from.
   */
  const exceptions = useApiQuery(
    (signal) => (site ? dispatchExceptionsApi.search({ siteCode: site }, signal) : Promise.resolve(undefined)),
    [site],
  );

  const relatedCases = useMemo(
    () => (exceptions.data ?? []).filter((c) => c.dispatchId === manifestId),
    [exceptions.data, manifestId],
  );
  const openCases = useMemo(() => relatedCases.filter(exceptionOpen), [relatedCases]);

  const closureBlockers = manifestClosureBlockers(gaps.data, openCases.length);

  const refreshAll = () => {
    manifest.refetch();
    items.refetch();
    handovers.refetch();
    gaps.refetch();
    receipts.refetch();
    returns.refetch();
    exceptions.refetch();
  };

  const advance = async (action: 'dispatch' | 'inTransit') => {
    setWorking(action);
    try {
      if (action === 'dispatch') {
        await manifestsApi.dispatch(manifestId);
        notifySuccess('Manifest dispatched.');
      } else {
        await manifestsApi.inTransit(manifestId);
        notifySuccess('Manifest marked in transit.');
      }
      refreshAll();
    } catch (error) {
      notifyError(error);
    } finally {
      setWorking(null);
    }
  };

  const record = manifest.data;

  const itemColumns = useMemo<Column<DispatchManifestItem>[]>(
    () => [
      {
        key: 'line',
        header: 'Line',
        width: 80,
        cell: (row) => <span className="font-semibold text-gray-900">{row.sequenceNo}</span>,
      },
      {
        key: 'item',
        header: 'Courier item',
        width: 220,
        cell: (row) => (
          <CellStack primary={shortId(row.courierItemId)} secondary={row.expectedSealId ?? 'no seal expected'} />
        ),
      },
      {
        key: 'quantity',
        header: 'Expected',
        width: 110,
        align: 'right',
        cell: (row) => formatNumber(row.expectedQuantity),
      },
      {
        key: 'returned',
        header: 'Return',
        width: 200,
        align: 'right',
        cell: (row) => (
          <div className="flex items-center justify-end gap-1.5">
            {row.returnSealState && <StatusChip value={row.returnSealState} />}
            <StatusChip value={row.returnStatus} />
          </div>
        ),
      },
      {
        key: 'open',
        header: '',
        width: 100,
        align: 'right',
        cell: (row) => (
          <Button
            size="sm"
            variant="ghost"
            endIcon="chevron-right"
            onClick={() => navigate(dispatchPaths.itemDetail(row.courierItemId))}
          >
            Open
          </Button>
        ),
      },
    ],
    [navigate],
  );

  const handoverColumns = useMemo<Column<CustodyHandover>[]>(
    () => [
      {
        key: 'hop',
        header: 'Hop',
        width: 200,
        cell: (row) => (
          <CellStack primary={humanise(row.hop)} secondary={`#${row.sequenceNo}`} />
        ),
      },
      {
        key: 'custodians',
        header: 'Handover',
        width: 260,
        cell: (row) => (
          <CellStack
            primary={`${row.transferringCustodian} → ${row.receivingCustodian}`}
            secondary={formatDateTime(row.occurredAt)}
          />
        ),
      },
      {
        key: 'seal',
        header: 'Seal',
        width: 110,
        cell: (row) => <StatusChip value={row.sealState} />,
      },
      {
        key: 'count',
        header: 'Verified',
        width: 100,
        align: 'right',
        cell: (row) =>
          row.verifiedCount === null ? (
            <span className="text-gray-500">not counted</span>
          ) : (
            formatNumber(row.verifiedCount)
          ),
      },
      {
        key: 'evidence',
        header: 'Evidence',
        width: 100,
        align: 'center',
        hideBelowLg: true,
        cell: (row) =>
          row.evidenceId ? (
            <StatusChip value="ACTIVE" label="Held" tone="ready" />
          ) : (
            <span className="text-gray-500">—</span>
          ),
      },
      {
        key: 'notes',
        header: 'Notes',
        width: 220,
        hideBelowLg: true,
        cell: (row) => row.notes ?? <span className="text-gray-500">—</span>,
      },
    ],
    [],
  );

  const receiptColumns = useMemo<Column<DispatchReceipt>[]>(
    () => [
      {
        key: 'outcome',
        header: 'Outcome',
        width: 180,
        cell: (row) => (
          <div className="flex items-center gap-1.5">
            <StatusChip value={row.outcome} />
            {row.varianceType && <StatusChip value={row.varianceType} tone="blocked" />}
          </div>
        ),
      },
      {
        key: 'counts',
        header: 'Counts',
        width: 160,
        cell: (row) => (
          <CellStack
            primary={`${row.verifiedCount} verified`}
            secondary={row.expectedCount === null ? 'no expectation set' : `${row.expectedCount} expected`}
          />
        ),
      },
      {
        key: 'seal',
        header: 'Seal',
        width: 150,
        cell: (row) => (
          <div className="flex items-center gap-1.5">
            <StatusChip value={row.sealState} />
            {!row.sealVerified && <StatusChip value="WARNING" label="Unverified" tone="caution" />}
          </div>
        ),
      },
      {
        key: 'recipient',
        header: 'Received by',
        width: 180,
        cell: (row) => (
          <CellStack primary={row.recipientName} secondary={formatDateTime(row.capturedAt)} />
        ),
      },
      {
        key: 'capture',
        header: 'Capture',
        width: 120,
        align: 'right',
        hideBelowLg: true,
        cell: (row) =>
          row.edgeCaptured ? (
            <StatusChip value="OFFLINE" label="Edge" tone="accent" />
          ) : (
            <span className="text-gray-500">Online</span>
          ),
      },
    ],
    [],
  );

  const returnColumns = useMemo<Column<ReturnReconciliation>[]>(
    () => [
      {
        key: 'outcome',
        header: 'Outcome',
        width: 140,
        cell: (row) => <StatusChip value={row.outcome} />,
      },
      {
        key: 'counts',
        header: 'Counts',
        width: 200,
        cell: (row) => (
          <CellStack
            primary={`${row.returnedCount} returned of ${row.expectedCount ?? '—'}`}
            secondary={
              row.shortfall > 0
                ? `${row.shortfall} short`
                : row.extras > 0
                  ? `${row.extras} more than expected`
                  : 'counts agree'
            }
          />
        ),
      },
      {
        key: 'seals',
        header: 'Broken seals',
        width: 130,
        align: 'right',
        cell: (row) =>
          row.brokenSeals > 0 ? (
            <span className="font-semibold text-error-800">{row.brokenSeals}</span>
          ) : (
            <span className="text-gray-500">0</span>
          ),
      },
      {
        key: 'by',
        header: 'Reconciled',
        width: 200,
        cell: (row) => (
          <CellStack primary={row.reconciledBy} secondary={formatDateTime(row.reconciledAt)} />
        ),
      },
      {
        key: 'notes',
        header: 'Notes',
        width: 220,
        hideBelowLg: true,
        cell: (row) => row.notes ?? <span className="text-gray-500">—</span>,
      },
    ],
    [],
  );

  const caseColumns = useMemo<Column<DispatchExceptionCase>[]>(
    () => [
      {
        key: 'case',
        header: 'Case',
        width: 260,
        cell: (row) => (
          <CellStack
            primary={`${row.exceptionNumber} · ${humanise(row.type)}`}
            secondary={row.detectedRules.map((rule) => humanise(rule)).join(', ') || 'no rule recorded'}
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
        key: 'assignee',
        header: 'Assignee',
        width: 150,
        hideBelowLg: true,
        cell: (row) => row.assignee ?? <span className="text-gray-500">Unassigned</span>,
      },
      {
        key: 'status',
        header: 'Status',
        width: 160,
        align: 'right',
        cell: (row) => <StatusChip value={row.status} />,
      },
    ],
    [],
  );

  return (
    <div>
      <PageHeader
        title={record?.manifestNumber ?? 'Manifest'}
        subtitle={record ? `${record.route} · ${record.assignedHandler}` : undefined}
        crumbs={[
          { label: 'Dispatch', to: dispatchPaths.dashboard },
          { label: 'Manifests', to: dispatchPaths.manifests },
          { label: record?.manifestNumber ?? '…' },
        ]}
        actions={
          <Button
            variant="outline"
            startIcon="arrow-left"
            onClick={() => navigate(dispatchPaths.manifests)}
          >
            Register
          </Button>
        }
        meta={
          record && (
            <div className="flex flex-wrap items-center gap-2">
              <StatusChip value={record.status} />
              <StatusChip
                value="NEUTRAL"
                label={`${record.itemCount} item${record.itemCount === 1 ? '' : 's'}`}
                tone="neutral"
              />
              <StatusChip
                value="NEUTRAL"
                label={`${record.sealIds.length} seal${record.sealIds.length === 1 ? '' : 's'}`}
                tone={record.status !== 'DRAFT' && record.sealIds.length === 0 ? 'caution' : 'neutral'}
              />
              {openCases.length > 0 && (
                <StatusChip
                  value="BLOCKED"
                  label={`${openCases.length} open case${openCases.length === 1 ? '' : 's'}`}
                  tone="blocked"
                />
              )}
            </div>
          )
        }
      />

      <DataState
        loading={manifest.initialising}
        error={manifest.error}
        onRetry={manifest.refetch}
        minHeight={300}
      >
        {record && (
          <div className="space-y-5">
            {record.status === 'DRAFT' && (
              <Alert variant="info" title="This manifest is still a draft">
                Items can be added and removed now. Sealing freezes the contents and cannot be undone.
              </Alert>
            )}
            {record.status === 'CLOSED' && (
              <Alert variant="success" title="This manifest is closed">
                {record.closureReason ?? 'No closure reason was recorded.'}
              </Alert>
            )}
            {record.status === 'EXCEPTION' && (
              <Alert variant="error" title="This manifest is in exception">
                Resolve the open cases below before it can continue.
              </Alert>
            )}

            <SectionCard title="Actions">
              <div className="flex flex-wrap items-center gap-2">
                {manifestActionAllowed(record, 'addItem') && (
                  <Button variant="primary" startIcon="plus" onClick={() => setDialog('addItem')}>
                    Add item
                  </Button>
                )}
                {manifestActionAllowed(record, 'seal') && (
                  <Button variant="accent" startIcon="lock" onClick={() => setDialog('seal')}>
                    Seal manifest
                  </Button>
                )}
                {manifestActionAllowed(record, 'assignTrip') && (
                  <Button variant="outline" startIcon="route" onClick={() => setDialog('assignTrip')}>
                    {record.tripId ? 'Reassign movement' : 'Assign movement'}
                  </Button>
                )}
                {manifestActionAllowed(record, 'dispatch') && (
                  <Button
                    variant="primary"
                    startIcon="truck"
                    loading={working === 'dispatch'}
                    onClick={() => advance('dispatch')}
                  >
                    Dispatch
                  </Button>
                )}
                {manifestActionAllowed(record, 'inTransit') && (
                  <Button
                    variant="outline"
                    startIcon="route"
                    loading={working === 'inTransit'}
                    onClick={() => advance('inTransit')}
                  >
                    Mark in transit
                  </Button>
                )}
                <Button
                  variant="outline"
                  startIcon="shield-lock"
                  onClick={() => setDialog('handover')}
                >
                  Record handover
                </Button>
                {manifestReceivable(record) && (
                  <Button
                    variant="accent"
                    startIcon="check-circle"
                    onClick={() => setDialog('receipt')}
                  >
                    Confirm receipt
                  </Button>
                )}
                {manifestReturnReconcilable(record) && (
                  <Button variant="outline" startIcon="refresh" onClick={() => setDialog('return')}>
                    Reconcile return
                  </Button>
                )}
                {manifestActionAllowed(record, 'close') && (
                  <Button variant="accent" startIcon="lock" onClick={() => setDialog('close')}>
                    Close manifest
                  </Button>
                )}
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

              {manifestActionAllowed(record, 'close') && closureBlockers.length > 0 && (
                <Alert variant="warning" title="Closure is blocked" className="mt-4">
                  <ul className="mt-1 list-disc space-y-1 pl-4">
                    {closureBlockers.map((blocker) => (
                      <li key={blocker}>{blocker}</li>
                    ))}
                  </ul>
                </Alert>
              )}
            </SectionCard>

            <div className="grid gap-5 xl:grid-cols-[1.4fr_1fr]">
              <SectionCard title="Consignment">
                <KeyValueGrid
                  items={[
                    { label: 'Manifest number', value: record.manifestNumber },
                    { label: 'Site', value: siteOf(record.siteCode) },
                    { label: 'Route', value: record.route },
                    { label: 'Handler', value: record.assignedHandler },
                    { label: 'Destination centre', value: record.destinationCentre ?? '—' },
                    { label: 'Examination context', value: record.examinationContext ?? '—' },
                    { label: 'Items', value: formatNumber(record.itemCount) },
                    {
                      label: 'Seals',
                      value: record.sealIds.length > 0 ? record.sealIds.join(', ') : 'None recorded',
                      span: 2,
                    },
                    { label: 'Dispatched at', value: formatDateTime(record.dispatchedAt) },
                    { label: 'Received at', value: formatDateTime(record.receivedAt) },
                    { label: 'Reconciled at', value: formatDateTime(record.reconciledAt) },
                    { label: 'Closure reason', value: record.closureReason ?? '—', span: 2 },
                  ]}
                />
              </SectionCard>

              <SectionCard title="Chain of custody" subtitle="What the policy makes of the chain">
                <DataState
                  loading={gaps.initialising}
                  error={gaps.error}
                  onRetry={gaps.refetch}
                  minHeight={200}
                >
                  {gaps.data && (
                    <>
                      <Alert variant={gaps.data.closable ? 'success' : 'warning'}>
                        {gaps.data.closable
                          ? 'The chain is complete and clean. It does not block closure.'
                          : 'The chain is not yet closable. The manifest cannot close until it is.'}
                      </Alert>

                      {gaps.data.gaps.length > 0 && (
                        <div className="mt-3">
                          <p className="text-theme-xs font-semibold text-gray-600">Recorded gaps</p>
                          <ul className="mt-1.5 space-y-1.5">
                            {gaps.data.gaps.map((gap) => {
                              const parsed = parseCustodyGap(gap);
                              return (
                                <li key={gap} className="flex items-start gap-2">
                                  <Icon
                                    name="alert-circle"
                                    size={14}
                                    className="mt-0.5 shrink-0 text-error-800"
                                  />
                                  <span className="text-theme-sm text-gray-700">
                                    <span className="font-medium text-gray-900">
                                      {humanise(parsed.reason)}
                                    </span>
                                    {parsed.hop && ` at ${humanise(parsed.hop).toLowerCase()}`}
                                    {parsed.detail && ` — ${parsed.detail}`}
                                  </span>
                                </li>
                              );
                            })}
                          </ul>
                        </div>
                      )}

                      <div className="mt-4">
                        <p className="text-theme-xs font-semibold text-gray-600">
                          Hops required for closure
                        </p>
                        <ul className="mt-1.5 space-y-1.5">
                          {CUSTODY_HOPS.map((hop) => {
                            const recorded = (handovers.data ?? []).some((h) => h.hop === hop);
                            const required = gaps.data!.missingClosureHops.includes(hop) || recorded;
                            if (!required) {
                              return null;
                            }
                            return (
                              <li key={hop} className="flex items-start gap-2">
                                <Icon
                                  name={recorded ? 'check-circle' : 'alert-circle'}
                                  size={14}
                                  className={
                                    recorded
                                      ? 'mt-0.5 shrink-0 text-success-700'
                                      : 'mt-0.5 shrink-0 text-warning-700'
                                  }
                                />
                                <span className="min-w-0 text-theme-sm">
                                  <span
                                    className={recorded ? 'text-gray-900' : 'font-medium text-gray-900'}
                                  >
                                    {humanise(hop)}
                                  </span>
                                  <span className="block text-theme-xs text-gray-600">
                                    {recorded ? 'Recorded.' : HOP_DESCRIPTIONS[hop]}
                                  </span>
                                </span>
                              </li>
                            );
                          })}
                        </ul>
                      </div>
                    </>
                  )}
                </DataState>
              </SectionCard>
            </div>

            <SectionCard flush>
              <Tabs
                items={[
                  { value: 'items', label: 'Items', count: items.data?.length ?? 0 },
                  { value: 'custody', label: 'Custody', count: handovers.data?.length ?? 0 },
                  { value: 'receipts', label: 'Receipts', count: receipts.data?.length ?? 0 },
                  { value: 'returns', label: 'Return leg', count: returns.data?.length ?? 0 },
                  { value: 'cases', label: 'Exception cases', count: relatedCases.length },
                ]}
                value={tab}
                onChange={setTab}
              />

              {tab === 'items' && (
                <DataState
                  loading={items.initialising}
                  error={items.error}
                  empty={(items.data?.length ?? 0) === 0}
                  emptyTitle="No items on this manifest"
                  emptyHint="Add at least one before sealing."
                  onRetry={items.refetch}
                  minHeight={200}
                >
                  <DataTable
                    rows={items.data ?? []}
                    columns={itemColumns}
                    getRowId={(row) => row.id}
                    loading={items.loading}
                    caption="The items on this manifest, with the seal and quantity expected for each and what the return leg made of it."
                    dense
                  />
                </DataState>
              )}

              {tab === 'custody' && (
                <DataState
                  loading={handovers.initialising}
                  error={handovers.error}
                  empty={(handovers.data?.length ?? 0) === 0}
                  emptyTitle="No handover recorded"
                  emptyHint="The chain of custody starts with the first handover."
                  onRetry={handovers.refetch}
                  minHeight={200}
                >
                  <DataTable
                    rows={handovers.data ?? []}
                    columns={handoverColumns}
                    getRowId={(row) => row.id}
                    loading={handovers.loading}
                    caption="Recorded custody handovers for this consignment, with the custodians, seal state, verified count and evidence for each."
                    dense
                  />
                </DataState>
              )}

              {tab === 'receipts' && (
                <DataState
                  loading={receipts.initialising}
                  error={receipts.error}
                  empty={(receipts.data?.length ?? 0) === 0}
                  emptyTitle="No receipt confirmed"
                  emptyHint="The destination confirms receipt when the consignment arrives."
                  onRetry={receipts.refetch}
                  minHeight={200}
                >
                  <DataTable
                    rows={receipts.data ?? []}
                    columns={receiptColumns}
                    getRowId={(row) => row.id}
                    loading={receipts.loading}
                    caption="Receipt confirmations for this consignment, with the derived outcome, counts, seal state and who received it."
                    dense
                  />
                </DataState>
              )}

              {tab === 'returns' && (
                <DataState
                  loading={returns.initialising}
                  error={returns.error}
                  empty={(returns.data?.length ?? 0) === 0}
                  emptyTitle="No return reconciled"
                  emptyHint="The return leg is reconciled once the consignment comes back."
                  onRetry={returns.refetch}
                  minHeight={200}
                >
                  <DataTable
                    rows={returns.data ?? []}
                    columns={returnColumns}
                    getRowId={(row) => row.id}
                    loading={returns.loading}
                    caption="Return reconciliations for this consignment, with counts, shortfall, broken seals and outcome."
                    dense
                  />
                </DataState>
              )}

              {tab === 'cases' && (
                <DataState
                  loading={exceptions.initialising}
                  error={exceptions.error}
                  empty={relatedCases.length === 0}
                  emptyTitle="No exception case"
                  emptyHint="Nothing has been raised against this consignment."
                  onRetry={exceptions.refetch}
                  minHeight={200}
                >
                  <DataTable
                    rows={relatedCases}
                    columns={caseColumns}
                    getRowId={(row) => row.id}
                    loading={exceptions.loading}
                    onRowClick={(row) => navigate(dispatchPaths.exceptionDetail(row.id))}
                    caption="Exception cases raised against this consignment, with the rule that raised each, its severity, assignee and status."
                    dense
                  />
                  <div className="px-5 pt-2 pb-4">
                    <p className="text-theme-xs text-gray-600">
                      Matched from the exception cases returned for {site} — the exception endpoint
                      has no manifest filter, so a case beyond that window would not appear here.
                    </p>
                  </div>
                </DataState>
              )}
            </SectionCard>

            {dialog === 'addItem' && (
              <AddManifestItemDialog
                open
                manifest={record}
                existingItemIds={(items.data ?? []).map((row) => row.courierItemId)}
                onClose={() => setDialog(null)}
                onSaved={() => {
                  notifySuccess('Item added to the manifest.');
                  refreshAll();
                }}
              />
            )}
            {dialog === 'seal' && (
              <SealManifestDialog
                open
                manifest={record}
                itemCount={items.data?.length ?? 0}
                onClose={() => setDialog(null)}
                onSaved={() => {
                  notifySuccess('Manifest sealed. Its contents are now frozen.');
                  refreshAll();
                }}
              />
            )}
            {dialog === 'assignTrip' && (
              <AssignTripDialog
                open
                manifest={record}
                onClose={() => setDialog(null)}
                onSaved={() => {
                  notifySuccess('Movement assignment saved.');
                  refreshAll();
                }}
              />
            )}
            {dialog === 'handover' && (
              <RecordHandoverDialog
                open
                manifest={record}
                recorded={handovers.data ?? []}
                onClose={() => setDialog(null)}
                onSaved={() => {
                  notifySuccess('Handover recorded on the custody chain.');
                  refreshAll();
                }}
              />
            )}
            {dialog === 'receipt' && (
              <ConfirmReceiptDialog
                open
                manifest={record}
                onClose={() => setDialog(null)}
                onSaved={() => {
                  notifySuccess('Receipt confirmed.');
                  refreshAll();
                }}
              />
            )}
            {dialog === 'return' && (
              <ReconcileReturnDialog
                open
                manifest={record}
                onClose={() => setDialog(null)}
                onSaved={() => {
                  notifySuccess('Return leg reconciled.');
                  refreshAll();
                }}
              />
            )}
            {dialog === 'close' && (
              <CloseManifestDialog
                open
                manifest={record}
                blockers={closureBlockers}
                onClose={() => setDialog(null)}
                onSaved={() => {
                  notifySuccess('Manifest closed.');
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

export default ManifestDetailPage;
