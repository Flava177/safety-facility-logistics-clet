import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { NotifierProvider } from 'shared/components/Notifier';
import type { Building, Floor, Space } from '../api/dto';

/**
 * The building screen, and the one behaviour on it worth pinning: **choosing a floor asks the server
 * again**.
 *
 * The alternative — fetching the building's spaces once and filtering the array in the browser — is
 * the obvious implementation and is wrong here for a reason a rendering test can catch. The query is
 * capped at a hundred rows, so a client-side filter over a large building would filter the first page
 * rather than the floor, and the third floor of a big block would appear empty. Asserting the request
 * carried `floorId` is asserting that the narrowing happened where it can be true.
 */

const getBuilding = vi.hoisted(() => vi.fn());
const listFloors = vi.hoisted(() => vi.fn());
const searchSpaces = vi.hoisted(() => vi.fn());
const createFloor = vi.hoisted(() => vi.fn());
const permits = vi.hoisted(() => vi.fn<(permission?: string) => boolean>());

vi.mock('../api/facilitiesApi', () => ({ getBuilding, listFloors, searchSpaces, createFloor }));
vi.mock('shared/layout/actorPermissions', () => ({ permits }));

const BuildingDetailPage = (await import('./BuildingDetailPage')).default;

const building: Building = {
  id: 'building-1',
  siteId: 'site-1',
  siteCode: 'ACCRA',
  buildingCode: 'MAIN',
  name: 'Main Block',
  description: 'Teaching and moot courts',
  lifecycleStatus: 'ACTIVE',
  createdAt: '2026-01-04T09:00:00Z',
  metadata: {
    createdBy: 'ama.mensah',
    createdAt: '2026-01-04T09:00:00Z',
    lastModifiedBy: 'ama.mensah',
    lastModifiedAt: '2026-01-04T09:00:00Z',
    version: 0,
    sourceChannel: 'WEB',
    correlationId: null,
  },
};

const floor = (id: string, floorCode: string, levelNumber: number | null): Floor =>
  ({
    id,
    buildingId: 'building-1',
    siteCode: 'ACCRA',
    floorCode,
    name: `${floorCode} name`,
    levelNumber,
    lifecycleStatus: 'ACTIVE',
    createdAt: '2026-01-04T09:00:00Z',
    metadata: building.metadata,
  }) as Floor;

const space = (roomCode: string): Space =>
  ({ id: `room-${roomCode}`, roomCode, name: `${roomCode} hall`, spaceType: 'LECTURE_HALL', capacity: 80, readinessStatus: 'READY' }) as Space;

const page = (items: Space[]) => ({ items, totalElements: items.length, totalPages: 1, page: 0, size: 100 });

const renderPage = () =>
  render(
    <MemoryRouter initialEntries={['/facilities/buildings/building-1']}>
      <NotifierProvider>
        <Routes>
          <Route path="/facilities/buildings/:buildingId" element={<BuildingDetailPage />} />
        </Routes>
      </NotifierProvider>
    </MemoryRouter>,
  );

beforeEach(() => {
  vi.clearAllMocks();
  permits.mockReturnValue(true);
  getBuilding.mockResolvedValue(building);
  // Lowest level first, as the service returns them. A basement and a mezzanine, because those are
  // the two the label has to handle and the two a naive sort gets wrong.
  listFloors.mockResolvedValue([floor('f-b1', 'B1', -1), floor('f-gf', 'GF', 0), floor('f-mz', 'MEZZ', null)]);
  searchSpaces.mockResolvedValue(page([space('HALL-A'), space('HALL-B')]));
});

describe('BuildingDetailPage', () => {
  it('opens on the whole building rather than on nothing', async () => {
    renderPage();

    await waitFor(() => expect(screen.getByText('Main Block')).toBeInTheDocument());
    // No floor chosen, so the space query is scoped to the building and carries no floorId.
    expect(searchSpaces).toHaveBeenCalledWith(
      expect.objectContaining({ buildingId: 'building-1', floorId: undefined }),
      expect.anything(),
    );
    expect(screen.getByText('HALL-A')).toBeInTheDocument();
  });

  it('names a basement and a mezzanine rather than showing a bare number', async () => {
    renderPage();

    await waitFor(() => expect(screen.getByText('B1 · basement 1')).toBeInTheDocument());
    expect(screen.getByText('GF · ground')).toBeInTheDocument();
    // The case a blank cell would misreport as missing data.
    expect(screen.getByText('MEZZ · no level')).toBeInTheDocument();
  });

  it('asks the server again when a floor is chosen', async () => {
    const user = userEvent.setup();
    renderPage();
    await waitFor(() => expect(screen.getAllByRole('button', { name: 'Show' }).length).toBe(3));

    searchSpaces.mockResolvedValue(page([space('HALL-A')]));
    await user.click(screen.getAllByRole('button', { name: 'Show' })[1]);

    await waitFor(() =>
      expect(searchSpaces).toHaveBeenLastCalledWith(
        expect.objectContaining({ buildingId: 'building-1', floorId: 'f-gf' }),
        expect.anything(),
      ),
    );
  });

  it('hides the add-a-floor control from an actor who cannot manage spaces', async () => {
    // Hidden rather than disabled: a control somebody will never be allowed to press is noise.
    permits.mockReturnValue(false);
    renderPage();

    await waitFor(() => expect(screen.getByText('Main Block')).toBeInTheDocument());
    expect(screen.queryByRole('button', { name: 'Add a floor' })).not.toBeInTheDocument();
  });

  it('tells an operator why an empty building matters', async () => {
    listFloors.mockResolvedValue([]);
    renderPage();

    // Not "no floors" alone: the consequence is that nothing can be placed here at all, and that is
    // the thing somebody registering a building needs to be told.
    await waitFor(() => expect(screen.getByText('No floors registered')).toBeInTheDocument());
    expect(screen.getByText(/needs one before it can hold anything/)).toBeInTheDocument();
  });
});
