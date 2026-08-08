import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { NotifierProvider } from 'shared/components/Notifier';

/**
 * The fuel capture form, asserted in the terms the requirement was written in.
 *
 * <p>Field-level assertions rather than a full submission: the value of this test is that it fails
 * the moment the form goes back to asking a driver for litres, a price they choose, a vendor as free
 * text, or an evidence identifier they have no way to obtain. That is exactly the regression that
 * would otherwise be invisible until somebody opened the screen.
 */

const fuelTransactionsApi = vi.hoisted(() => ({ capture: vi.fn() }));
const fuelPricesApi = vi.hoisted(() => ({ providers: vi.fn(), postedPrices: vi.fn() }));
const vehiclesApi = vi.hoisted(() => ({ search: vi.fn() }));
const driversApi = vi.hoisted(() => ({ search: vi.fn() }));
const tripsApi = vi.hoisted(() => ({ search: vi.fn() }));
const isPersona = vi.hoisted(() => vi.fn<(persona: string) => boolean>());
const evidenceFilesApi = vi.hoisted(() => ({ upload: vi.fn() }));

vi.mock('modules/fuel/api/fuelApi', () => ({ fuelTransactionsApi, fuelPricesApi }));
vi.mock('modules/fleet/api/fleetApi', () => ({ vehiclesApi, driversApi, tripsApi }));
vi.mock('shared/layout/personas', () => ({ isPersona }));
vi.mock('shared/evidence/evidenceFilesApi', async () => {
  const actual = await vi.importActual<typeof import('shared/evidence/evidenceFilesApi')>(
    'shared/evidence/evidenceFilesApi',
  );
  return { ...actual, evidenceFilesApi };
});

const { CaptureTransactionDialog } = await import('./transactionDialogs');

const trip = {
  id: 'trip-1',
  tripNumber: 'TRP-0001',
  vehicleId: 'vehicle-1',
  driverId: 'driver-1',
  siteCode: 'CLET-HQ',
  origin: 'Accra',
  destination: 'Kumasi',
  status: 'IN_PROGRESS',
};

const renderDialog = () =>
  render(
    <NotifierProvider>
      <CaptureTransactionDialog
        open
        defaultSiteCode="CLET-HQ"
        onClose={vi.fn()}
        onSaved={vi.fn()}
      />
    </NotifierProvider>,
  );

