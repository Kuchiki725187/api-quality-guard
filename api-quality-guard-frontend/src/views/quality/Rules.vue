<script setup>
const runDimensions = [
  {
    name: '功能通过率',
    weight: '70%',
    formula: '断言全部通过的用例数 / 执行用例总数 × 100',
    note: 'ERROR 计入分母，但不计入通过数',
  },
  {
    name: '性能达标率',
    weight: '15%',
    formula: '含响应时间断言且全部通过的用例数 / 含响应时间断言的用例数 × 100',
    note: '没有响应时间断言时该维度不计入，权重按比例摊回',
  },
  {
    name: '稳定性',
    weight: '15%',
    formula: '(1 − ERROR 用例数 / 用例总数) × 100',
    note: '网络异常、超时等执行失败都计入 ERROR',
  },
]

const platformDimensions = [
  {
    name: '接口规范性',
    weight: '25%',
    formula: '有描述、响应定义含成功状态码、所有参数声明类型的接口数 / 接口总数 × 100',
    note: '当前接口定义的快照指标',
  },
  {
    name: '测试覆盖率',
    weight: '25%',
    formula: '至少有一条用例的接口数 / 接口总数 × 100',
    note: '当前用例与接口的快照指标',
  },
  {
    name: '性能表现',
    weight: '25%',
    formula: '近 14 天含响应时间断言的结果中，全部通过的比例',
    note: '没有响应时间断言的结果不计入该维度',
  },
  {
    name: '稳定性',
    weight: '25%',
    formula: '近 14 天 (1 − ERROR 结果数 / 总结果数) × 100',
    note: '统计窗口与性能维度一致',
  },
]

const grades = [
  { label: '优秀', range: '≥ 90', type: 'success' },
  { label: '良好', range: '80 ~ 89.9', type: 'primary' },
  { label: '及格', range: '60 ~ 79.9', type: 'warning' },
  { label: '不合格', range: '< 60', type: 'danger' },
]

const gateRules = [
  {
    rule: '质量总分达标',
    threshold: 'score ≥ 80',
    desc: '门禁检查的是单次运行评分 test_run.score，不是平台四维总分',
  },
  {
    rule: '执行异常零容忍',
    threshold: 'error ≤ 0',
    desc: '运行结果中 ERROR 数量必须为 0',
  },
  {
    rule: '失败用例上限',
    threshold: 'failed ≤ 0',
    desc: '默认不允许失败用例；计划或请求可放宽',
  },
]

const overrideSteps = [
  '本次门禁请求参数：scoreThreshold / maxFailedCount / maxErrorCount',
  '测试计划 execution_config.gate：{ scoreThreshold, maxFailedCount, maxErrorCount }',
  '系统默认值：80 / 0 / 0',
]
</script>

