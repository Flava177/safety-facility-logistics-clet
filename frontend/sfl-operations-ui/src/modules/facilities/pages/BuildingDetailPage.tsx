import { useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import Alert from 'shared/components/Alert';
import Button from 'shared/components/Button';
import DataState from 'shared/components/DataState';
import DataTable, { CellStack, Column } from 'shared/components/DataTable';
import KeyValueGrid from 'shared/components/KeyValueGrid';
import PageHeader from 'shared/components/PageHeader';
import SectionCard from 'shared/components/SectionCard';
import StatusChip from 'shared/components/StatusChip';
import { useNotifier } from 'shared/components/Notifier';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { facilitiesPaths } from 'shared/layout/navigation';
import type { CreateFloorRequest, Floor, Space } from '../api/dto';
import { createFloor, getBuilding, listFloors, searchSpaces } from '../api/facilitiesApi';
import { canManageSpaces } from '../api/workflow';
import CreateFloorDialog from '../dialogs/CreateFloorDialog';
import {
  floorLabel,
  formatDateTime,
  humaniseCode,
  orDash,
  readinessTone,
} from '../components/facilitiesFormat';

/**
 * One building, its floors, and what is on the floor being looked at.
 *
 * ## The screen that was missing from the middle of the hierarchy
 *
 * S152's estate is Site → Building → Floor → Space, and until now the dashboard had screens for the
 * first, second and fourth. `listFloors`, `getFloor` and `createFloor` were written, exported and
 * called by nothing, and a building row on the site page led nowhere — so the only way to place a
 * space was to already know a floor id.
 *
 * ## Why floors are a list beside the spaces rather than a page of their own
 *
 * A floor has four fields and no behaviour: a code, a name, a level number and a lifecycle status.
 * Nothing is ever done *to* a floor. What somebody wants from "the second floor" is **what is on
 * it**, so selecting one filters the spaces beside it rather than navigating away — and the building
 * stays on screen, which is the context that makes a floor mean anything.
 *
 * ## Level number is nullable and signed, and the sort has to survive both
 *
 * Basements are negative and mezzanines have no level at all. The service returns them lowest level
 * first; this screen preserves that order rather than re-sorting, because a client that sorted
 * `null` as zero would file every mezzanine at ground level.
 */
const BuildingDetailPage = () => {
  const { buildingId = '' } = useParams();
  const navigate = useNavigate();
  const notify = useNotifier();
  const [selectedFloor, setSelectedFloor] = useState<string | null>(null);
  const [addingFloor, setAddingFloor] = useState(false);

  const building = useApiQuery((signal) => getBuilding(buildingId, signal), [buildingId]);
  const floors = useApiQuery((signal) => listFloors(buildingId, signal), [buildingId]);

  /*
    Scoped to the building when no floor is chosen, so the page opens on everything in it rather than
    on nothing. `floorId` narrows within that — both are server-side filters, because a client
    filtering a page of fifty rooms would be filtering a page rather than the register.
  */
  const spaces = useApiQuery(
    (signal) =>
      searchSpaces(
        { buildingId, floorId: selectedFloor ?? undefined, size: 100 },
        signal,
      ),
    [buildingId, selectedFloor],
  );

  const floorRows = floors.data ?? [];
  const chosen = floorRows.find((floor) => floor.id === selectedFloor) ?? null;
  const mayManage = canManageSpaces();

  const floorColumns: Column<Floor>[] = [
    {
      key: 'floorCode',
      header: 'Floor',
      width: 200,
      cell: (floor) => (
        <CellStack primary={floorLabel(floor.levelNumber, floor.floorCode)} secondary={floor.name} />
      ),
    },
    {
      key: 'lifecycle',
      header: 'Lifecycle',
      width: 120,
      cell: (floor) => <StatusChip value={floor.lifecycleStatus} />,
    },
    {
      key: 'view',
      header: 'Spaces',
      align: 'right',
      width: 150,
      cell: (floor) => (
        <Button
          size="sm"
          variant={floor.id === selectedFloor ? 'primary' : 'outline'}
          onClick={() => setSelectedFloor(floor.id === selectedFloor ? null : floor.id)}
        >
          {floor.id === selectedFloor ? 'Showing' : 'Show'}
        </Button>
      ),
    },
  ];

  const spaceColumns: Column<Space>[] = [
    {
      key: 'roomCode',
      header: 'Code',
      width: 150,
      cell: (space) => <span className="font-medium text-gray-900">{space.roomCode}</span>,
    },
    { key: 'name', header: 'Space', cell: (space) => space.name },
    {
      key: 'spaceType',
      header: 'Type',
      hideBelowLg: true,
      cell: (space) => humaniseCode(space.spaceType),
    },
    {
      key: 'capacity',
      header: 'Seats',
      align: 'right',
      width: 90,
      hideBelowLg: true,
      cell: (space) => orDash(space.capacity),
    },
    {
      key: 'readiness',
      header: 'Readiness',
      width: 130,
      align: 'right',
      cell: (space) => (
        <StatusChip value={space.readinessStatus} tone={readinessTone(space.readinessStatus)} />
      ),
    },
  ];

  const submitFloor = async (request: CreateFloorRequest) => {
    const created = await createFloor(request);
    setAddingFloor(false);
    notify.notifySuccess(`${created.floorCode} added to this building.`);
    floors.refetch();
    // Select it: somebody who has just created a floor is about to put spaces on it.
    setSelectedFloor(created.id);
  };

  return (
    <>
      <DataState
        loading={building.loading}
        error={building.error}
        empty={!building.data}
        emptyTitle="Building not found"
        onRetry={building.refetch}
        minHeight={280}
      >
        {building.data && (
          <>
            <PageHeader
              title={building.data.name}
              subtitle={`${building.data.buildingCode} · ${building.data.siteCode}`}
              crumbs={[
                { label: 'Facilities', to: facilitiesPaths.dashboard },
                { label: 'Sites', to: facilitiesPaths.sites },
                { label: building.data.siteCode, to: facilitiesPaths.siteDetail(building.data.siteId) },
                { label: building.data.buildingCode },
              ]}
              meta={<StatusChip value={building.data.lifecycleStatus} size="md" />}
              actions={
                mayManage ? (
                  <Button startIcon="plus" onClick={() => setAddingFloor(true)}>
                    Add a floor
                  </Button>
                ) : undefined
              }
            />

            <div className="space-y-5">
              <SectionCard title="Building record">
                <KeyValueGrid
                  items={[
                    { label: 'Code', value: building.data.buildingCode },
                    { label: 'Site', value: building.data.siteCode },
                    { label: 'Description', value: orDash(building.data.description) },
                    { label: 'Registered', value: formatDateTime(building.data.createdAt) },
                    { label: 'Registered by', value: building.data.metadata.createdBy },
                    { label: 'Version', value: String(building.data.metadata.version) },
                  ]}
                />
              </SectionCard>

              <div className="grid gap-5 xl:grid-cols-[minmax(0,26rem)_minmax(0,1fr)]">
                <SectionCard
                  title="Floors"
                  subtitle="Lowest level first. Choose one to see what is on it."
                  flush
                >
                  <DataState
                    loading={floors.loading}
                    error={floors.error}
                    empty={floorRows.length === 0}
                    emptyTitle="No floors registered"
                    emptyHint={
                      mayManage
                        ? 'A space is placed on a floor, so this building needs one before it can hold anything.'
                        : 'A space is placed on a floor, so nothing can be placed in this building yet.'
                    }
                    minHeight={180}
                    onRetry={floors.refetch}
                  >
                    <DataTable
                      rows={floorRows}
                      columns={floorColumns}
                      getRowId={(floor) => floor.id}
                      dense
                      caption="Floors in this building"
                    />
                  </DataState>
                </SectionCard>

                <SectionCard
                  title={chosen ? `Spaces on ${floorLabel(chosen.levelNumber, chosen.floorCode)}` : 'Spaces'}
                  subtitle={
                    chosen
                      ? chosen.name
                      : 'Everything in this building. Choose a floor to narrow it.'
                  }
                  actions={
                    chosen ? (
                      <Button size="sm" variant="ghost" onClick={() => setSelectedFloor(null)}>
                        Show the whole building
                      </Button>
                    ) : undefined
                  }
                  flush
                >
                  <DataState
                    loading={spaces.loading}
                    error={spaces.error}
                    empty={!spaces.data || spaces.data.items.length === 0}
                    emptyTitle={chosen ? 'Nothing on this floor' : 'No spaces in this building'}
                    emptyHint="Spaces are registered against a floor from the space register."
                    minHeight={180}
                    onRetry={spaces.refetch}
                  >
                    {spaces.data && (
                      <DataTable
                        rows={spaces.data.items}
                        columns={spaceColumns}
                        getRowId={(space) => space.id}
                        onRowClick={(space) => navigate(facilitiesPaths.spaceDetail(space.id))}
                        dense
                        caption="Spaces"
                      />
                    )}
                  </DataState>
                </SectionCard>
              </div>

              {floorRows.length > 0 && spaces.data && spaces.data.totalElements > 100 && (
                <Alert variant="info" title="Showing the first 100 spaces">
                  This building has {spaces.data.totalElements.toLocaleString()}. Choose a floor, or
                  use the space register, which pages properly.
                </Alert>
              )}
            </div>
          </>
        )}
      </DataState>

      {addingFloor && building.data && (
        <CreateFloorDialog
          building={building.data}
          existingFloors={floorRows}
          onClose={() => setAddingFloor(false)}
          onSubmit={submitFloor}
        />
      )}
    </>
  );
};

export default BuildingDetailPage;