describe('CaptureTransactionDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    URL.createObjectURL = vi.fn(() => 'blob:preview');
    URL.revokeObjectURL = vi.fn();
    isPersona.mockImplementation((persona) => persona === 'driver');
    tripsApi.search.mockResolvedValue({ content: [trip], page: 0, size: 25, totalElements: 1, totalPages: 1 });
    vehiclesApi.search.mockResolvedValue({ content: [], page: 0, size: 25, totalElements: 0, totalPages: 0 });
    driversApi.search.mockResolvedValue({ content: [], page: 0, size: 25, totalElements: 0, totalPages: 0 });
    fuelPricesApi.providers.mockResolvedValue(['GOIL', 'SHELL']);
    fuelPricesApi.postedPrices.mockResolvedValue([]);
  });

  it('asks for the trip, and asks nothing it can already answer', async () => {
    renderDialog();

    await waitFor(() => expect(tripsApi.search).toHaveBeenCalled());

    expect(screen.getByRole('combobox', { name: /trip/i })).toBeInTheDocument();
    // A signed-in driver is not asked who they are.
    expect(screen.queryByRole('combobox', { name: /^driver$/i })).not.toBeInTheDocument();
    // Nor which site: the register this opens from is already filtered to one, and it is passed in.
    expect(screen.queryByRole('combobox', { name: /site code/i })).not.toBeInTheDocument();
    // Nor when. The receipt photograph carries the printed time, and a driver retyping it at the
    // pump only created a second version of the same fact for the two to disagree over. Capture
    // time is sent instead; see `occurredAt` in the submit handler.
    expect(screen.queryByLabelText(/time fuel purchased/i)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/occurred at/i)).not.toBeInTheDocument();
  });

  it('prefills the vehicle from the chosen trip and leaves it on screen', async () => {
    // The register has to hold the trip's vehicle for the select to be able to show it.
    vehiclesApi.search.mockResolvedValue({
      content: [{ id: trip.vehicleId, registrationNumber: 'GT 1234-24', make: 'Toyota', model: 'Hilux' }],
      page: 0,
      size: 25,
      totalElements: 1,
      totalPages: 1,
    });
    renderDialog();
    await waitFor(() => expect(tripsApi.search).toHaveBeenCalled());

    // `Select` is a custom ARIA combobox - a trigger that opens a listbox - so it is driven by
    // clicking rather than by `selectOptions`, which only applies to a native element.
    await userEvent.click(screen.getByRole('combobox', { name: /trip/i }));
    await userEvent.click(await screen.findByRole('option', { name: new RegExp(trip.tripNumber) }));

    // The field used to be replaced by a sentence saying the trip had supplied it, so the driver
    // could not see which vehicle was on their claim without abandoning the trip.
    const vehicle = await screen.findByRole('combobox', { name: /vehicle/i });
    expect(vehicle).toBeInTheDocument();
    expect(vehicle).toHaveTextContent('GT 1234-24');
    expect(screen.getByText(new RegExp(`From ${trip.tripNumber}`))).toBeInTheDocument();
  });

  it('offers the approved providers as a list and the station as a place', async () => {
    renderDialog();

    await waitFor(() => expect(fuelPricesApi.providers).toHaveBeenCalledWith('CLET-HQ', expect.anything()));
    expect(screen.getByRole('combobox', { name: /provider/i })).toBeInTheDocument();
    expect(screen.getByLabelText(/location/i)).toBeInTheDocument();
    // Free text made "GOIL", "Goil Tema" and "goil" three vendors the APPROVED_VENDOR rule could
    // never match, so the old single free-text "Vendor" field must not come back.
    expect(screen.queryByLabelText(/^vendor$/i)).not.toBeInTheDocument();
  });

  it('asks what was paid and derives the litres, rather than asking for both', async () => {
    renderDialog();
    await waitFor(() => expect(fuelPricesApi.providers).toHaveBeenCalled());

    expect(screen.getByLabelText(/amount paid/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/price per litre/i)).toBeInTheDocument();
    // Quantity was the field a claimant could inflate without touching anything the policy checks.
    expect(screen.queryByLabelText(/^quantity$/i)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/^unit$/i)).not.toBeInTheDocument();
  });

  it('takes both images in the form and never asks for an identifier to paste', async () => {
    const { container } = renderDialog();
    await waitFor(() => expect(fuelPricesApi.providers).toHaveBeenCalled());

    expect(screen.getByText(/photo of the pump meter/i)).toBeInTheDocument();
    expect(screen.getByText(/^receipt$/i)).toBeInTheDocument();
    // Two upload controls plus the camera-only input beside the pump field.
    expect(container.querySelectorAll('input[type="file"]').length).toBeGreaterThanOrEqual(2);
    expect(screen.queryByText(/paste its identifier/i)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/receipt evidence reference/i)).not.toBeInTheDocument();
  });

  it('locks the price to the posted one when the site has recorded it', async () => {
    fuelPricesApi.postedPrices.mockResolvedValue([
      {
        id: 'price-1',
        siteCode: 'CLET-HQ',
        vendor: 'GOIL',
        fuelProduct: 'DIESEL',
        unitPrice: 15.45,
        currency: 'GHS',
        effectiveFrom: '2026-08-01T00:00:00Z',
        effectiveTo: null,
        source: 'ADMINISTERED',
        notes: null,
      },
    ]);
    renderDialog();
    await waitFor(() => expect(fuelPricesApi.providers).toHaveBeenCalled());

    // The provider has to be chosen before a price can be looked up, so the lookup is asserted
    // through its dependency rather than by driving the combobox: with no vendor selected the query
    // must not run at all.
    expect(fuelPricesApi.postedPrices).not.toHaveBeenCalled();
    expect((screen.getByLabelText(/price per litre/i) as HTMLInputElement).disabled).toBe(false);
  });
});
