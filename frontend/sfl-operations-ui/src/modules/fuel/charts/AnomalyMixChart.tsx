import { BarChart } from 'shared/charts/Charts';
import { toneColors } from 'shared/charts/palette';

export interface AnomalyBar {
  label: string;
  value: number;
  /** Past its SLA, or material — the two things that make a case jump the queue. */
  urgent?: boolean;
}

interface AnomalyMixChartProps {
  bars: AnomalyBar[];
  height?: number;
}

/**
 * Open anomaly cases by type.
 *
 * Horizontal, because the seventeen anomaly types have long names ("Abnormal consumption",
 * "Odometer regression") that an operator reads rather than positions. Two stacked series carry
 * urgency for the same reason the fleet exceptions chart does: the kit colours a series, not a
 * point, and each category contributes to exactly one of the two so the bars still read as one
 * value each.
 */
const AnomalyMixChart = ({ bars, height = 300 }: AnomalyMixChartProps) => (
  <BarChart
    height={height}
    horizontal
    stacked
    integerAxis={false}
    categories={bars.map((bar) => bar.label)}
    series={[
      {
        name: 'Breaching SLA or material',
        data: bars.map((bar) => (bar.urgent ? bar.value : 0)),
        color: toneColors.blocked,
      },
      {
        name: 'Within SLA',
        data: bars.map((bar) => (bar.urgent ? 0 : bar.value)),
        color: toneColors.caution,
      },
    ]}
  />
);

export default AnomalyMixChart;
