import type { ApexOptions } from 'apexcharts';
import BaseChart from './BaseChart';
import { chartColors, seriesColors } from './palette';

export interface Series {
  name: string;
  data: number[];
  color?: string;
}

const gridDefaults: ApexOptions['grid'] = {
  borderColor: chartColors.greyLine,
  strokeDashArray: 4,
  xaxis: { lines: { show: false } },
  yaxis: { lines: { show: true } },
  padding: { left: 4, right: 8, top: 0, bottom: 0 },
};

const axisDefaults = {
  labels: { style: { colors: chartColors.text, fontSize: '11px' } },
  axisBorder: { show: false },
  axisTicks: { show: false },
};

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

/** Trend over time. Used where the measure is continuous and the shape matters more than the value. */
export const AreaChart = ({
  categories,
  series,
  height = 260,
  integerAxis = true,
  showLegend = true,
}: CategorySeriesProps) => (
  <BaseChart
    type="area"
    height={height}
    series={series}
    options={{
      colors: series.map((entry, index) => entry.color ?? defaultColor(index)),
      dataLabels: { enabled: false },
      stroke: { curve: 'smooth', width: 2 },
      fill: {
        type: 'gradient',
        gradient: { shadeIntensity: 1, opacityFrom: 0.28, opacityTo: 0.02, stops: [0, 95] },
      },
      grid: gridDefaults,
      legend: legendOptions(showLegend),
      xaxis: { categories, ...axisDefaults, tooltip: { enabled: false } },
      yaxis: {
        ...axisDefaults,
        labels: {
          ...axisDefaults.labels,
          formatter: (value: number) => (integerAxis ? String(Math.round(value)) : String(value)),
        },
      },
      tooltip: { shared: true, intersect: false },
    }}
  />
);

/** Comparison across categories. */
export const BarChart = ({
  categories,
  series,
  height = 260,
  integerAxis = true,
  stacked = false,
  horizontal = false,
  showLegend = true,
}: CategorySeriesProps) => (
  <BaseChart
    type="bar"
    height={height}
    series={series}
    options={{
      chart: { stacked },
      colors: series.map((entry, index) => entry.color ?? defaultColor(index)),
      plotOptions: {
        bar: {
          horizontal,
          borderRadius: 4,
          borderRadiusApplication: 'end',
          columnWidth: series.length > 1 ? '62%' : '42%',
          barHeight: '62%',
        },
      },
      dataLabels: { enabled: false },
      grid: gridDefaults,
      legend: legendOptions(showLegend),
      // On a horizontal bar chart the axes swap roles: the value axis becomes x and the category
      // axis becomes y. Rounding must follow the values, or it is handed a category label and
      // renders every tick as NaN.
      xaxis: horizontal
        ? {
            categories,
            ...axisDefaults,
            tooltip: { enabled: false },
            labels: {
              ...axisDefaults.labels,
              formatter: (value: string) =>
                integerAxis ? String(Math.round(Number(value))) : String(value),
            },
          }
        : { categories, ...axisDefaults, tooltip: { enabled: false } },
      yaxis: horizontal
        ? axisDefaults
        : {
            ...axisDefaults,
            labels: {
              ...axisDefaults.labels,
              formatter: (value: number) =>
                integerAxis ? String(Math.round(value)) : String(value),
            },
          },
    }}
  />
);

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
  // Computed here rather than in Apex's `total.formatter`, which is handed the whole chart globals
  // object; reading the numbers we already have is both simpler and impossible to get wrong.
  const total = values.reduce((sum, value) => sum + value, 0);

  return (
  <BaseChart
    type="donut"
    height={height}
    series={values}
    options={{
      labels,
      colors,
      stroke: { width: 0 },
      dataLabels: { enabled: false },
      legend: {
        position: 'bottom',
        horizontalAlign: 'center',
        fontSize: '12px',
        markers: { size: 6 },
        itemMargin: { horizontal: 8, vertical: 3 },
      },
      plotOptions: {
        pie: {
          donut: {
            size: '68%',
            labels: {
              show: true,
              name: { fontSize: '12px', color: chartColors.text },
              value: {
                fontSize: '22px',
                fontWeight: 700,
                color: chartColors.navy,
                formatter: (value: string) => value,
              },
              total: {
                show: true,
                label: centreLabel,
                color: chartColors.text,
                fontSize: '11px',
                formatter: () => String(total),
              },
            },
          },
        },
      },
      tooltip: { y: { formatter: (value: number) => String(value) } },
    }}
  />
  );
};

interface SparklineProps {
  values: number[];
  colour?: string;
  height?: number;
}

/** A bare trend line for a KPI card. No axes, no tooltip — shape only. */
export const Sparkline = ({ values, colour = chartColors.navy, height = 42 }: SparklineProps) => (
  <BaseChart
    type="area"
    height={height}
    series={[{ name: 'value', data: values }]}
    options={{
      chart: { sparkline: { enabled: true } },
      colors: [colour],
      stroke: { curve: 'smooth', width: 2 },
      fill: {
        type: 'gradient',
        gradient: { shadeIntensity: 1, opacityFrom: 0.3, opacityTo: 0, stops: [0, 100] },
      },
      tooltip: { enabled: false },
    }}
  />
);

function defaultColor(index: number): string {
  return seriesColors[index % seriesColors.length];
}

function legendOptions(show: boolean): ApexOptions['legend'] {
  return {
    show,
    position: 'top',
    horizontalAlign: 'right',
    fontSize: '12px',
    markers: { size: 6 },
    itemMargin: { horizontal: 8 },
  };
}
