import { useMemo } from 'react';
import { BarChart } from 'echarts/charts';
import { GridComponent, TooltipComponent } from 'echarts/components';
import * as echarts from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import { grey, red, sflGold } from 'theme/palette/colors';
import ReactEchart from 'components/base/ReactEchart';

echarts.use([BarChart, GridComponent, TooltipComponent, CanvasRenderer]);

export interface ExceptionBar {
  label: string;
  value: number;
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
 */
const ExceptionsChart = ({ bars, height = 250 }: ExceptionsChartProps) => {
  const option = useMemo(
    () => ({
      grid: { left: 4, right: 24, top: 8, bottom: 4, containLabel: true },
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      xAxis: {
        type: 'value',
        minInterval: 1,
        axisLabel: { color: grey[600], fontSize: 11 },
        splitLine: { lineStyle: { color: grey[200] } },
      },
      yAxis: {
        type: 'category',
        data: bars.map((bar) => bar.label),
        axisLabel: { color: grey[600], fontSize: 11 },
        axisLine: { show: false },
        axisTick: { show: false },
      },
      series: [
        {
          type: 'bar',
          barWidth: 14,
          itemStyle: { borderRadius: [0, 4, 4, 0] },
          data: bars.map((bar) => ({
            value: bar.value,
            itemStyle: { color: bar.critical ? red[500] : sflGold[500] },
          })),
        },
      ],
    }),
    [bars],
  );

  return <ReactEchart echarts={echarts} option={option} sx={{ height, width: 1 }} />;
};

export default ExceptionsChart;
