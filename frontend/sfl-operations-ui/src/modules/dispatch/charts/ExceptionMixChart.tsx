import { BarChart } from 'shared/charts/Charts';
import { toneColors } from 'shared/charts/palette';

export interface ExceptionBar {
  label: string;
  value: number;
  /** Security relevant, or past its SLA — the two things that make a case jump the queue. */
  urgent?: boolean;
}

interface ExceptionMixChartProps {
  bars: ExceptionBar[];
  height?: number;
}

/**
 * Open dispatch exception cases by type.
 *
 * Horizontal, because the six types have long names ("Receipt variance", "Return discrepancy") that
 * an operator reads rather than positions. Two stacked series carry urgency for the same reason the
 * fleet and fuel charts do: the kit colours a series, not a point, and each category contributes to
 * exactly one of the two so the bars still read as one value each.
 */
const ExceptionMixChart = ({ bars, height = 280 }: ExceptionMixChartProps) => (
  <BarChart
    height={height}
    horizontal
    stacked
    integerAxis={false}
    categories={bars.map((bar) => bar.label)}
    series={[
      {
        name: 'Security relevant or breaching SLA',
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

export default ExceptionMixChart;
