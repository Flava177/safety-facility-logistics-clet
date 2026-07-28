import { useMemo } from 'react';
import { PieChart } from 'echarts/charts';
import { LegendComponent, TooltipComponent } from 'echarts/components';
import * as echarts from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import { basic, green, grey, red, sflGold, sflNavy } from 'theme/palette/colors';
import ReactEchart from 'components/base/ReactEchart';

echarts.use([PieChart, TooltipComponent, LegendComponent, CanvasRenderer]);

/**
 * Chart colours are literal hex, not theme CSS variables.
 *
 * ECharts renders to canvas and cannot resolve `var(--…)`, so the brand scales are imported
 * directly. They are the same values the palette is built from, so the chart cannot drift from the
 * rest of the interface.
 */
const toneColour = {
  ready: green[500],
  caution: sflGold[500],
  blocked: red[500],
} as const;

export interface ReadinessSlice {
  name: string;
  value: number;
  tone: keyof typeof toneColour;
}

interface ReadinessChartProps {
  slices: ReadinessSlice[];
  centreLabel: string;
  centreValue: number | string;
  height?: number;
}

/**
 * Fleet availability split as a donut.
 *
 * The centre carries the number an operator acts on, so the chart answers its question without a
 * legend lookup.
 */
const ReadinessChart = ({
  slices,
  centreLabel,
  centreValue,
  height = 250,
}: ReadinessChartProps) => {
  const option = useMemo(
    () => ({
      tooltip: { trigger: 'item', formatter: '{b}: {c}' },
      legend: {
        bottom: 0,
        icon: 'circle',
        itemWidth: 8,
        itemHeight: 8,
        textStyle: { fontSize: 12, color: grey[600] },
      },
      series: [
        {
          type: 'pie',
          radius: ['62%', '84%'],
          center: ['50%', '44%'],
          avoidLabelOverlap: true,
          itemStyle: { borderWidth: 2, borderColor: basic.white },
          label: {
            show: true,
            position: 'center',
            formatter: () => `{value|${centreValue}}\n{label|${centreLabel}}`,
            rich: {
              value: { fontSize: 26, fontWeight: 700, color: sflNavy[800], lineHeight: 32 },
              label: { fontSize: 11, color: grey[600] },
            },
          },
          emphasis: { label: { show: true } },
          labelLine: { show: false },
          data: slices.map((slice) => ({
            name: slice.name,
            value: slice.value,
            itemStyle: { color: toneColour[slice.tone] },
          })),
        },
      ],
    }),
    [slices, centreLabel, centreValue],
  );

  return <ReactEchart echarts={echarts} option={option} sx={{ height, width: 1 }} />;
};

export default ReadinessChart;
