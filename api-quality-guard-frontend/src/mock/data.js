// 前端演示用 mock 数据；接入后端后，将各页面中的引用替换为 http 请求即可。
import http from '@/api/http'

export const dashboardStats = {
  apiTotal: 128,
  coverage: 76,
  avgScore: 87.5,
  todayRuns: 342,
}

export const scoreTrend = {
  dates: [
    '09-29', '09-30', '10-01', '10-02', '10-03', '10-04', '10-05',
    '10-06', '10-07', '10-08', '10-09', '10-10', '10-11', '10-12',
  ],
  values: [79.2, 80.5, 81.1, 79.8, 82.4, 83.0, 84.6, 83.9, 85.2, 86.1, 85.4, 86.8, 87.0, 87.5],
}

export const riskApis = [
  { name: '创建订单', method: 'POST', path: '/order/create', risk: '高', issue: '缺少库存校验，存在超卖风险' },
  { name: '取消订单', method: 'PUT', path: '/order/cancel', risk: '高', issue: '并发场景下可能重复退款' },
  { name: '更新用户资料', method: 'PUT', path: '/user/profile', risk: '中', issue: '未校验手机号格式' },
  { name: '批量删除消息', method: 'DELETE', path: '/message/batch', risk: '中', issue: '缺少权限校验' },
  { name: '查询订单详情', method: 'GET', path: '/order/detail/{id}', risk: '中', issue: '响应时间 P95 超过 800ms' },
]

export const recentRuns = [
  { name: '订单模块回归', time: '2026-10-12 14:32', status: '成功', score: 91.2 },
  { name: '用户中心冒烟', time: '2026-10-12 11:05', status: '失败', score: 68.4 },
  { name: '商品服务全量', time: '2026-10-12 09:40', status: '成功', score: 88.7 },
  { name: '支付网关巡检', time: '2026-10-11 20:15', status: '运行中', score: null },
  { name: '订单模块回归', time: '2026-10-11 16:00', status: '成功', score: 90.1 },
]

export const apiList = [
  { id: 1, name: '用户登录', method: 'POST', path: '/user/login', module: '用户中心', status: '正常', score: 95 },
  { id: 2, name: '查询订单列表', method: 'GET', path: '/order/list', module: '订单中心', status: '正常', score: 88 },
  { id: 3, name: '创建订单', method: 'POST', path: '/order/create', module: '订单中心', status: '风险', score: 62 },
  { id: 4, name: '取消订单', method: 'PUT', path: '/order/cancel', module: '订单中心', status: '风险', score: 68 },
  { id: 5, name: '用户注册', method: 'POST', path: '/user/register', module: '用户中心', status: '正常', score: 91 },
  { id: 6, name: '查询商品详情', method: 'GET', path: '/product/detail/{id}', module: '商品中心', status: '正常', score: 86 },
  { id: 7, name: '更新用户资料', method: 'PUT', path: '/user/profile', module: '用户中心', status: '告警', score: 74 },
  { id: 8, name: '删除收货地址', method: 'DELETE', path: '/address/delete/{id}', module: '用户中心', status: '正常', score: 83 },
]

export const importPreview = {
  apiTotal: 42,
  get: 18,
  post: 15,
  put: 5,
  delete: 4,
}

export const apiDetailMap = {
  1: {
    id: 1,
    name: '用户登录',
    method: 'POST',
    path: '/user/login',
    module: '用户中心',
    score: 95,
    requestParams: {
      username: 'string，必填，4~20 字符',
      password: 'string，必填，6~32 字符',
      rememberMe: 'boolean，可选，默认 false',
    },
    responseExample: {
      code: 0,
      message: 'success',
      data: {
        token: 'eyJhbGciOiJIUzI1NiIs...',
        expiresIn: 7200,
        userInfo: { id: 1001, nickname: '张三', role: 'USER' },
      },
    },
    quality: { paramIntegrity: 96, exceptionHandling: 90, responseTime: 94, testCoverage: 100 },
  },
  3: {
    id: 3,
    name: '创建订单',
    method: 'POST',
    path: '/order/create',
    module: '订单中心',
    score: 62,
    requestParams: {
      skuId: 'integer，必填',
      quantity: 'integer，必填，1~99',
      addressId: 'integer，必填',
    },
    responseExample: {
      code: 0,
      message: 'success',
      data: { orderId: 20261012001, status: 'CREATED', amount: 199.0 },
    },
    quality: { paramIntegrity: 72, exceptionHandling: 55, responseTime: 68, testCoverage: 54 },
  },
  7: {
    id: 7,
    name: '更新用户资料',
    method: 'PUT',
    path: '/user/profile',
    module: '用户中心',
    score: 74,
    requestParams: {
      nickname: 'string，可选，1~30 字符',
      phone: 'string，可选，未声明格式约束',
      avatar: 'string(url)，可选',
    },
    responseExample: { code: 0, message: 'success', data: null },
    quality: { paramIntegrity: 66, exceptionHandling: 80, responseTime: 92, testCoverage: 58 },
  },
}

