import { BarChart } from 'shared/charts/Charts';
import { toneColors } from 'shared/charts/palette';

export interface DrillBar {
  label: string;
  /** Reached but did not acknowledge — the part of the audience the drill failed to close. */
  reachedOnly: number;
  acknowledged: number;
  /** Never reached at all: target minus reached. */
  missed: number;
}

interface DrillPerformanceChartProps {
  bars: DrillBar[];
  height?: number;
}

/**
 * What each completed drill actually achieved, against the target it set.
 *
 * Three stacked series that add up to the target, so a bar's full height is the number of people
 * the drill meant to reach and the green segment is the only part that worked end to end. Stacking
 * rather than grouping is deliberate: acknowledged, reached-but-silent and never-reached are three
 * mutually exclusive fates of the same population, and reading them as a proportion of one bar is
 * the question — "how much of the site did we actually close?" — that a grouped chart makes an
 * operator answer with arithmetic.
 *
 * Never-reached carries the blocked tone because it is a delivery failure; reached-but-silent
 * carries caution because it may simply be somebody who was busy.
 */
const DrillPerformanceChart = ({ bars, height = 300 }: DrillPerformanceChartProps) => (
  <BarChart
    height={height}
    stacked
    integerAxis
    categories={bars.map((bar) => bar.label)}
    series={[
      {
        name: 'Acknowledged',
        data: bars.map((bar) => bar.acknowledged),
        color: toneColors.ready,
      },
      {
        name: 'Reached, did not acknowledge',
        data: bars.map((bar) => bar.reachedOnly),
        color: toneColors.caution,
      },
      {
        name: 'Never reached',
        data: bars.map((bar) => bar.missed),
        color: toneColors.blocked,
      },
    ]}
  />
);

export default DrillPerformanceChart;
