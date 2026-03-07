<script setup lang="ts">
import { computed } from 'vue'
import VueApexCharts from 'vue3-apexcharts'
import type { ChartSpec } from '../types'

const props = defineProps<{
  spec: ChartSpec
}>()

const chartOptions = computed(() => {
  const chartBase = {
    toolbar: { show: false },
    fontFamily: 'inherit',
    background: 'transparent',
  }

  const base = {
    chart: chartBase,
    theme: { mode: 'dark', palette: 'palette2' },
    colors: ['#4a6edb', '#7cb342', '#f9a825', '#e64a19', '#7b1fa2', '#00838f'],
    dataLabels: { enabled: false },
    stroke: { width: 1 },
    legend: {
      position: 'bottom' as const,
      labels: { colors: '#8888a0' },
    },
    title: {
      text: props.spec.title,
      align: 'left',
      style: { color: '#e8e8f0', fontSize: '14px' },
    },
  }

  if (props.spec.type === 'stackedBar' || props.spec.type === 'bar') {
    return {
      ...base,
      chart: {
        ...chartBase,
        type: 'bar',
        stacked: props.spec.type === 'stackedBar',
      },
      xaxis: {
        categories: props.spec.categories,
        labels: { style: { colors: '#8888a0' } },
      },
      yaxis: { labels: { style: { colors: '#8888a0' } } },
      plotOptions: {
        bar: { horizontal: false, columnWidth: '60%' },
      },
    }
  }

  if (props.spec.type === 'pie') {
    return {
      ...base,
      chart: { ...chartBase, type: 'pie' },
      labels: props.spec.categories,
    }
  }

  return base
})

const chartSeries = computed(() => {
  if (props.spec.type === 'pie') {
    const s = props.spec.series[0]
    return s ? s.data : []
  }
  return props.spec.series.map((s) => ({ name: s.name, data: s.data }))
})

const chartType = computed(() =>
  props.spec.type === 'pie' ? 'pie' : 'bar'
)
</script>

<template>
  <div class="chart-bubble">
    <VueApexCharts
      :type="chartType"
      height="240"
      :options="chartOptions as object"
      :series="chartSeries"
    />
  </div>
</template>

<style scoped>
.chart-bubble {
  min-width: 260px;
  max-width: 100%;
  margin-top: 0.5rem;
}
</style>
