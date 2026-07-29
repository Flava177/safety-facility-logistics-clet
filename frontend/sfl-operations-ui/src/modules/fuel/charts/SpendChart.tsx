import { AreaChart } from 'shared/charts/Charts';
import { chartColors } from 'shared/charts/palette';

export interface SpendPoint {
  label: string;
  spend: number;
  volume: number;
}

interface SpendChartProps {
  points: SpendPoint[];
  currencyCode: string;
  unit: string;
  height?: number;
}

/**
 * Fuel spend and volume by day.
 *
 * Two series on one axis on purpose: spend and volume move together when nothing is wrong, and the
 * day they diverge — the same litres costing noticeably more, or the same money buying less — is
 * exactly what an operator is looking for. Sharing an axis is what makes that divergence visible;
 * two separate panels would hide it.
 *
 * Both series are bucketed from the transactions the register returned, not from a service
 * time-series endpoint — there isn't one. The panel that hosts this says so.
 */
const SpendChart = ({ points, currencyCode, unit, height = 280 }: SpendChartProps) => (
  <AreaChart
    height={height}
    integerAxis={false}
    categories={points.map((point) => point.label)}
    series={[
      {
        name: currencyCode ? `Spend (${currencyCode})` : 'Spend',
        data: points.map((point) => point.spend),
        color: chartColors.teal,
      },
      {
        name: unit ? `Volume (${unit})` : 'Volume',
        data: points.map((point) => point.volume),
        color: chartColors.gold,
      },
    ]}
  />
);

export default SpendChart;
