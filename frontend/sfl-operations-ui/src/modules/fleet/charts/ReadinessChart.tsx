import { DonutChart } from 'shared/charts/Charts';
import { toneColors } from 'shared/charts/palette';

/** Ready / caution / blocked read the same here as they do on a `StatusChip`. */
export type ReadinessTone = 'ready' | 'caution' | 'blocked';

export interface ReadinessSlice {
  name: string;
  value: number;
  tone: ReadinessTone;
}

interface ReadinessChartProps {
  slices: ReadinessSlice[];
  /**
   * Names what the ring adds up to.
   *
   * The figure in the middle is the sum of the slices — the kit's donut derives it rather than
   * accepting one, which keeps the centre from disagreeing with the arcs around it.
   */
  centreLabel: string;
  height?: number;
}

/**
 * Fleet availability split as a donut.
 *
 * Composition, not trend: an operator reads how the scope divides between usable, committed and
 * blocked vehicles in one glance, and the actionable count sits on the indicator card above.
 *
 * These three slices are statuses rather than plain categories, so they take the tone palette
 * instead of the categorical sequence — green, amber and red here mean exactly what they mean on a
 * `StatusChip`. Three is also the ceiling: a ring that needs a fourth hue wants a table.
 */
const ReadinessChart = ({ slices, centreLabel, height = 280 }: ReadinessChartProps) => (
  <DonutChart
    height={height}
    centreLabel={centreLabel}
    labels={slices.map((slice) => slice.name)}
    values={slices.map((slice) => slice.value)}
    colors={slices.map((slice) => toneColors[slice.tone])}
  />
);

export default ReadinessChart;
