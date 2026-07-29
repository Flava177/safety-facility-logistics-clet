import { useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { CourierItem } from 'modules/dispatch/api/dto';
import {
  ITEM_DIRECTIONS,
  ITEM_STATUSES,
  ITEM_TYPES,
  ItemDirection,
  ItemStatus,
  ItemType,
  SENSITIVITIES,
  Sensitivity,
} from 'modules/dispatch/api/enums';
import { DEFAULT_WINDOW, courierItemsApi, dispatchReportsApi } from 'modules/dispatch/api/dispatchApi';
import { RegisterItemDialog } from 'modules/dispatch/dialogs/itemDialogs';
import WindowNotice from 'modules/dispatch/components/WindowNotice';
import { useClientWindow } from 'modules/dispatch/components/useClientWindow';
import { humanise } from 'modules/fleet/api/enums';
import Button from 'shared/components/Button';
import DataState from 'shared/components/DataState';
import DataTable, { CellStack, Column } from 'shared/components/DataTable';
import FilterBar from 'shared/components/FilterBar';
import Icon from 'shared/components/Icon';
import { useNotifier } from 'shared/components/Notifier';
import PageHeader from 'shared/components/PageHeader';
import SectionCard from 'shared/components/SectionCard';
import SiteSelect, { defaultSite } from 'shared/components/SiteSelect';
import StatusChip from 'shared/components/StatusChip';
import { DateTimeField } from 'shared/components/DateField';
import { EnumSelect, TextInput } from 'shared/components/fields';
import { formatDateTime } from 'shared/components/format';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { dispatchPaths } from 'shared/layout/navigation';

/**
 * The courier item register.
 *
 * Site, direction, status, sensitivity, handler and the date range all reach the service — those are
 * the six filters `GET /items` accepts. Item type is filtered here over the returned window and is
 * labelled as such, because the endpoint has no parameter for it.
 *
 * There is no pagination on the dispatch side, so the footer counts the window the service returned
 * and `WindowNotice` says plainly when that window came back full.
 */
const CourierItemsPage = () => {
  const navigate = useNavigate();
  const { notifySuccess, notifyError } = useNotifier();
  const [searchParams] = useSearchParams();

  const [siteCode, setSiteCode] = useState(defaultSite);
  const [direction, setDirection] = useState<ItemDirection | ''>(
    (searchParams.get('direction') as ItemDirection | null) ?? '',
  );
  const [status, setStatus] = useState<ItemStatus | ''>(
    (searchParams.get('status') as ItemStatus | null) ?? '',
  );
  const [sensitivity, setSensitivity] = useState<Sensitivity | ''>('');
  const [handler, setHandler] = useState('');
  const [itemType, setItemType] = useState<ItemType | ''>('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [registering, setRegistering] = useState(false);
  const [exporting, setExporting] = useState(false);

  const query = useApiQuery(
    (signal) =>
      courierItemsApi.search(
        {
          siteCode,
          direction: direction || undefined,
          status: status || undefined,
          sensitivity: sensitivity || undefined,
          handler: handler.trim() || undefined,
          from: from ? new Date(from).toISOString() : undefined,
          to: to ? new Date(to).toISOString() : undefined,
        },
        signal,
      ),
    [siteCode, direction, status, sensitivity, handler, from, to],
  );

  const filtered = useMemo(() => {
    const rows = query.data ?? [];
    return itemType ? rows.filter((row) => row.itemType === itemType) : rows;
  }, [query.data, itemType]);

  const windowed = useClientWindow(
    filtered,
    `${siteCode}|${direction}|${status}|${sensitivity}|${handler}|${itemType}|${from}|${to}`,
    query.data?.length,
  );

  const exportReport = async () => {
    setExporting(true);
    try {
      const fileName = await dispatchReportsApi.items(siteCode);
      notifySuccess(
        `Downloaded ${fileName}.`,
        'The service exports the site’s items, not the filtered view.',
      );
    } catch (error) {
      notifyError(error);
    } finally {
      setExporting(false);
    }
  };

  const columns = useMemo<Column<CourierItem>[]>(
    () => [
      {
        key: 'item',
        header: 'Item',
        width: 260,
        cell: (row) => (
          <CellStack
            primary={`${row.itemNumber} · ${row.origin} → ${row.destination}`}
            secondary={humanise(row.itemType)}
          />
        ),
      },
      {
        key: 'direction',
        header: 'Direction',
        width: 110,
        cell: (row) => <StatusChip value={row.direction} />,
      },
      {
        key: 'sensitivity',
        header: 'Sensitivity',
        width: 130,
        cell: (row) => (
          <div className="flex items-center gap-1.5">
            <StatusChip value={row.sensitivity} />
            {row.chainOfCustodyRequired && (
              <Icon
                name="shield-lock"
                size={14}
                className="shrink-0 text-gray-600"
                aria-label="Chain of custody required"
              />
            )}
          </div>
        ),
      },
      {
        key: 'handler',
        header: 'Handler',
        width: 150,
        hideBelowLg: true,
        cell: (row) => row.assignedHandler ?? <span className="text-gray-500">Unassigned</span>,
      },
      {
        key: 'registered',
        header: 'Registered',
        width: 160,
        hideBelowLg: true,
        cell: (row) => formatDateTime(row.metadata.createdAt),
      },
      {
        key: 'status',
        header: 'Status',
        width: 140,
        align: 'right',
        cell: (row) => (
          <div className="flex items-center justify-end gap-1.5">
            {row.undelivered && <StatusChip value="MISSING" label="Undelivered" tone="blocked" />}
            <StatusChip value={row.status} />
          </div>
        ),
      },
    ],
    [],
  );

  const filtersApplied = Boolean(
    direction || status || sensitivity || handler || itemType || from || to,
  );

  return (
    <div>
      <PageHeader
        title="Courier items"
        subtitle="Every tracked item at this site, inbound and outbound."
        crumbs={[{ label: 'Dispatch', to: dispatchPaths.dashboard }, { label: 'Courier items' }]}
        actions={
          <>
            <Button
              variant="outline"
              startIcon="download"
              loading={exporting}
              onClick={exportReport}
            >
              Export CSV
            </Button>
            <Button variant="primary" startIcon="plus" onClick={() => setRegistering(true)}>
              Register item
            </Button>
          </>
        }
      />

      <SectionCard flush>
        <FilterBar
          onReset={() => {
            setDirection('');
            setStatus('');
            setSensitivity('');
            setHandler('');
            setItemType('');
            setFrom('');
            setTo('');
          }}
          resetDisabled={!filtersApplied}
        >
          <SiteSelect value={siteCode} onChange={setSiteCode} required />
          <EnumSelect
            label="Direction"
            value={direction}
            options={ITEM_DIRECTIONS}
            onChange={(value) => setDirection(value)}
            allowEmpty
          />
          <EnumSelect
            label="Status"
            value={status}
            options={ITEM_STATUSES}
            onChange={(value) => setStatus(value)}
            allowEmpty
          />
          <EnumSelect
            label="Sensitivity"
            value={sensitivity}
            options={SENSITIVITIES}
            onChange={(value) => setSensitivity(value)}
            allowEmpty
          />
          <TextInput
            label="Handler"
            value={handler}
            onChange={setHandler}
            placeholder="Exact handler name"
          />
          <EnumSelect
            label="Item type"
            value={itemType}
            options={ITEM_TYPES}
            onChange={(value) => setItemType(value)}
            allowEmpty
            helperText="Filters the loaded records."
          />
          <DateTimeField label="From" value={from} onChange={setFrom} />
          <DateTimeField label="To" value={to} onChange={setTo} />
        </FilterBar>
      </SectionCard>

      <div className="mt-5">
        <SectionCard flush>
          <DataState
            loading={query.initialising}
            error={query.error}
            onRetry={query.refetch}
            minHeight={300}
          >
            <DataTable
              rows={windowed.rows}
              columns={columns}
              getRowId={(row) => row.id}
              loading={query.loading}
              onRowClick={(row) => navigate(dispatchPaths.itemDetail(row.id))}
              caption="Courier items matching the current filters, with direction, sensitivity, whether a chain of custody is required, handler and status."
              emptyMessage="No item matches these filters."
              page={windowed.page}
              pageSize={windowed.pageSize}
              totalElements={windowed.total}
              onPageChange={windowed.setPage}
              onPageSizeChange={windowed.setPageSize}
            />
          </DataState>
        </SectionCard>

        <WindowNotice
          truncated={windowed.truncated}
          total={query.data?.length ?? 0}
          requestedSize={DEFAULT_WINDOW}
          noun="items"
        />
      </div>

      {registering && (
        <RegisterItemDialog
          open
          defaultSiteCode={siteCode}
          onClose={() => setRegistering(false)}
          onSaved={(item) => {
            notifySuccess(
              `${item.itemNumber} registered.`,
              item.chainOfCustodyRequired
                ? 'It requires a chain of custody — every handover must be recorded.'
                : 'No chain of custody is required for this item.',
            );
            query.refetch();
            navigate(dispatchPaths.itemDetail(item.id));
          }}
        />
      )}
    </div>
  );
};

export default CourierItemsPage;
