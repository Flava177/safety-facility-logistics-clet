import { ReactNode, useState } from 'react';
import {
  Area,
  AreaChart as RechartsArea,
  Bar,
  BarChart as RechartsBar,
  CartesianGrid,
  Cell,
  Legend,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { chartColors, seriesColors } from './palette';

/**
 * The dashboard's charts, on Recharts.
 *
 * <h2>Why the exported shape did not change</h2>
 *
 * `AreaChart`, `BarChart`, `DonutChart` and `Sparkline` keep the props they had under ApexCharts —
 * `categories` and `series` for the first two, `labels`/`values`/`colors` for the donut. Eight module
 * files import them and none needed editing. A migration that also redesigned the API would have
 * meant reviewing eight dashboards for two unrelated reasons at once, and any rendering difference
 * would have been impossible to attribute.
 *
 * <h2>What Recharts buys, concretely</h2>
 *
 * It renders React elements rather than driving an imperative chart instance into a container div,
 * and that is what makes the interaction work rather than merely exist:
 *
 * - **Hover reaches the whole plot.** The shared cursor tracks the nearest category across every
 *   series, so a four-series area chart reads all four values at one pointer position. The Apex
 *   version needed `shared: true, intersect: false` to approximate this and still missed between
 *   points.
 * - **The tooltip is a component**, so it uses the dashboard's own type scale, spacing and status
 *   colours instead of a second styling system that had to be kept in step by hand.
 * - **Legends toggle series.** Clicking a legend entry hides that series and rescales the axis,
 *   which is the one interaction people actually reach for on a multi-series chart.
 * - **Resize is native.** `ResponsiveContainer` observes the element; the sidebar collapsing no
 *   longer leaves a chart at its old width until something else forces a reflow.
 *
 * <h2>Colours are still literals</h2>
 *
 * Same reason as before: SVG attributes cannot resolve the CSS custom properties the rest of the
 * dashboard is themed with. `palette.ts` restates them and says so.
 */

export interface Series {
  name: string;
  data: number[];
  color?: string;
}

interface CategorySeriesProps {
  categories: string[];
  series: Series[];
  height?: number;
  /** Whole numbers only — counts of vehicles, trips and defects are never fractional. */
  integerAxis?: boolean;
  stacked?: boolean;
  horizontal?: boolean;
  showLegend?: boolean;
}

const defaultColor = (index: number): string => seriesColors[index % seriesColors.length];

/** Recharts wants one object per category; the callers hold parallel arrays, so pivot here. */
const toRows = (categories: string[], series: Series[]): Record<string, string | number>[] =>
  categories.map((category, index) => {
    const row: Record<string, string | number> = { category };
    series.forEach((entry) => {
      row[entry.name] = entry.data[index] ?? 0;
    });
    return row;
  });

const axisTick = { fill: chartColors.text, fontSize: 11 };

const integerFormatter = (value: number) => String(Math.round(value));

interface TooltipEntry {
  name?: string | number;
  value?: string | number | (string | number)[];
  color?: string;
}

/**
 * The shared tooltip.
 *
 * Built rather than configured so it carries the dashboard's own type scale and card treatment, and
 * so a zero is shown rather than dropped — on an operations chart "0 defects" is a reading, and a
 * missing row reads as missing data.
 */
const ChartTooltip = ({
  active,
  payload,
  label,
}: {
  active?: boolean;
  payload?: TooltipEntry[];
  label?: string | number;
}): ReactNode => {
  if (!active || !payload?.length) {
    return null;
  }
  return (
    <div className="rounded-lg border border-gray-200 bg-white px-3 py-2 shadow-theme-lg">
      <p className="mb-1 text-theme-xs font-semibold text-gray-900">{label}</p>
      <ul className="space-y-0.5">
        {payload.map((entry) => (
          <li key={String(entry.name)} className="flex items-center gap-2 text-theme-xs">
            <span
              aria-hidden="true"
              className="h-2 w-2 shrink-0 rounded-full"
              style={{ backgroundColor: entry.color }}
            />
            <span className="text-gray-600">{entry.name}</span>
            <span className="ml-auto font-semibold text-gray-900 tabular-nums">
              {String(entry.value)}
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
};

/**
 * Series visibility, driven by the legend.
 *
 * Kept per chart instance rather than lifted, because hiding a series is a reading aid for the
 * person looking at it — not state any other part of the screen should react to.
 */
const useHiddenSeries = () => {
  const [hidden, setHidden] = useState<string[]>([]);
  const toggle = (name: string) =>
    setHidden((current) =>
      current.includes(name) ? current.filter((entry) => entry !== name) : [...current, name],
    );
  return { hidden, toggle };
};

const legendProps = (hidden: string[], toggle: (name: string) => void) => ({
  verticalAlign: 'top' as const,
  align: 'right' as const,
  height: 32,
  iconType: 'circle' as const,
  iconSize: 8,
  onClick: (entry: { value?: string }) => entry.value && toggle(entry.value),
  formatter: (value: string) => (
    <span
      className="cursor-pointer text-theme-xs"
      style={{ color: hidden.includes(value) ? chartColors.grey : chartColors.text }}
    >
      {value}
    </span>
  ),
});

/** Trend over time. Used where the measure is continuous and the shape matters more than the value. */
export const AreaChart = ({
  categories,
  series,
  height = 260,
  integerAxis = true,
  showLegend = true,
}: CategorySeriesProps) => {
  const { hidden, toggle } = useHiddenSeries();
  const rows = toRows(categories, series);

  return (
    <ResponsiveContainer width="100%" height={height}>
      <RechartsArea data={rows} margin={{ top: 8, right: 12, left: 0, bottom: 0 }}>
        <defs>
          {series.map((entry, index) => {
            const colour = entry.color ?? defaultColor(index);
            return (
              <linearGradient key={entry.name} id={`fill-${entry.name}`} x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor={colour} stopOpacity={0.28} />
                <stop offset="95%" stopColor={colour} stopOpacity={0.02} />
              </linearGradient>
            );
          })}
        </defs>
        <CartesianGrid stroke={chartColors.greyLine} strokeDasharray="4 4" vertical={false} />
        <XAxis dataKey="category" tick={axisTick} tickLine={false} axisLine={false} />
        <YAxis
          tick={axisTick}
          tickLine={false}
          axisLine={false}
          width={40}
          allowDecimals={!integerAxis}
          tickFormatter={integerAxis ? integerFormatter : undefined}
        />
        {/* A faint vertical rule under the pointer, so the reading is anchored to a category. */}
        <Tooltip content={<ChartTooltip />} cursor={{ stroke: chartColors.grey, strokeWidth: 1 }} />
        {showLegend && <Legend {...legendProps(hidden, toggle)} />}
        {series.map((entry, index) => (
          <Area
            key={entry.name}
            type="monotone"
            dataKey={entry.name}
            hide={hidden.includes(entry.name)}
            stroke={entry.color ?? defaultColor(index)}
            strokeWidth={2}
            fill={`url(#fill-${entry.name})`}
            // Dots only on hover: a 30-point series with a marker per point is noise, but the
            // hovered point must be identifiable.
            dot={false}
            activeDot={{ r: 4, strokeWidth: 2, stroke: '#fff' }}
          />
        ))}
      </RechartsArea>
    </ResponsiveContainer>
  );
};

/** Comparison across categories. */
export const BarChart = ({
  categories,
  series,
  height = 260,
  integerAxis = true,
  stacked = false,
  horizontal = false,
  showLegend = true,
}: CategorySeriesProps) => {
  const { hidden, toggle } = useHiddenSeries();
  const rows = toRows(categories, series);

  return (
    <ResponsiveContainer width="100%" height={height}>
      <RechartsBar
        data={rows}
        layout={horizontal ? 'vertical' : 'horizontal'}
        margin={{ top: 8, right: 12, left: 0, bottom: 0 }}
        barCategoryGap={series.length > 1 ? '20%' : '38%'}
      >
        <CartesianGrid
          stroke={chartColors.greyLine}
          strokeDasharray="4 4"
          // The grid lines belong on the value axis, and which axis that is swaps with the layout.
          vertical={horizontal}
          horizontal={!horizontal}
        />
        {horizontal ? (
          <>
            <XAxis
              type="number"
              tick={axisTick}
              tickLine={false}
              axisLine={false}
              allowDecimals={!integerAxis}
              tickFormatter={integerAxis ? integerFormatter : undefined}
            />
            <YAxis
              type="category"
              dataKey="category"
              tick={axisTick}
              tickLine={false}
              axisLine={false}
              width={120}
            />
          </>
        ) : (
          <>
            <XAxis dataKey="category" tick={axisTick} tickLine={false} axisLine={false} />
            <YAxis
              tick={axisTick}
              tickLine={false}
              axisLine={false}
              width={40}
              allowDecimals={!integerAxis}
              tickFormatter={integerAxis ? integerFormatter : undefined}
            />
          </>
        )}
        <Tooltip content={<ChartTooltip />} cursor={{ fill: chartColors.greyLine }} />
        {showLegend && <Legend {...legendProps(hidden, toggle)} />}
        {series.map((entry, index) => (
          <Bar
            key={entry.name}
            dataKey={entry.name}
            hide={hidden.includes(entry.name)}
            stackId={stacked ? 'stack' : undefined}
            fill={entry.color ?? defaultColor(index)}
            radius={horizontal ? [0, 4, 4, 0] : [4, 4, 0, 0]}
            maxBarSize={48}
          />
        ))}
      </RechartsBar>
    </ResponsiveContainer>
  );
};

interface DonutChartProps {
  labels: string[];
  values: number[];
  colors: string[];
  height?: number;
  /** Shown in the middle of the ring — usually the total the slices add up to. */
  centreLabel?: string;
}

/** Composition of a whole — readiness mix, workflow status mix. */
export const DonutChart = ({
  labels,
  values,
  colors,
  height = 260,
  centreLabel = 'Total',
}: DonutChartProps) => {
  const total = values.reduce((sum, value) => sum + value, 0);
  const rows = labels.map((label, index) => ({ name: label, value: values[index] ?? 0 }));

  /*
    Numeric radii rather than percentages, because `activeShape` takes resolved sector props and a
    percentage string is not one — the hover growth has to be expressed in the same units as the
    resting size. Derived from the height, less the room the legend takes at the bottom.
  */
  const outerRadius = Math.max(48, Math.round((height - 56) / 2));
  const innerRadius = Math.round(outerRadius * 0.62);

  return (
    <div className="relative" style={{ height }}>
      <ResponsiveContainer width="100%" height="100%">
        <PieChart>
          <Pie
            data={rows}
            dataKey="value"
            nameKey="name"
            innerRadius={innerRadius}
            outerRadius={outerRadius}
            paddingAngle={1}
            stroke="none"
            // Enlarging the hovered slice is the whole interaction here: a donut has no axis to
            // anchor a cursor to, so the slice itself has to acknowledge the pointer.
            activeShape={{ outerRadius: outerRadius + 6 }}
          >
            {rows.map((row, index) => (
              <Cell key={row.name} fill={colors[index] ?? defaultColor(index)} />
            ))}
          </Pie>
          <Tooltip content={<ChartTooltip />} />
          <Legend
            verticalAlign="bottom"
            align="center"
            iconType="circle"
            iconSize={8}
            formatter={(value: string) => (
              <span className="text-theme-xs" style={{ color: chartColors.text }}>
                {value}
              </span>
            )}
          />
        </PieChart>
      </ResponsiveContainer>

      {/*
        The centre total is absolutely positioned rather than drawn into the SVG, so it inherits the
        dashboard's font stack and tabular figures. `pointer-events-none` keeps it out of the way of
        the slice hover underneath it — without that, the middle of the chart swallows the pointer.
      */}
      <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center pb-8">
        <span className="text-theme-xs" style={{ color: chartColors.text }}>
          {centreLabel}
        </span>
        <span
          className="text-title-sm font-bold tabular-nums"
          style={{ color: chartColors.navy }}
        >
          {total}
        </span>
      </div>
    </div>
  );
};

interface SparklineProps {
  values: number[];
  colour?: string;
  height?: number;
}

/** A bare trend line for a KPI card. No axes, no tooltip — shape only. */
export const Sparkline = ({ values, colour = chartColors.navy, height = 42 }: SparklineProps) => (
  <ResponsiveContainer width="100%" height={height}>
    <RechartsArea
      data={values.map((value, index) => ({ index, value }))}
      margin={{ top: 2, right: 0, left: 0, bottom: 0 }}
    >
      <defs>
        <linearGradient id={`spark-${colour.replace('#', '')}`} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor={colour} stopOpacity={0.3} />
          <stop offset="100%" stopColor={colour} stopOpacity={0} />
        </linearGradient>
      </defs>
      <Area
        type="monotone"
        dataKey="value"
        stroke={colour}
        strokeWidth={2}
        fill={`url(#spark-${colour.replace('#', '')})`}
        dot={false}
        isAnimationActive={false}
      />
    </RechartsArea>
  </ResponsiveContainer>
);
