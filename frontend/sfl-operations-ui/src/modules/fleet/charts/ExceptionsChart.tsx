import { BarChart } from 'shared/charts/Charts';
import { toneColors } from 'shared/charts/palette';

export interface ExceptionBar {
  label: string;
  value: number;
  /** Stops work rather than merely warranting a look — drawn in the blocked tone. */
  critical?: boolean;
}

interface ExceptionsChartProps {
  bars: ExceptionBar[];
  height?: number;
}

/**
 * Open exceptions by kind.
 *
 * Horizontal bars because the category labels are long ("Expired compliance", "Assignment
 * conflicts") and an operator scans them by name, not by position.
 *
 * Severity is carried by two stacked series rather than per-bar colours, because the kit's bar chart
 * colours a series and not a point; every category contributes to exactly one of the two, so the bar
 * lengths still read as one value each. `integerAxis` is off because on a horizontal chart the kit
 * applies that formatter to the axis holding the category labels, which are text.
 */
const ExceptionsChart = ({ bars, height = 270 }: ExceptionsChartProps) => (
  <BarChart
    height={height}
    horizontal
    stacked
    integerAxis={false}
    categories={bars.map((bar) => bar.label)}
    series={[
      {
        name: 'Blocking',
        data: bars.map((bar) => (bar.critical ? bar.value : 0)),
        color: toneColors.blocked,
      },
      {
        name: 'Needs attention',
        data: bars.map((bar) => (bar.critical ? 0 : bar.value)),
        color: toneColors.caution,
      },
    ]}
  />
);

export default ExceptionsChart;