export const execRecords = [
  {
    id: 1, name: '订单模块回归', time: '2026-10-12 14:32', status: '成功',
    duration: '12.4s', assertionsPassed: 46, failedReason: '—',
  },
  {
    id: 2, name: '用户中心冒烟', time: '2026-10-12 11:05', status: '失败',
    duration: '8.1s', assertionsPassed: 21,
    failedReason: '状态码断言失败：期望 200 实际 500（/user/profile）',
  },
  {
    id: 3, name: '商品服务全量', time: '2026-10-12 09:40', status: '成功',
    duration: '21.7s', assertionsPassed: 58, failedReason: '—',
  },
  {
    id: 4, name: '支付网关巡检', time: '2026-10-11 20:15', status: '异常',
    duration: '3.2s', assertionsPassed: 4,
    failedReason: '请求超时：/pay/callback 连接超时（5000ms）',
  },
  {
    id: 5, name: '订单模块回归', time: '2026-10-11 16:00', status: '成功',
    duration: '11.9s', assertionsPassed: 44, failedReason: '—',
  },
]

export const execDetailMap = {
  1: {
    status: 'PASSED',
    durationMs: 12400,
    assertions: [
      { name: 'HTTP 状态码 = 200', expected: '200', actual: '200', passed: true },
      { name: '响应 code = 0', expected: '0', actual: '0', passed: true },
      { name: '响应时间 < 500ms', expected: '< 500ms', actual: '142ms', passed: true },
      { name: 'data.orderId 存在', expected: '存在', actual: '20261012001', passed: true },
    ],
  },
  2: {
    status: 'FAILED',
    durationMs: 8120,
    assertions: [
      { name: 'HTTP 状态码 = 200', expected: '200', actual: '500', passed: false },
      { name: '响应 code = 0', expected: '0', actual: '—', passed: false },
      { name: '响应时间 < 500ms', expected: '< 500ms', actual: '128ms', passed: true },
    ],
  },
}

export const quickRunResult = {
  status: 'PASSED',
  durationMs: 128,
  assertions: [
    { name: 'HTTP 状态码 = 200', expected: '200', actual: '200', passed: true },
    { name: '响应 code = 0', expected: '0', actual: '0', passed: true },
    { name: '响应时间 < 500ms', expected: '< 500ms', actual: '128ms', passed: true },
  ],
}

export const qualityOverview = {
  total: 92.4,
  grade: '优秀',
  weekDelta: 2.6,
  dimensions: [
    { name: '接口规范性', score: 95, color: '#3b82f6' },
    { name: '测试覆盖率', score: 76, color: '#f59e0b' },
    { name: '性能表现', score: 88, color: '#10b981' },
    { name: '稳定性', score: 91, color: '#8b5cf6' },
  ],
}

export const qualityRules = [
  { id: 1, name: '接口必须存在描述', desc: '所有接口需在文档中提供 description 说明', level: '一般', enabled: true },
  { id: 2, name: '接口必须有参数校验', desc: '请求参数需声明类型与约束（长度 / 范围 / 正则）', level: '重要', enabled: true },
  { id: 3, name: '响应时间限制', desc: '接口响应时间 P95 不得超过 500ms', level: '重要', enabled: true },
  { id: 4, name: '测试通过率要求', desc: '接口关联用例的断言通过率必须超过 90%', level: '严重', enabled: true },
  { id: 5, name: '禁止明文敏感字段', desc: '响应中不得返回明文手机号、身份证号', level: '严重', enabled: false },
]

export const gateStatus = {
  passed: false,
  score: 74.2,
  threshold: 80,
  checkedAt: '2026-10-12 14:32',
  violations: [
    { rule: '测试覆盖率不足', detail: '当前覆盖率 76%，要求 ≥ 90%', level: '严重' },
    { rule: '存在高风险接口', detail: '2 个接口风险等级为高（创建订单、取消订单）', level: '严重' },
    { rule: '性能指标不达标', detail: '订单详情接口 P95 响应时间 820ms，要求 < 500ms', level: '重要' },
    { rule: '质量总分未达标', detail: '综合评分 74.2，要求 ≥ 80', level: '严重' },
  ],
  history: [
    { id: 1, time: '2026-10-12 14:32', trigger: 'CI #128', passed: false, score: 74.2 },
    { id: 2, time: '2026-10-11 20:15', trigger: '手动触发', passed: true, score: 86.5 },
    { id: 3, time: '2026-10-11 16:00', trigger: 'CI #127', passed: true, score: 88.1 },
    { id: 4, time: '2026-10-10 18:22', trigger: 'CI #126', passed: false, score: 78.9 },
  ],
}

