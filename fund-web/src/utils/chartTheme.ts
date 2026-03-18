import * as echarts from 'echarts/core';

const fundTheme = {
  color: ['#1677FF', '#36CFC9', '#FAAD14', '#F5222D', '#722ED1', '#13C2C2', '#EB2F96', '#FA8C16'],
  backgroundColor: 'transparent',
  textStyle: { fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif' },
  title: { textStyle: { color: '#1F2937' }, subtextStyle: { color: '#8C8C8C' } },
  line: {
    itemStyle: { borderWidth: 2 },
    lineStyle: { width: 2 },
    symbolSize: 6,
    smooth: true,
  },
  bar: {
    itemStyle: { barBorderRadius: [4, 4, 0, 0] },
  },
  pie: {
    itemStyle: { borderColor: '#fff', borderWidth: 2 },
  },
  categoryAxis: {
    axisLine: { show: true, lineStyle: { color: '#f0f0f0' } },
    axisTick: { show: false },
    axisLabel: { color: '#8C8C8C', fontSize: 11 },
    splitLine: { show: false },
  },
  valueAxis: {
    axisLine: { show: false },
    axisTick: { show: false },
    axisLabel: { color: '#8C8C8C', fontSize: 11 },
    splitLine: { lineStyle: { color: '#f0f0f0', type: 'dashed' as const } },
  },
  tooltip: {
    backgroundColor: '#fff',
    borderColor: '#e8e8e8',
    borderWidth: 1,
    textStyle: { color: '#1F2937', fontSize: 13 },
    extraCssText: 'box-shadow: 0 2px 8px rgba(0,0,0,0.1); border-radius: 8px;',
  },
  legend: {
    textStyle: { color: '#8C8C8C', fontSize: 12 },
  },
};

export function registerFundTheme() {
  echarts.registerTheme('fundTheme', fundTheme);
}
