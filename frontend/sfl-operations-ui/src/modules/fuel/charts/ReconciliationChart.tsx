import { DonutChart } from 'shared/charts/Charts';
import { toneColors } from 'shared/charts/palette';

export interface ReconciliationSlice {
  name: string;
  value: number;
  tone: 'ready' | 'caution' | 'blocked' | 'neutral';
}

interface ReconciliationChartProps {
  slices: ReconciliationSlice[];
  height?: number;
}

/**
 * How the site's transactions divide between reconciled, in exception and not yet run.
 *
 * The first two slices come straight from the dashboard snapshot (`reconciledCount`,
 * `exceptionCount`); the third is the remainder of `transactionCount`, which is arithmetic on the
 * service's own figures rather than a count the console made up. Tone-coded rather than
 * categorical, because these are statuses and they mean here what they mean on a `StatusChip`.
 */
const ReconciliationChart = ({ slices, height = 280 }: ReconciliationChartProps) => (
  <DonutChart
    height={height}
    centreLabel="Transactions"
    labels={slices.map((slice) => slice.name)}
    values={slices.map((slice) => slice.value)}
    colors={slices.map((slice) => toneColors[slice.tone])}
  />
);

export default ReconciliationChart;
