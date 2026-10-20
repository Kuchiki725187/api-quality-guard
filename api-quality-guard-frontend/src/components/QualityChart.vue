<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  option: { type: Object, required: true },
  height: { type: String, default: '320px' },
})

const el = ref(null)
let chart = null
let observer = null

function render() {
  if (!chart) {
    chart = echarts.init(el.value)
    observer = new ResizeObserver(() => chart && chart.resize())
    observer.observe(el.value)
  }
  chart.setOption(props.option, true)
}

onMounted(render)
watch(() => props.option, render, { deep: true })

onBeforeUnmount(() => {
  observer && observer.disconnect()
  chart && chart.dispose()
  chart = null
})
</script>

<template>
  <div ref="el" :style="{ height, width: '100%' }"></div>
</template>
