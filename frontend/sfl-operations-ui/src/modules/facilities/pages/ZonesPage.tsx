import { useState } from 'react';
import ControlButton from 'shared/components/ControlButton';
import DataState from 'shared/components/DataState';
import DataTable, { CellStack, Column } from 'shared/components/DataTable';
import FilterBar from 'shared/components/FilterBar';
import PageHeader from 'shared/components/PageHeader';
import SectionCard from 'shared/components/SectionCard';
import SiteSelect, { defaultSite } from 'shared/components/SiteSelect';
import StatusChip from 'shared/components/StatusChip';
import { useNotifier } from 'shared/components/Notifier';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import type { Zone, ZoneMember } from '../api/dto';
import {
  addZoneMember,
  createZone,
  listBuildings,
  listDeviceReferences,
  listSpaces,
  listZoneMembers,
  listZones,
  removeZoneMember,
} from '../api/facilitiesApi';
import { createZoneControl, manageZoneMembersControl } from '../api/workflow';
import RowActions from '../components/RowActions';
import { formatDateTime, orDash } from '../components/facilitiesFormat';
import { AddZoneMemberDialog, CreateZoneDialog } from '../dialogs/zoneDialogs';

/**
 * Zones and what they cover.
 *
 * A zone is how the safety and emergency systems address the estate - life-safety events arrive per
 * zone, emergency broadcasts target recipient zones - so "what is actually in this zone" is the
 * question the screen exists to answer. Selecting a zone loads its membership rather than navigating
 * away, because the comparison between zones is the common task.
 *
 * Membership is edited here for the same reason it is read here: an empty zone resolves to nobody,
 * and the moment somebody notices that is the moment they should be able to fix it.
 */
