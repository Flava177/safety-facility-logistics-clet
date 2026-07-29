import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router';
import { CourierItem } from 'modules/dispatch/api/dto';
import { ITEM_STATUSES, ItemStatus } from 'modules/dispatch/api/enums';
import { inboundMailApi } from 'modules/dispatch/api/dispatchApi';
import { itemDistributable } from 'modules/dispatch/api/workflow';
import {
  DistributeInboundDialog,
  RegisterItemDialog,
} from 'modules/dispatch/dialogs/itemDialogs';
import { humanise } from 'modules/fleet/api/enums';
import Alert from 'shared/components/Alert';
import Button from 'shared/components/Button';
import DataState from 'shared/components/DataState';
import DataTable, { CellStack, Column } from 'shared/components/DataTable';
import FilterBar from 'shared/components/FilterBar';
import { useNotifier } from 'shared/components/Notifier';
import PageHeader from 'shared/components/PageHeader';
import SectionCard from 'shared/components/SectionCard';
import SiteSelect, { defaultSite } from 'shared/components/SiteSelect';
import StatCard from 'shared/components/StatCard';
import StatusChip from 'shared/components/StatusChip';
import { DateTimeField } from 'shared/components/DateField';
import { EnumSelect, TextInput } from 'shared/components/fields';
import { formatDateTime, formatNumber } from 'shared/components/format';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { useClampPage, useServerPage } from 'shared/hooks/useServerPage';
import { dispatchPaths } from 'shared/layout/navigation';

/**
 * The mailroom: inbound registration and acknowledged distribution.
 *
 * The same courier item register underneath, with direction fixed to inbound by the endpoint. What
 * makes it a distinct screen is the one thing inbound mail is *for* — getting the item to its
 * recipient and recording that they took it. Distribution is offered directly from the row, because
 * an operator working through the morning's post should not have to open each item to acknowledge
 * it.
 */
