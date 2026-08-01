import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { NotifierProvider } from 'shared/components/Notifier';
import type { FuelCard, FuelPageResponse } from '../api/dto';

const fuelCardsApi = vi.hoisted(() => ({
  search: vi.fn(),
  findById: vi.fn(),
  issue: vi.fn(),
  transition: vi.fn(),
}));

const vehiclesApi = vi.hoisted(() => ({ search: vi.fn() }));
const driversApi = vi.hoisted(() => ({ search: vi.fn() }));
const tripsApi = vi.hoisted(() => ({ search: vi.fn() }));
const permits = vi.hoisted(() => vi.fn<(permission?: string) => boolean>());

vi.mock('modules/fuel/api/fuelApi', () => ({ DEFAULT_PAGE_SIZE: 25, fuelCardsApi }));
vi.mock('modules/fleet/api/fleetApi', () => ({ vehiclesApi, driversApi, tripsApi }));
vi.mock('shared/layout/actorPermissions', () => ({ permits }));

const FuelCardsPage = (await import('./FuelCardsPage')).default;

const metadata = {
  createdBy: 'fleet.manager',
  createdAt: '2026-08-01T08:00:00Z',
  lastModifiedBy: 'fleet.manager',
  lastModifiedAt: '2026-08-01T08:00:00Z',
  version: 1,
  sourceChannel: 'WEB' as const,
  auditCorrelationId: 'corr-card-1',
};

const card = (overrides: Partial<FuelCard> = {}): FuelCard => ({
  id: 'card-1',
  siteCode: { value: 'CLET-HQ' },
  maskedReference: '****1234',
  provider: 'CLET FUEL CARDS',
  vehicleId: 'vehicle-1',
  driverId: 'driver-1',
  status: 'ACTIVE',
  issuedOn: '2026-08-01',
  expiresOn: '2027-08-01',
  dailyLimit: 250,
  monthlyLimit: null,
  perTransactionLimit: 100,
  suspensionReason: null,
  notes: 'Primary pool card',
  metadata,
  ...overrides,
});

const page = <T,>(content: T[]): FuelPageResponse<T> => ({
  content,
  page: 0,
  size: 25,
  totalElements: content.length,
  totalPages: content.length === 0 ? 0 : 1,
  first: true,
  last: true,
  sort: null,
});

const renderPage = () =>
  render(
    <MemoryRouter>
      <NotifierProvider>
        <FuelCardsPage />
      </NotifierProvider>
    </MemoryRouter>,
  );

const selectFirstCard = async (user: ReturnType<typeof userEvent.setup>) => {
  const cardLabel = await screen.findByText('****1234');
  const rowButton = cardLabel.closest('button');
  expect(rowButton).not.toBeNull();
  await user.click(rowButton!);
};

describe('FuelCardsPage', () => {
  beforeEach(() => {
    permits.mockReturnValue(true);
    fuelCardsApi.search.mockResolvedValue(page([card()]));
    fuelCardsApi.issue.mockResolvedValue(card({ id: 'card-2', maskedReference: '****9876' }));
    fuelCardsApi.transition.mockResolvedValue(
      card({ status: 'SUSPENDED', suspensionReason: 'Suspected misuse' }),
    );
    vehiclesApi.search.mockResolvedValue(page([]));
    driversApi.search.mockResolvedValue(page([]));
    tripsApi.search.mockResolvedValue(page([]));
  });

  it('shows the register but withholds mutation controls from a read-only fuel-card actor', async () => {
    const user = userEvent.setup();
    permits.mockImplementation((permission) => permission !== 'FUEL_CARD_MANAGE');

    renderPage();

    expect(await screen.findByText('****1234')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /issue card/i })).not.toBeInTheDocument();

    await selectFirstCard(user);

    expect(await screen.findByText('Read-only')).toBeInTheDocument();
    expect(
      screen.getByText(
        'You can inspect the card register, but issuing and lifecycle changes require FUEL_CARD_MANAGE.',
      ),
    ).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^assign$/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^suspend$/i })).not.toBeInTheDocument();
  });

  it('refuses full card numbers before they leave the browser', async () => {
    const user = userEvent.setup();

    renderPage();

    await screen.findByText('****1234');
    await user.click(screen.getByRole('button', { name: /issue card/i }));

    const dialog = await screen.findByRole('dialog', { name: /issue a fuel card/i });
    await user.type(within(dialog).getByLabelText(/^masked reference/i), '1234567890123456');
    await user.click(within(dialog).getByRole('button', { name: /^issue card$/i }));

    expect(
      await within(dialog).findByText(/enter only the masked provider reference/i),
    ).toBeInTheDocument();
    expect(fuelCardsApi.issue).not.toHaveBeenCalled();
  });

  it('issues a card using only the masked provider reference', async () => {
    const user = userEvent.setup();

    renderPage();

    await screen.findByText('****1234');
    await user.click(screen.getByRole('button', { name: /issue card/i }));

    const dialog = await screen.findByRole('dialog', { name: /issue a fuel card/i });
    await user.type(within(dialog).getByLabelText(/^masked reference/i), '****9876');
    await user.click(within(dialog).getByRole('button', { name: /^issue card$/i }));

    await waitFor(() =>
      expect(fuelCardsApi.issue).toHaveBeenCalledWith(
        expect.objectContaining({
          siteCode: 'CLET-HQ',
          maskedReference: '****9876',
          provider: 'CLET FUEL CARDS',
          vehicleId: null,
          driverId: null,
        }),
      ),
    );
    expect(await screen.findByText('****9876 issued.')).toBeInTheDocument();
  });

  it('submits card lifecycle transitions with a required reason', async () => {
    const user = userEvent.setup();

    renderPage();

    await selectFirstCard(user);
    await user.click(screen.getByRole('button', { name: /^suspend$/i }));

    const dialog = await screen.findByRole('dialog', { name: /suspend \*\*\*\*1234/i });
    await user.click(within(dialog).getByRole('button', { name: /^suspend card$/i }));
    expect(await within(dialog).findByText('Reason is required.')).toBeInTheDocument();

    await user.type(within(dialog).getByLabelText(/^reason/i), 'Suspected misuse');
    await user.click(within(dialog).getByRole('button', { name: /^suspend card$/i }));

    await waitFor(() =>
      expect(fuelCardsApi.transition).toHaveBeenCalledWith('card-1', 'suspend', {
        vehicleId: null,
        driverId: null,
        reason: 'Suspected misuse',
      }),
    );
  });
});
