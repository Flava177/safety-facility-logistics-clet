import { CurrencyValue, SiteCodeValue } from 'modules/fuel/api/dto';

/**
 * Fuel-specific display helpers.
 *
 * The general ones (`formatDateTime`, `formatNumber`, `formatOdometer`) are in
 * `shared/components/format` and are used unchanged. These three exist because the fuel wire types
 * carry two value objects the rest of the console never sees, and because money on this module is
 * always a `BigDecimal` paired with an ISO currency code rather than a bare number.
 */

/** `SiteCode` serialises as `{ value }`, not as a string. */
export const siteOf = (site: SiteCodeValue | string | null | undefined): string => {
  if (!site) {
    return '—';
  }
  return typeof site === 'string' ? site : site.value;
};

export const currencyCodeOf = (currency: CurrencyValue | null | undefined): string =>
  currency?.currencyCode ?? '';

/**
 * Money with its code — "GHS 1,240.00".
 *
 * The code leads rather than a symbol: the fleet operates in cedis but a provider import can carry
 * any ISO code, and `GHS 200.00` beside `USD 200.00` is unambiguous where two currency symbols an
 * operator has to recognise are not. Two decimal places always, matching the domain's own scale.
 */
export const formatMoney = (
  amount: number | null | undefined,
  currency?: CurrencyValue | string | null,
): string => {
  if (amount === null || amount === undefined) {
    return '—';
  }
  const code = typeof currency === 'string' ? currency : currencyCodeOf(currency);
  const value = amount.toLocaleString(undefined, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
  return code ? `${code} ${value}` : value;
};

/**
 * Quantity with its unit — "20.000 L".
 *
 * Three decimal places because that is the scale `FuelTransaction` rounds to, so the console never
 * shows a figure the record does not hold.
 */
export const formatQuantity = (
  quantity: number | null | undefined,
  unit?: string | null,
): string => {
  if (quantity === null || quantity === undefined) {
    return '—';
  }
  const value = quantity.toLocaleString(undefined, {
    minimumFractionDigits: 3,
    maximumFractionDigits: 3,
  });
  const suffix = unit ? UNIT_ABBREVIATIONS[unit.toUpperCase()] ?? unit.toLowerCase() : '';
  return suffix ? `${value} ${suffix}` : value;
};

const UNIT_ABBREVIATIONS: Record<string, string> = {
  LITRE: 'L',
  LITRES: 'L',
  GALLON: 'gal',
  GALLONS: 'gal',
  KILOGRAM: 'kg',
  KILOGRAMS: 'kg',
};

/** Unit price, at the four decimal places the domain stores. */
export const formatUnitPrice = (
  price: number | null | undefined,
  currency?: CurrencyValue | string | null,
): string => {
  if (price === null || price === undefined) {
    return '—';
  }
  const code = typeof currency === 'string' ? currency : currencyCodeOf(currency);
  const value = price.toLocaleString(undefined, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 4,
  });
  return code ? `${code} ${value}` : value;
};

/** Shortens a UUID for a dense table cell. The full value stays in the row's title attribute. */
export const shortId = (id: string | null | undefined): string =>
  id ? id.slice(0, 8) : '—';

/** "in 4 hours" / "6 hours overdue" — how an SLA reads in a queue. */
export const formatDueIn = (dueAt: string | null | undefined, now = Date.now()): string => {
  if (!dueAt) {
    return '—';
  }
  const due = new Date(dueAt).getTime();
  if (Number.isNaN(due)) {
    return '—';
  }
  const minutes = Math.round((due - now) / 60000);
  const overdue = minutes < 0;
  const absolute = Math.abs(minutes);
  const text =
    absolute < 60
      ? `${absolute} minute${absolute === 1 ? '' : 's'}`
      : absolute < 60 * 48
        ? `${Math.round(absolute / 60)} hour${Math.round(absolute / 60) === 1 ? '' : 's'}`
        : `${Math.round(absolute / 1440)} days`;
  return overdue ? `${text} overdue` : `in ${text}`;
};
