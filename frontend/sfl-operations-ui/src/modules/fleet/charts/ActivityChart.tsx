import { AreaChart } from 'shared/charts/Charts';
import { seriesColors } from 'shared/charts/palette';

export interface ActivityPoint {
  label: string;
  trips: number;
  workflow: number;
}

interface ActivityChartProps {
  points: ActivityPoint[];
  height?: number;
}

/**
 * Movements planned and exceptions raised, by day.
 *
 * Both series are bucketed client-side from records the service actually returned — there is no
 * time-series endpoint — so the window is bounded by what the register queries fetch.
 *
 * Neither series carries a status, so the colours are the categorical sequence rather than tones:
 * Deep Teal then CLET Gold, which differ in lightness as well as hue and so stay separable in
 * greyscale and to a viewer with deuteranopia. The legend names both, so colour is never the only
 * cue (SC 1.4.1).
 */
const ActivityChart = ({ points, height = 280 }: ActivityChartProps) => (
  <AreaChart
    height={height}
    categories={points.map((point) => point.label)}
    series={[
      {
        name: 'Trips planned',
        data: points.map((point) => point.trips),
        color: seriesColors[0],
      },
      {
        name: 'Exceptions raised',
        data: points.map((point) => point.workflow),
        color: seriesColors[1],
      },
    ]}
  />
);

export default ActivityChart;