const ZonesPage = () => {
  const notify = useNotifier();
  const [siteCode, setSiteCode] = useState<string>(defaultSite);
  const [selected, setSelected] = useState<Zone | null>(null);
  const [adding, setAdding] = useState(false);
  const [addingMember, setAddingMember] = useState(false);

  const zones = useApiQuery((signal) => listZones(siteCode || undefined, signal), [siteCode]);
  const members = useApiQuery(
    (signal) => (selected ? listZoneMembers(selected.id, signal) : Promise.resolve([])),
    [selected?.id],
  );

  /**
   * What each member actually is, rather than the identifier it is stored as.
   *
   * `ZoneMember` carries only `memberType` and `memberId`, so the register was a column of raw
   * UUIDs - which tells an operator deciding whether an evacuation zone covers the right rooms
   * precisely nothing. The names come from the same three registers the add dialog picks out of, so
   * the row reads as what it is and keeps the identifier underneath for support.
   *
   * Fetched per selected zone rather than per member: three requests for a whole zone, not one per
   * row. Where a name cannot be resolved - a record archived or outside this site - the identifier
   * stands on its own rather than being hidden behind a guess.
   */
  const memberNames = useApiQuery(
    async (signal) => {
      if (!selected) {
        return new Map<string, string>();
      }
      const [spaces, buildings, devices] = await Promise.all([
        listSpaces(selected.siteCode, signal),
        listBuildings(selected.siteCode, signal),
        listDeviceReferences({ siteCode: selected.siteCode }, signal),
      ]);
      return new Map<string, string>([
        ...spaces.map((space): [string, string] => [space.id, `${space.roomCode} · ${space.name}`]),
        ...buildings.map((building): [string, string] => [
          building.id,
          `${building.buildingCode} · ${building.name}`,
        ]),
        ...devices.map((device): [string, string] => [
          device.id,
          `${device.deviceCode} · ${device.name}`,
        ]),
      ]);
    },
    [selected?.id, selected?.siteCode],
  );

  const zoneColumns: Column<Zone>[] = [
    {
      key: 'zoneCode',
      header: 'Code',
      width: 150,
      cell: (zone) => <span className="font-medium text-gray-900">{zone.zoneCode}</span>,
    },
    { key: 'name', header: 'Zone', cell: (zone) => zone.name },
    {
      key: 'purpose',
      header: 'Purpose',
      hideBelowLg: true,
      cell: (zone) => <span className="text-gray-600">{orDash(zone.purpose)}</span>,
    },
    {
      key: 'parent',
      header: 'Nested',
      width: 110,
      align: 'right',
      cell: (zone) =>
        zone.parentZoneId ? <StatusChip value="NESTED" label="Nested" tone="neutral" /> : null,
    },
  ];

  const memberColumns: Column<ZoneMember>[] = [
    {
      key: 'memberType',
      header: 'Type',
      width: 130,
      cell: (member) => <StatusChip value={member.memberType} tone="neutral" />,
    },
    {
      key: 'memberId',
      header: 'Record',
      cell: (member) => {
        const name = memberNames.data?.get(member.memberId);
        return name ? (
          <CellStack primary={name} secondary={member.memberId} />
        ) : (
          <span className="font-mono text-theme-xs">{member.memberId}</span>
        );
      },
    },
    {
      key: 'addedBy',
      header: 'Added by',
      width: 160,
      hideBelowLg: true,
      cell: (member) => member.addedBy,
    },
    {
      key: 'addedAt',
      header: 'Added',
      width: 190,
      align: 'right',
      cell: (member) => <span className="text-gray-600">{formatDateTime(member.addedAt)}</span>,
    },
    {
      key: 'actions',
      header: '',
      width: 110,
      align: 'right',
      cell: (member) => (
        <RowActions>
          <ControlButton
            state={selected ? manageZoneMembersControl(selected) : { kind: 'hidden' }}
            variant="ghost"
            size="sm"
            onClick={() => void removeMember(member)}
          >
            Remove
          </ControlButton>
        </RowActions>
      ),
    },
  ];

  /**
   * Removing a member.
   *
   * No confirmation dialog, and that is deliberate rather than an omission: the act is a single
   * reversible click, the row states exactly what is being removed, and adding it back is the button
   * directly above. A confirmation here would be ceremony. The irreversible acts in this module -
   * archiving - do get one.
   */
  const removeMember = async (member: ZoneMember) => {
    if (!selected) {
      return;
    }
    try {
      await removeZoneMember(selected.id, member.memberType, member.memberId);
      notify.notifySuccess(`Removed from ${selected.zoneCode}.`);
      members.refetch();
    } catch (cause) {
      notify.notifyError(cause);
    }
  };

  return (
    <>
      <PageHeader
        title="Zones"
        subtitle="How safety, life-safety and emergency systems address this estate"
        actions={
          <ControlButton
            state={createZoneControl()}
            variant="primary"
            startIcon="plus"
            onClick={() => setAdding(true)}
          >
            Add a zone
          </ControlButton>
        }
      />

      <FilterBar>
        <SiteSelect
          value={siteCode}
          onChange={(value) => {
            setSiteCode(value);
            setSelected(null);
          }}
          allowEmpty
          emptyLabel="All sites"
        />
      </FilterBar>

      <div className="space-y-5">
        <SectionCard title="Zones" subtitle="Select one to see what it covers">
          <DataState
            loading={zones.loading}
            error={zones.error}
            empty={!zones.data || zones.data.length === 0}
            emptyTitle="No zones configured"
            emptyHint="A zone is what an evacuation broadcast or a life-safety alarm resolves against."
            onRetry={zones.refetch}
          >
            {zones.data && (
              <DataTable
                rows={zones.data}
                columns={zoneColumns}
                getRowId={(zone) => zone.id}
                onRowClick={setSelected}
                caption="Zones"
                dense
              />
            )}
          </DataState>
        </SectionCard>

        {selected && (
          <SectionCard
            title={`What ${selected.zoneCode} covers`}
            subtitle={`${selected.name}${selected.purpose ? ` · ${selected.purpose}` : ''}`}
            actions={
              <ControlButton
                state={manageZoneMembersControl(selected)}
                variant="outline"
                size="sm"
                startIcon="plus"
                onClick={() => setAddingMember(true)}
              >
                Add a record
              </ControlButton>
            }
          >
            <DataState
              loading={members.loading}
              error={members.error}
              empty={!members.data || members.data.length === 0}
              emptyTitle="This zone is empty"
              emptyHint="A zone with no members resolves to nothing - an alarm against it would reach nobody."
              onRetry={members.refetch}
              minHeight={120}
            >
              {members.data && (
                <DataTable
                  rows={members.data}
                  columns={memberColumns}
                  getRowId={(member) => member.id}
                  dense
                />
              )}
            </DataState>
          </SectionCard>
        )}
      </div>

      {adding && (
        <CreateZoneDialog
          siteCode={siteCode || defaultSite}
          onClose={() => setAdding(false)}
          onSubmit={async (request) => {
            const created = await createZone(request);
            setAdding(false);
            notify.notifySuccess(`${created.zoneCode} added to ${created.siteCode}.`);
            zones.refetch();
          }}
        />
      )}

      {addingMember && selected && (
        <AddZoneMemberDialog
          zone={selected}
          onClose={() => setAddingMember(false)}
          onSubmit={async (request) => {
            await addZoneMember(selected.id, request);
            setAddingMember(false);
            notify.notifySuccess(`Added to ${selected.zoneCode}.`);
            members.refetch();
          }}
        />
      )}
    </>
  );
};

export default ZonesPage;
