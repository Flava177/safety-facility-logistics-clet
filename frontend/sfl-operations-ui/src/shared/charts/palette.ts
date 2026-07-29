/**
 * Chart colours, taken from the CLET design system's Base collection.
 *
 * ApexCharts renders to SVG with inline attributes and cannot resolve the CSS custom properties the
 * rest of the dashboard is themed with, so the values are restated here as literals. They are copied
 * from `src/index.css` — change one, change the other, or a chart and its legend will drift apart.
 *
 * The categorical order is deliberate and short. Deep Teal, CLET Gold and a mid navy are far apart
 * in both hue and lightness, so they stay separable in greyscale and to a viewer with deuteranopia
 * (SC 1.4.1 — colour is never the only cue; every series is also labelled). A fourth and fifth step
 * exist for the rare chart that needs them, but a panel that wants six series usually wants a table.
 */

export const chartColors = {
  navy: '#0a1931',
  navyMid: '#3e5f8a',
  teal: '#0c4a6e',
  tealMid: '#0284c7',
  gold: '#b8960c',
  goldSoft: '#facc15',
  success: '#008236',
  warning: '#b45309',
  error: '#99080f',
  grey: '#71717a',
  greyLine: '#f4f4f5',
  text: '#52525b',
} as const;

/** Categorical sequence for series that carry no inherent status meaning. */
export const seriesColors = [
  chartColors.teal,
  chartColors.gold,
  chartColors.navyMid,
  chartColors.tealMid,
  chartColors.grey,
] as const;

/**
 * Tone-consistent with `StatusChip`: ready / caution / blocked read the same everywhere.
 * These are the 700–800 steps, so a thin bar or line still clears 3:1 against white (SC 1.4.11).
 */
export const toneColors = {
  ready: chartColors.success,
  caution: chartColors.warning,
  blocked: chartColors.error,
  neutral: chartColors.grey,
  active: chartColors.teal,
  accent: chartColors.gold,
} as const;

export const chartFont = "Lato, 'Segoe UI', system-ui, -apple-system, Arial, sans-serif";