<template>
  <div class="page">
    <div class="panel">
      <div class="head">
        <div>
          <div class="page-title">
            质量规则说明
            <el-tag type="info" size="small">只读</el-tag>
          </div>
          <div class="page-sub">当前评分与门禁口径固定在代码中，本页展示实际生效的规则</div>
        </div>
      </div>
      <el-alert
        type="info"
        show-icon
        :closable="false"
        class="head-alert"
        title="规则暂不支持页面编辑；计划级可通过 execution_config.gate 覆盖门禁阈值，门禁检查请求也可临时覆盖。"
      />
    </div>

    <div class="panel">
      <h3 class="panel-title">
        <span>1. 单次运行评分</span>
        <span class="sub">test_run.score / score_detail</span>
      </h3>
      <el-table :data="runDimensions" class="dark-table">
        <el-table-column prop="name" label="维度" width="120" />
        <el-table-column label="默认权重" width="100">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.weight }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="formula" label="计算口径" min-width="360" />
        <el-table-column prop="note" label="说明" min-width="250" />
      </el-table>

      <div class="formula-block">
        <div>总分 = Σ(维度得分 × 权重)，保留 1 位小数</div>
        <div>无响应时间断言时：功能权重 = 0.70 / 0.85 ≈ 82.35%，稳定性权重 = 0.15 / 0.85 ≈ 17.65%，性能维度不计入</div>
      </div>

      <div class="grade-row">
        <span class="grade-label">等级：</span>
        <el-tag
          v-for="grade in grades"
          :key="grade.label"
          :type="grade.type"
          effect="dark"
          size="small"
        >
          {{ grade.label }} {{ grade.range }}
        </el-tag>
      </div>
    </div>

    <div class="panel">
      <h3 class="panel-title">
        <span>2. 平台四维评分</span>
        <span class="sub">工作台 / 质量评分页使用</span>
      </h3>
      <el-table :data="platformDimensions" class="dark-table">
        <el-table-column prop="name" label="维度" width="120" />
        <el-table-column label="默认权重" width="100">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.weight }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="formula" label="计算口径" min-width="380" />
        <el-table-column prop="note" label="说明" min-width="220" />
      </el-table>

      <div class="formula-block">
        <div>总分 = 可用维度加权平均（四维默认各 25%）；无数据的维度不计入，权重按比例摊回其余维度</div>
        <div>等级阈值与单次运行评分一致；性能与稳定性取近 14 天执行结果</div>
      </div>
    </div>

    <div class="panel">
      <h3 class="panel-title">
        <span>3. 质量门禁</span>
        <span class="sub">quality_gate_record</span>
      </h3>
      <el-table :data="gateRules" class="dark-table">
        <el-table-column prop="rule" label="规则" width="160" />
        <el-table-column label="默认阈值" width="140">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.threshold }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="desc" label="说明" min-width="360" />
      </el-table>

      <div class="precedence">
        <div class="block-title">阈值优先级（高到低）</div>
        <div v-for="(step, index) in overrideSteps" :key="step" class="step">
          <span class="step-no">{{ index + 1 }}</span>
          <span>{{ step }}</span>
        </div>
      </div>

      <div class="json-block">
        <div class="block-title">计划级覆盖示例（test_plan.execution_config）</div>
        <pre>{
  "gate": {
    "scoreThreshold": 85,
    "maxFailedCount": 2,
    "maxErrorCount": 0
  }
}</pre>
      </div>

      <div class="note">
        仅 SUCCESS / FAILED 的运行可以检查，其他状态返回 409；每次检查写入 quality_gate_record，
        保存当时的阈值快照和违规明细。门禁检查的是单次运行评分，不是平台四维总分。
      </div>
    </div>
  </div>
</template>

<style scoped>
.head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.page-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 18px;
  font-weight: 700;
}

.page-sub {
  margin-top: 5px;
  font-size: 12px;
  color: var(--aqg-text-muted);
}

.head-alert {
  margin-top: 14px;
}

.sub {
  font-size: 12px;
  font-weight: 400;
  color: var(--aqg-text-muted);
}

.formula-block {
  margin-top: 14px;
  padding: 12px 14px;
  border: 1px solid var(--aqg-border);
  border-radius: 8px;
  background: var(--aqg-panel);
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 12.5px;
  color: var(--aqg-text-secondary);
}

.grade-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
}

.grade-label {
  font-size: 12.5px;
  color: var(--aqg-text-secondary);
}

.precedence {
  margin-top: 16px;
}

.block-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--aqg-text);
  margin-bottom: 8px;
}

.step {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 7px 0;
  font-size: 12.5px;
  color: var(--aqg-text-secondary);
}

.step-no {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: rgba(59, 130, 246, 0.16);
  color: var(--aqg-blue);
  font-size: 11px;
  flex-shrink: 0;
}

.json-block {
  margin-top: 12px;
}

.json-block pre {
  margin: 0;
  padding: 12px 14px;
  border: 1px solid var(--aqg-border);
  border-radius: 8px;
  background: var(--aqg-panel);
  color: var(--aqg-text);
  font-family: ui-monospace, Consolas, monospace;
  font-size: 12.5px;
  line-height: 1.7;
  overflow-x: auto;
}

.note {
  margin-top: 14px;
  padding: 10px 12px;
  border-left: 3px solid var(--aqg-blue);
  background: rgba(59, 130, 246, 0.08);
  border-radius: 0 6px 6px 0;
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--aqg-text-secondary);
}

.dark-table {
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-header-bg-color: var(--aqg-panel);
  --el-table-header-text-color: var(--aqg-text-secondary);
  --el-table-border-color: var(--aqg-border);
  --el-table-row-hover-bg-color: #273449;
}
</style>