const InboundMailPage = () => {
  const navigate = useNavigate();
  const { notifySuccess } = useNotifier();

  const [siteCode, setSiteCode] = useState(defaultSite);
  const [status, setStatus] = useState<ItemStatus | ''>('');
  const [handler, setHandler] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [registering, setRegistering] = useState(false);
  const [distributing, setDistributing] = useState<CourierItem | null>(null);

  const filterKey = `${siteCode}|${status}|${handler}|${from}|${to}`;
  const paging = useServerPage(filterKey);

  const query = useApiQuery(
    (signal) =>
      inboundMailApi.search(
        {
          siteCode,
          status: status || undefined,
          handler: handler.trim() || undefined,
          from: from ? new Date(from).toISOString() : undefined,
          to: to ? new Date(to).toISOString() : undefined,
          page: paging.page,
          size: paging.size,
        },
        signal,
      ),
    [siteCode, status, handler, from, to, paging.page, paging.size],
  );

  useClampPage(paging.page, query.data?.totalPages, paging.setPage);

  const rows = useMemo(() => query.data?.content ?? [], [query.data]);
  const awaitingDistribution = useMemo(() => rows.filter(itemDistributable), [rows]);
  const acknowledged = useMemo(() => rows.filter((item) => Boolean(item.acknowledgedBy)), [rows]);

  const columns = useMemo<Column<CourierItem>[]>(
    () => [
      {
        key: 'item',
        header: 'Item',
        width: 250,
        cell: (row) => (
          <CellStack
            primary={`${row.itemNumber} · from ${row.sender ?? row.origin}`}
            secondary={humanise(row.itemType)}
          />
        ),
      },
      {
        key: 'recipient',
        header: 'For',
        width: 170,
        cell: (row) => row.recipient ?? <span className="text-gray-500">Unaddressed</span>,
      },
      {
        key: 'sensitivity',
        header: 'Sensitivity',
        width: 120,
        cell: (row) => <StatusChip value={row.sensitivity} />,
      },
      {
        key: 'acknowledged',
        header: 'Acknowledged',
        width: 200,
        cell: (row) =>
          row.acknowledgedBy ? (
            <CellStack
              primary={row.acknowledgedBy}
              secondary={formatDateTime(row.acknowledgedAt)}
            />
          ) : (
            <span className="text-gray-500">Not yet</span>
          ),
      },
      {
        key: 'status',
        header: 'Status',
        width: 120,
        cell: (row) => <StatusChip value={row.status} />,
      },
      {
        key: 'action',
        header: '',
        width: 150,
        align: 'right',
        cell: (row) =>
          itemDistributable(row) ? (
            <Button
              size="sm"
              variant="outline"
              startIcon="check-circle"
              onClick={() => setDistributing(row)}
            >
              Distribute
            </Button>
          ) : null,
      },
    ],
    [],
  );

  const filtersApplied = Boolean(status || handler || from || to);

  return (
    <div>
      <PageHeader
        title="Inbound mail"
        subtitle="Registration, and the acknowledgement that closes each item."
        crumbs={[{ label: 'Dispatch', to: dispatchPaths.dashboard }, { label: 'Inbound mail' }]}
        actions={
          <Button variant="primary" startIcon="plus" onClick={() => setRegistering(true)}>
            Register inbound item
          </Button>
        }
      />

      <div className="mb-5 grid gap-4 sm:grid-cols-3">
        <StatCard
          label="Awaiting distribution"
          value={formatNumber(awaitingDistribution.length)}
          icon="inbox"
          tone={awaitingDistribution.length > 0 ? 'caution' : 'neutral'}
          caption="Received or staged, not yet handed over"
        />
        <StatCard
          label="Acknowledged"
          value={formatNumber(acknowledged.length)}
          icon="check-circle"
          tone="neutral"
          caption="Distribution recorded with a name"
        />
        <StatCard
          label="Registered in this window"
          value={formatNumber(query.data?.totalElements ?? 0)}
          icon="package"
          caption="Inbound items matching the filters, site-wide"
        />
      </div>

      <SectionCard flush>
        <FilterBar
          onReset={() => {
            setStatus('');
            setHandler('');
            setFrom('');
            setTo('');
          }}
          resetDisabled={!filtersApplied}
        >
          <SiteSelect value={siteCode} onChange={setSiteCode} required />
          <EnumSelect
            label="Status"
            value={status}
            options={ITEM_STATUSES}
            onChange={(value) => setStatus(value)}
            allowEmpty
          />
          <TextInput
            label="Handler"
            value={handler}
            onChange={setHandler}
            placeholder="Exact handler name"
          />
          <DateTimeField label="From" value={from} onChange={setFrom} />
          <DateTimeField label="To" value={to} onChange={setTo} />
        </FilterBar>
      </SectionCard>

      <div className="mt-5 space-y-5">
        <Alert variant="info" title="Distribution is the record that matters">
          An acknowledgement names who physically took the item. Without it there is nothing to show
          that the mail reached its recipient, so the signature reference is worth capturing even
          though the service treats it as optional.
        </Alert>

        <SectionCard flush>
          <DataState
            loading={query.initialising}
            error={query.error}
            onRetry={query.refetch}
            minHeight={300}
          >
            <DataTable
              rows={rows}
              columns={columns}
              getRowId={(row) => row.id}
              loading={query.loading}
              onRowClick={(row) => navigate(dispatchPaths.itemDetail(row.id))}
              caption="Inbound mail at this site, with the recipient, sensitivity, whether distribution has been acknowledged, and status."
              emptyMessage="No inbound item matches these filters."
              page={query.data?.page ?? paging.page}
              pageSize={query.data?.size ?? paging.size}
              totalElements={query.data?.totalElements ?? 0}
              onPageChange={paging.setPage}
              onPageSizeChange={paging.setSize}
            />
          </DataState>
        </SectionCard>
      </div>

      {registering && (
        <RegisterItemDialog
          open
          inboundOnly
          defaultSiteCode={siteCode}
          onClose={() => setRegistering(false)}
          onSaved={(item) => {
            notifySuccess(`${item.itemNumber} registered as inbound.`);
            query.refetch();
          }}
        />
      )}

      {distributing && (
        <DistributeInboundDialog
          open
          item={distributing}
          onClose={() => setDistributing(null)}
          onSaved={() => {
            notifySuccess('Distribution recorded with its acknowledgement.');
            query.refetch();
          }}
        />
      )}
    </div>
  );
};

export default InboundMailPage;
