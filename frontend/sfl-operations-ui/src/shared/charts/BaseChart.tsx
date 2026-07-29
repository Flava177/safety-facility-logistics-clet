import type { ApexOptions } from 'apexcharts';
import ReactApexChart from 'react-apexcharts';
import { chartFont } from './palette';

type ApexSeries = ApexOptions['series'];

interface BaseChartProps {
  type: 'area' | 'line' | 'bar' | 'donut' | 'radialBar';
  options: ApexOptions;
  series: ApexSeries;
  height?: number;
}

/** Shared chart defaults so every chart in the dashboard has the same typography and chrome. */
const BaseChart = ({ type, options, series, height = 260 }: BaseChartProps) => {
  const merged: ApexOptions = {
    ...options,
    chart: {
      fontFamily: chartFont,
      toolbar: { show: false },
      zoom: { enabled: false },
      animations: { enabled: false },
      ...options.chart,
      type,
      height,
    },
  };

  return <ReactApexChart type={type} options={merged} series={series} height={height} />;
};

export default BaseChart;