export const aiReports = [
  {
    id: 1,
    api: 'POST /order/create',
    apiName: '创建订单',
    riskLevel: '高风险',
    score: 62,
    issue: '订单创建接口缺少库存校验',
    impact: '高并发场景下可能导致库存超卖，产生资损',
    suggestions: [
      '在扣减库存前校验可用库存，并使用乐观锁防止并发超卖',
      '增加事务控制，保证订单与库存数据的一致性',
      '对超卖场景增加补偿机制与实时告警',
    ],
    optimization: ['引入库存预占机制，缩短扣减窗口', '对创建订单接口增加限流保护'],
    testAdvice: [
      '补充并发下单场景测试（≥ 50 并发）',
      '补充库存不足时的边界用例',
      '补充事务回滚后的数据一致性断言',
    ],
    riskNote: '该接口属于大促核心链路，故障影响面为 P0 级，建议纳入发布门禁必检项。',
  },
  {
    id: 2,
    api: 'PUT /order/cancel',
    apiName: '取消订单',
    riskLevel: '高风险',
    score: 68,
    issue: '取消订单接口在并发调用下可能重复退款',
    impact: '同一订单被多次取消时，退款操作可能执行两次，造成资金损失',
    suggestions: [
      '为取消操作增加幂等键（订单号 + 请求号）',
      '状态流转增加 CAS 校验（仅 CREATED/PAID 状态可取消）',
    ],
    optimization: ['退款操作接入对账系统，异常自动补偿'],
    testAdvice: ['补充并发取消同一订单的测试', '补充幂等性断言（重复请求返回相同结果）'],
    riskNote: '资金链路操作，任何重复执行的缺陷都可能造成直接资损。',
  },
  {
    id: 3,
    api: 'PUT /user/profile',
    apiName: '更新用户资料',
    riskLevel: '中风险',
    score: 74,
    issue: 'phone 参数未声明格式约束',
    impact: '可能写入非法手机号，影响下游短信、风控等依赖手机号的业务',
    suggestions: ['为 phone 参数增加正则约束（^1[3-9]\\d{9}$）', '写入前统一做格式归一化'],
    optimization: ['在文档层面为所有联系类字段统一约束规范'],
    testAdvice: ['补充非法手机号格式的用例（字母、超长、空值）'],
    riskNote: '数据质量问题会缓慢扩散到下游系统，建议尽快修复。',
  },
]

export const assistantShortcuts = ['为什么接口评分下降？', '哪些接口存在风险？', '如何优化测试覆盖率？']

export const assistantAnswers = {
  '为什么接口评分下降？': {
    conclusion: '近 7 天平台平均质量评分从 86.1 降至 82.4，主要由订单模块拖累。',
    points: [
      '创建订单接口新版本缺失库存校验，2 条用例持续失败，功能通过率下降 8.3%',
      '订单查询接口 P95 响应时间由 210ms 升至 820ms，性能维度扣分明显',
      '新增 5 个接口未配置测试用例，测试覆盖率由 81% 降至 76%',
    ],
    advice: ['优先修复创建订单接口的库存校验问题', '对新增接口使用规则生成一次性补齐基础用例'],
  },
  '哪些接口存在风险？': {
    conclusion: '当前共识别 2 个高风险接口、3 个中风险接口，集中在订单与用户模块。',
    points: [
      'POST /order/create：缺少库存校验，评分 62，高风险',
      'PUT /order/cancel：并发重复退款风险，评分 68，高风险',
      'PUT /user/profile：手机号无格式约束，评分 74，中风险',
    ],
    advice: ['高风险接口纳入门禁必检项', '在测试用例中补充并发与幂等场景'],
  },
  '如何优化测试覆盖率？': {
    conclusion: '覆盖率短板集中在新增接口与异常分支，可通过规则生成 + AI 增强两步补齐。',
    points: [
      '5 个新增接口尚无用例，规则生成可覆盖正常值 / 边界 / 必填缺失 / 类型异常四类场景',
      '已有用例中 63% 集中在正常路径，异常分支（4xx/5xx）覆盖不足',
      'AI 增强可补充业务场景用例（并发、幂等、状态流转）',
    ],
    advice: ['先对新增接口执行规则生成', '再对核心链路接口运行 AI 增强生成'],
  },
}

export function fetchHealth() {
  return http.get('/health')
}
