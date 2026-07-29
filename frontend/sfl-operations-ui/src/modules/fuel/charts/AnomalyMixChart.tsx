import { BarChart } from 'shared/charts/Charts';
import { toneColors } from 'shared/charts/palette';

export interface AnomalyBar {
  label: string;
  value: number;
}

interface AnomalyMixChartProps {
  bars: AnomalyBar[];
  height?: number;
}

/**
 * Open anomaly cases by type.
 *
 * Horizontal, because the seventeen anomaly types have long names ("Abnormal consumption",
 * "Odometer regression") that an operator reads rather than positions.
 *
 * One series, not two. This used to stack "breaching SLA or material" against "within SLA", split in
 * the browser from the page of cases the dashboard had loaded. The counts come from
 * `/fuel/dashboard/anomaly-counts` now, which counts every open case at the site and carries no
 * urgency — so the split had to go with it. Keeping the two-colour legend over a single-valued series
 * would have been worse than losing it: every bar would have rendered as "within SLA", which is a
 * claim about nineteen cases that the indicator beside it contradicts.
 *
 * Urgency lives where it can be stated exactly: the SLA and material counters above this chart, and
 * the per-case SLA in the queue next to it.
 */
const AnomalyMixChart = ({ bars, height = 300 }: AnomalyMixChartProps) => (
  <BarChart
    height={height}
    horizontal
    integerAxis={false}
    categories={bars.map((bar) => bar.label)}
    series={[
      {
        name: 'Open cases',
        data: bars.map((bar) => bar.value),
        color: toneColors.caution,
      },
    ]}
  />
);

export default AnomalyMixChart;
