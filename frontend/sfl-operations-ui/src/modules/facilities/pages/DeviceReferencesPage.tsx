import { useState } from 'react';
import Alert from 'shared/components/Alert';
import ControlButton from 'shared/components/ControlButton';
import DataState from 'shared/components/DataState';
import DataTable, { Column } from 'shared/components/DataTable';
import FilterBar from 'shared/components/FilterBar';
import PageHeader from 'shared/components/PageHeader';
import SiteSelect, { defaultSite } from 'shared/components/SiteSelect';
import StatusChip from 'shared/components/StatusChip';
import { SelectInput } from 'shared/components/fields';
import { useNotifier } from 'shared/components/Notifier';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import type { DeviceReference } from '../api/dto';
import { deviceReferenceTypes } from '../api/enums';
import type { DeviceReferenceType } from '../api/enums';
import {
  changeDeviceReferenceLifecycle,
  listDeviceReferences,
  registerDeviceReference,
  updateDeviceReference,
} from '../api/facilitiesApi';
import { editDeviceControl, registerDeviceControl, retireDeviceControl } from '../api/workflow';
import RowActions, { EditRowAction, RetireRowAction } from '../components/RowActions';
import { humaniseCode, orDash, relativeTime } from '../components/facilitiesFormat';
import { LifecycleDialog } from '../dialogs/common';
import { EditDeviceDialog, RegisterDeviceDialog } from '../dialogs/deviceDialogs';

/**
 * Device references - the identity and location of devices vendor systems operate.
 *
 * The facilities register does not run cameras, readers or panels; it owns where each one is, so that
 * a CCTV event, an access denial or a fire alarm can be placed in a space and a zone without every
 * consuming system inventing its own device registry.
 *
 * The reported time is the *vendor's* observation, not our receipt, which is why "last reported" can
 * be old on a device the feed is talking to constantly.
 */
const DeviceReferencesPage = () => {
  const notify = useNotifier();
  const [siteCode, setSiteCode] = useState<string>(defaultSite);
  const [type, setType] = useState<string>('');
  const [adding, setAdding] = useState(false);
  const [editing, setEditing] = useState<DeviceReference | null>(null);
  const [retiring, setRetiring] = useState<DeviceReference | null>(null);

  const { data, loading, error, refetch } = useApiQuery(
    (signal) =>
      listDeviceReferences(
        {
          siteCode: siteCode || undefined,
          type: (type as DeviceReferenceType) || undefined,
        },
        signal,
      ),
    [siteCode, type],
  );

  const columns: Column<DeviceReference>[] = [
    {
      key: 'deviceCode',
      header: 'Code',
      width: 150,
      cell: (device) => <span className="font-medium text-gray-900">{device.deviceCode}</span>,
    },
    { key: 'name', header: 'Device', cell: (device) => device.name },
    {
      key: 'type',
      header: 'Type',
      hideBelowLg: true,
      cell: (device) => humaniseCode(device.type),
    },
    {
      key: 'vendor',
      header: 'Vendor',
      width: 150,
      hideBelowLg: true,
      cell: (device) => <span className="text-gray-600">{orDash(device.vendor)}</span>,
    },
    {
      key: 'status',
      header: 'Reported status',
      width: 150,
      cell: (device) => (
        <StatusChip
          value={device.status}
          tone={
            device.status === 'ONLINE'
              ? 'ready'
              : device.status === 'OFFLINE'
                ? 'blocked'
                : device.status === 'DEGRADED'
                  ? 'caution'
                  : 'neutral'
          }
        />
      ),
    },
    {
      key: 'reported',
      header: 'Last reported',
      width: 160,
      align: 'right',
      cell: (device) => (
        <span className="text-gray-600">{relativeTime(device.statusReportedAt)}</span>
      ),
    },
    {
      key: 'actions',
      header: '',
      width: 150,
      align: 'right',
      cell: (device) => (
        <RowActions>
          <EditRowAction
            state={editDeviceControl(device)}
            onClick={() => setEditing(device)}
            label={`Edit ${device.deviceCode}`}
          />
          <RetireRowAction
            state={retireDeviceControl(device)}
            onClick={() => setRetiring(device)}
            label={`Retire ${device.deviceCode}`}
          />
        </RowActions>
      ),
    },
  ];

  return (
    <>
      <PageHeader
        title="Device references"
        subtitle="Where each vendor-operated device sits on this estate"
        actions={
          <ControlButton
            state={registerDeviceControl()}
            variant="primary"
            startIcon="plus"
            onClick={() => setAdding(true)}
          >
            Register a device
          </ControlButton>
        }
      />

      {/* Both controls labelled, so they sit on one line - see the note on the asset register. */}
      <FilterBar>
        <SiteSelect value={siteCode} onChange={setSiteCode} allowEmpty emptyLabel="All sites" />
        <SelectInput
          label="Device type"
          value={type}
          onChange={setType}
          allowEmpty
          emptyLabel="Any device type"
          options={deviceReferenceTypes.map((value) => ({ value, label: humaniseCode(value) }))}
        />
      </FilterBar>

      <DataState
        loading={loading}
        error={error}
        empty={!data || data.length === 0}
        emptyTitle="No device references"
        emptyHint="Register one so CCTV, access and life-safety events can be placed in a space."
        onRetry={refetch}
      >
        {data && (
          <>
            {data.some((device) => device.status === 'UNKNOWN') && (
              <Alert variant="info" className="mb-4">
                Devices showing UNKNOWN have never been reported on by their vendor system. The
                facilities register holds the reference; the vendor feed supplies the status.
              </Alert>
            )}
            <DataTable
              rows={data}
              columns={columns}
              getRowId={(device) => device.id}
              caption="Device references"
            />
          </>
        )}
      </DataState>

      {adding && (
        <RegisterDeviceDialog
          siteCode={siteCode || defaultSite}
          onClose={() => setAdding(false)}
          onSubmit={async (request) => {
            const created = await registerDeviceReference(request);
            setAdding(false);
            notify.notifySuccess(`${created.deviceCode} registered at ${created.siteCode}.`);
            refetch();
          }}
        />
      )}

      {editing && (
        <EditDeviceDialog
          device={editing}
          onClose={() => setEditing(null)}
          onSubmit={async (request) => {
            const saved = await updateDeviceReference(editing.id, request);
            setEditing(null);
            notify.notifySuccess(`${saved.deviceCode} updated.`);
            refetch();
          }}
        />
      )}

      {retiring && (
        <LifecycleDialog
          noun="device reference"
          label={retiring.deviceCode}
          current={retiring.lifecycleStatus}
          expectedVersion={retiring.metadata.version}
          onClose={() => setRetiring(null)}
          onSubmit={async (status, expectedVersion) => {
            const saved = await changeDeviceReferenceLifecycle(retiring.id, {
              status,
              expectedVersion,
            });
            setRetiring(null);
            notify.notifySuccess(`${saved.deviceCode} is now ${status.toLowerCase()}.`);
            refetch();
          }}
        />
      )}
    </>
  );
};

export default DeviceReferencesPage;
