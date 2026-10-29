/* -----------------------------------------------------------------------------
 * Genisource ERP — 官网截图用的演示数据
 *
 * 走真实业务接口（不是直接灌 SQL），因此单据号、库存账、移动加权平均成本、
 * 应收应付与会计凭证都由系统按真实规则生成，截图里看到的数是自洽的。
 *
 * 用法：
 *   1) 后端跑在 http://127.0.0.1:8081
 *   2) node tools/seed-demo.mjs
 * -------------------------------------------------------------------------- */

const BASE = process.env.ERP_BASE || 'http://127.0.0.1:8081'
const USER = process.env.ERP_USER || 'admin'
const PASS = process.env.ERP_PASS || 'admin123'

let token = ''
const problems = []

async function api(method, path, body) {
  const res = await fetch(BASE + path, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: 'Bearer ' + token } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const text = await res.text()
  let data = null
  try { data = text ? JSON.parse(text) : null } catch { /* 非 JSON */ }
  if (!res.ok) {
    const detail = data && (data.detail || data.title || data.message) || text.slice(0, 200)
    throw new Error(`${method} ${path} -> ${res.status} ${detail}`)
  }
  return data
}

const post = (p, b) => api('POST', p, b)
const get = (p) => api('GET', p)

/** 允许单步失败，但要把原因记下来，最后汇总。 */
async function step(label, fn) {
  try {
    const out = await fn()
    console.log('  ✓', label)
    return out
  } catch (err) {
    problems.push(`${label}: ${err.message}`)
    console.log('  ✗', label, '—', err.message)
    return null
  }
}

/* ============================ 1. 主数据 ============================ */

const CATEGORIES = [
  { code: 'CAT-FG', name: '成品' },
  { code: 'CAT-SF', name: '半成品' },
  { code: 'CAT-EC', name: '电子元件' },
  { code: 'CAT-ME', name: '结构件' },
  { code: 'CAT-PK', name: '包装耗材' },
  { code: 'CAT-AX', name: '辅料' },
]

// [code, name, spec, type, uom, category, purchased, sold, manufactured, standardCost, salePrice, leadTime, minOrder, safetyStock, lotControlled]
const ITEMS = [
  // 成品
  ['FG-TC220', '智能温控器 TC-220', '220V / 16A / 液晶显示', 'FINISHED', 'PCS', 'CAT-FG', false, true, true, 186.4, 328, 0, 0, 0, false],
  ['FG-TC110', '智能温控器 TC-110', '110V / 10A', 'FINISHED', 'PCS', 'CAT-FG', false, true, true, 152.7, 268, 0, 0, 0, false],
  ['FG-PM30', '开关电源模块 PM-30', 'DC 24V / 30W', 'FINISHED', 'PCS', 'CAT-FG', false, true, true, 108.2, 185, 0, 0, 0, false],
  ['FG-CB01', '主控盒 CB-01', '四路输出', 'FINISHED', 'PCS', 'CAT-FG', false, true, true, 238.5, 420, 0, 0, 0, false],
  // 半成品
  ['SF-PCBA', '主控板组件 M1', 'MCU + 阻容贴片', 'SEMI', 'PCS', 'CAT-SF', false, false, true, 62.3, 0, 0, 0, 0, false],
  ['SF-PWR', '电源板组件 A2', '变压器 + 整流滤波', 'SEMI', 'PCS', 'CAT-SF', false, false, true, 41.8, 0, 0, 0, 0, false],
  ['SF-DISP', '显示模块组件 S3', 'LCD + 指示灯', 'SEMI', 'PCS', 'CAT-SF', false, false, true, 27.6, 0, 0, 0, 0, false],
  // 电子元件
  ['RM-MCU', '主控芯片 MCU-STM32', 'LQFP-48', 'RAW', 'PCS', 'CAT-EC', true, false, false, 24.5, 0, 14, 500, 800, true],
  ['RM-RELAY', '功率继电器 REL-16A', '250VAC / 16A', 'RAW', 'PCS', 'CAT-EC', true, false, false, 6.8, 0, 7, 1000, 600, true],
  ['RM-CAP', '铝电解电容 470uF/35V', 'φ10×12.5', 'RAW', 'PCS', 'CAT-EC', true, false, false, 0.85, 0, 7, 2000, 3000, true],
  ['RM-RES', '贴片电阻 10K 0805', '±1%', 'RAW', 'PCS', 'CAT-EC', true, false, false, 0.032, 0, 10, 5000, 8000, false],
  ['RM-PCB', 'PCB 空板 双层 FR-4', '100×80mm', 'RAW', 'PCS', 'CAT-EC', true, false, false, 4.2, 0, 10, 500, 1200, false],
  ['RM-LCD', '液晶显示屏 LCD-12864', '带背光', 'RAW', 'PCS', 'CAT-EC', true, false, false, 18.6, 0, 12, 300, 500, true],
  ['RM-TRANS', '电源变压器 EI-35', '220V / 24V', 'RAW', 'PCS', 'CAT-EC', true, false, false, 12.4, 0, 10, 300, 400, false],
  ['RM-FUSE', '保险丝 2A 250V', '陶瓷管', 'RAW', 'PCS', 'CAT-EC', true, false, false, 0.62, 0, 7, 1000, 1500, false],
  ['RM-CONN', '接线端子 2P 5.08', '插拔式', 'RAW', 'PCS', 'CAT-EC', true, false, false, 1.15, 0, 7, 1000, 2000, false],
  ['RM-WIRE', '电子线束 AWG20', '150mm 带端子', 'RAW', 'PCS', 'CAT-EC', true, false, false, 2.75, 0, 10, 500, 1000, false],
  ['RM-SENSOR', '温度传感器 NTC-10K', 'B=3950', 'RAW', 'PCS', 'CAT-EC', true, false, false, 3.6, 0, 12, 500, 800, true],
  ['RM-LED', 'LED 指示灯 3mm 红', '共阳', 'RAW', 'PCS', 'CAT-EC', true, false, false, 0.24, 0, 7, 3000, 5000, false],
  // 结构件
  ['RM-SHELL', '阻燃塑壳 ABS-V0', '含卡扣', 'RAW', 'PCS', 'CAT-ME', true, false, false, 9.8, 0, 12, 500, 800, false],
  ['RM-PANEL', '面板贴膜 PC', '丝印成品', 'RAW', 'PCS', 'CAT-ME', true, false, false, 2.2, 0, 12, 500, 800, false],
  ['RM-SCREW', '不锈钢螺丝 M3×8', '304', 'RAW', 'PCS', 'CAT-ME', true, false, false, 0.12, 0, 5, 5000, 6000, false],
  ['RM-FOAM', '防震泡棉 EVA', '定制模切', 'RAW', 'PCS', 'CAT-ME', true, false, false, 1.05, 0, 8, 1000, 2000, false],
  // 包装耗材
  ['RM-BOX', '彩盒 250g 白卡', '四色印刷', 'RAW', 'PCS', 'CAT-PK', true, false, false, 3.4, 0, 10, 500, 1000, false],
  ['RM-LABEL', '标签 二维码', '含序列号', 'RAW', 'PCS', 'CAT-PK', true, false, false, 0.16, 0, 5, 2000, 3000, false],
  // 辅料
  ['RM-SOLDER', '无铅锡丝 Sn99.3Cu0.7', 'φ0.8mm', 'CONSUMABLE', 'KG', 'CAT-AX', true, false, false, 182, 0, 5, 5, 10, false],
  ['RM-GLUE', '导热硅胶', '白色 100g', 'CONSUMABLE', 'PCS', 'CAT-AX', true, false, false, 22, 0, 5, 20, 30, false],
]

const SUPPLIERS = [
  ['SUP-SHX', '上海芯元电子有限公司', 'SUPPLIER', '周立', '021-5432-1188', '上海市松江区新桥工业区', 30],
  ['SUP-NBH', '宁波华磁电子有限公司', 'SUPPLIER', '陈志强', '0574-8826-3355', '宁波市北仑区港口路 88 号', 30],
  ['SUP-DGL', '东莞立信塑胶五金有限公司', 'SUPPLIER', '李惠芬', '0769-8331-6620', '东莞市长安镇振安路 26 号', 45],
  ['SUP-SZX', '深圳明辉显示技术有限公司', 'SUPPLIER', '王鹏', '0755-2698-4400', '深圳市宝安区西乡大道 12 号', 30],
  ['SUP-WZJ', '温州精工线束有限公司', 'SUPPLIER', '徐建', '0577-8655-2017', '温州市龙湾区滨海三路 6 号', 30],
  ['SUP-HZP', '杭州包材印刷有限公司', 'SUPPLIER', '马丽', '0571-8817-5590', '杭州市余杭区仁和街道 5 号', 45],
]

const CUSTOMERS = [
  ['CUS-JSZ', '江苏中科电器有限公司', 'CUSTOMER', '赵启明', '0512-6288-3400', '苏州市工业园区星龙街 199 号', 30, 800000],
  ['CUS-ZJM', '浙江美凌电器有限公司', 'CUSTOMER', '孙丽娟', '0571-8602-7711', '杭州市萧山区新塘路 66 号', 30, 500000],
  ['CUS-SHJ', '上海锦江机电设备有限公司', 'CUSTOMER', '吴国强', '021-6288-9021', '上海市闵行区莘庄工业区 8 号', 45, 600000],
  ['CUS-GDS', '广东顺德小家电制造有限公司', 'CUSTOMER', '谭永坤', '0757-2266-1088', '佛山市顺德区容桂大道 15 号', 60, 1000000],
  ['CUS-AHH', '安徽合肥制冷设备有限公司', 'CUSTOMER', '刘志远', '0551-6388-2244', '合肥市高新区望江西路 700 号', 45, 400000],
  ['CUS-FJQ', '福建泉州智能电器有限公司', 'CUSTOMER', '黄伟杰', '0595-2233-6688', '泉州市鲤城区火炬工业区 3 号', 30, 300000],
  ['CUS-SDL', '山东临沂电子设备有限公司', 'CUSTOMER', '孟凡涛', '0539-8122-3300', '临沂市兰山区工业园一路 22 号', 30, 250000],
  ['CUS-HNC', '湖南长沙控制设备有限公司', 'CUSTOMER', '罗俊', '0731-8899-5566', '长沙市开福区沙坪工业组团 9 号', 45, 350000],
]

const WORK_CENTERS = [
  ['WC-SMT', 'SMT 贴片线', '电子车间', 960, 92, 180],
  ['WC-DIP', '插件线', '电子车间', 960, 88, 120],
  ['WC-ASM', '总装线', '装配车间', 960, 90, 95],
  ['WC-TEST', '测试老化台', '质量部', 960, 85, 60],
  ['WC-PACK', '包装线', '装配车间', 960, 95, 70],
]

/* ============================ 2. BOM ============================ */

const BOMS = {
  'FG-TC220': [['RM-SHELL', 1], ['RM-PANEL', 1], ['SF-PCBA', 1], ['SF-PWR', 1], ['SF-DISP', 1],
               ['RM-WIRE', 2], ['RM-SENSOR', 1], ['RM-SCREW', 4], ['RM-FOAM', 2], ['RM-BOX', 1], ['RM-LABEL', 1]],
  'FG-TC110': [['RM-SHELL', 1], ['RM-PANEL', 1], ['SF-PCBA', 1], ['SF-PWR', 1],
               ['RM-WIRE', 2], ['RM-SENSOR', 1], ['RM-SCREW', 4], ['RM-BOX', 1], ['RM-LABEL', 1]],
  'FG-PM30': [['RM-SHELL', 1], ['SF-PWR', 1], ['RM-TRANS', 1], ['RM-WIRE', 2],
              ['RM-CONN', 1], ['RM-SCREW', 2], ['RM-BOX', 1], ['RM-LABEL', 1]],
  'FG-CB01': [['RM-SHELL', 1], ['SF-PCBA', 1], ['SF-PWR', 1], ['SF-DISP', 1],
              ['RM-CONN', 2], ['RM-SCREW', 6], ['RM-FOAM', 2], ['RM-BOX', 1], ['RM-LABEL', 1]],
  'SF-PCBA': [['RM-PCB', 1], ['RM-MCU', 1], ['RM-RES', 12], ['RM-CAP', 4], ['RM-LED', 2], ['RM-SOLDER', 0.006]],
  'SF-PWR': [['RM-PCB', 1], ['RM-TRANS', 1], ['RM-CAP', 2], ['RM-FUSE', 1], ['RM-CONN', 1], ['RM-SOLDER', 0.005]],
  'SF-DISP': [['RM-LCD', 1], ['RM-PCB', 1], ['RM-LED', 3], ['RM-CONN', 1], ['RM-SOLDER', 0.004]],
}

const ROUTINGS = {
  'FG-TC220': [['SMT 贴片', 'WC-SMT', 20, 3.2], ['插件焊接', 'WC-DIP', 15, 4.5], ['总装', 'WC-ASM', 10, 6.8], ['老化测试', 'WC-TEST', 5, 5.5], ['包装入库', 'WC-PACK', 8, 2.4]],
  'FG-TC110': [['SMT 贴片', 'WC-SMT', 20, 3.0], ['插件焊接', 'WC-DIP', 15, 4.2], ['总装', 'WC-ASM', 10, 6.2], ['老化测试', 'WC-TEST', 5, 5.0], ['包装入库', 'WC-PACK', 8, 2.2]],
  'FG-PM30': [['插件焊接', 'WC-DIP', 15, 3.6], ['总装', 'WC-ASM', 10, 4.8], ['老化测试', 'WC-TEST', 8, 4.0], ['包装入库', 'WC-PACK', 8, 2.0]],
  'FG-CB01': [['SMT 贴片', 'WC-SMT', 25, 4.0], ['插件焊接', 'WC-DIP', 20, 5.6], ['总装', 'WC-ASM', 15, 8.4], ['老化测试', 'WC-TEST', 10, 7.2], ['包装入库', 'WC-PACK', 10, 3.0]],
  'SF-PCBA': [['SMT 贴片', 'WC-SMT', 25, 2.6], ['回流焊目检', 'WC-DIP', 10, 1.8]],
  'SF-PWR': [['插件焊接', 'WC-DIP', 15, 2.9], ['功能初测', 'WC-TEST', 8, 2.2]],
  'SF-DISP': [['SMT 贴片', 'WC-SMT', 20, 2.0], ['组装点亮', 'WC-ASM', 10, 1.6]],
}

/* ============================ 3. 期初库存 ============================ */

// warehouse code -> location code -> [{ item, qty, cost }]
// 产线从生产订单指定的主仓领料，因此原料、辅料、半成品都放在主仓；成品单独放成品仓。
const OPENING = {
  'WH-MAIN': {
    'LOC-EC': [
      ['RM-MCU', 6400, 24.2], ['RM-RELAY', 5200, 6.7], ['RM-CAP', 28000, 0.83], ['RM-RES', 88000, 0.031],
      ['RM-PCB', 8200, 4.15], ['RM-LCD', 4200, 18.4], ['RM-TRANS', 4800, 12.2], ['RM-FUSE', 8400, 0.6],
      ['RM-CONN', 12800, 1.12], ['RM-WIRE', 8200, 2.7], ['RM-SENSOR', 4600, 3.55], ['RM-LED', 36000, 0.23],
    ],
    'LOC-ME': [
      ['RM-SHELL', 5200, 9.65], ['RM-PANEL', 5400, 2.15], ['RM-SCREW', 58000, 0.115], ['RM-FOAM', 7200, 1.02],
    ],
    'LOC-PK': [
      ['RM-BOX', 5600, 3.32], ['RM-LABEL', 16000, 0.15],
    ],
    'LOC-SF': [
      ['SF-PCBA', 1400, 61.8], ['SF-PWR', 1200, 41.4], ['SF-DISP', 900, 27.2],
    ],
    'LOC-AX': [
      ['RM-SOLDER', 72, 181], ['RM-GLUE', 180, 21.8],
    ],
  },
  'WH-FG': {
    'LOC-FGA': [
      ['FG-TC220', 1400, 185.6], ['FG-TC110', 1400, 152.1],
    ],
    'LOC-FGB': [
      ['FG-PM30', 1600, 107.6], ['FG-CB01', 520, 237.4],
    ],
  },
}

/* ============================ 4. 业务单据 ============================ */

const TAX = 0.13

// [dayOffset, customer, warehouse, termDays, [[item, qty, price]...]]，dayOffset 相对 2026-10-29
const SALES_ORDERS = [
  [-32, 'CUS-JSZ', 'WH-FG', 30, [['FG-TC220', 400, 328], ['FG-TC110', 200, 268]], '年度框架第一批'],
  [-28, 'CUS-GDS', 'WH-FG', 60, [['FG-PM30', 600, 185]], '小家电配套订单'],
  [-24, 'CUS-ZJM', 'WH-FG', 30, [['FG-TC220', 260, 328], ['FG-CB01', 120, 420]], ''],
  [-20, 'CUS-SHJ', 'WH-FG', 45, [['FG-CB01', 90, 420], ['FG-PM30', 150, 185]], '含终端 electrical 柜配套'],
  [-16, 'CUS-AHH', 'WH-FG', 45, [['FG-TC110', 500, 268]], '制冷设备配套'],
  [-12, 'CUS-FJQ', 'WH-FG', 30, [['FG-TC220', 180, 328], ['FG-PM30', 240, 185]], ''],
  [-9, 'CUS-SDL', 'WH-FG', 30, [['FG-TC110', 320, 268]], ''],
  [-6, 'CUS-HNC', 'WH-FG', 45, [['FG-CB01', 60, 420], ['FG-TC220', 150, 328]], ''],
  [-4, 'CUS-JSZ', 'WH-FG', 30, [['FG-PM30', 300, 185], ['FG-CB01', 40, 420]], '追加订单'],
  [-2, 'CUS-GDS', 'WH-FG', 60, [['FG-TC220', 220, 328]], ''],
  [-1, 'CUS-ZJM', 'WH-FG', 30, [['FG-TC110', 180, 268], ['FG-PM30', 120, 185]], '急单，客户催交期'],
  [0, 'CUS-FJQ', 'WH-FG', 30, [['FG-CB01', 50, 420]], '新增：待评审'],
]

const PURCHASE_ORDERS = [
  [-30, 'SUP-SHX', 'WH-MAIN', 30, [['RM-MCU', 3000, 24.5], ['RM-RES', 30000, 0.032]]],
  [-26, 'SUP-NBH', 'WH-MAIN', 30, [['RM-TRANS', 2000, 12.4], ['RM-RELAY', 2000, 6.8]]],
  [-22, 'SUP-SZX', 'WH-MAIN', 30, [['RM-LCD', 1500, 18.6], ['RM-LED', 15000, 0.24]]],
  [-18, 'SUP-DGL', 'WH-MAIN', 45, [['RM-SHELL', 2500, 9.8], ['RM-SCREW', 20000, 0.12], ['RM-PANEL', 2500, 2.2]]],
  [-14, 'SUP-WZJ', 'WH-MAIN', 30, [['RM-WIRE', 3000, 2.75], ['RM-CONN', 5000, 1.15]]],
  [-10, 'SUP-NBH', 'WH-MAIN', 30, [['RM-CAP', 12000, 0.85], ['RM-FUSE', 4000, 0.62]]],
  [-7, 'SUP-HZP', 'WH-MAIN', 45, [['RM-BOX', 2000, 3.4], ['RM-LABEL', 8000, 0.16], ['RM-FOAM', 3000, 1.05]]],
  [-5, 'SUP-SHX', 'WH-MAIN', 30, [['RM-MCU', 1800, 24.8], ['RM-PCB', 3000, 4.2]]],
  [-3, 'SUP-SZX', 'WH-MAIN', 30, [['RM-LCD', 1200, 18.9], ['RM-SENSOR', 1500, 3.6]]],
  [-1, 'SUP-DGL', 'WH-MAIN', 45, [['RM-SHELL', 1200, 9.9], ['RM-SCREW', 12000, 0.12]]],
]

// [dayOffset, item, qty, plannedStartOffset, plannedEndOffset]
const PRODUCTION_ORDERS = [
  [-15, 'FG-TC220', 300, -14, -9],
  [-12, 'FG-TC110', 400, -11, -6],
  [-9, 'FG-PM30', 500, -8, -4],
  [-6, 'FG-CB01', 120, -5, -1],
  [-3, 'SF-PCBA', 600, -2, 2],
  [-2, 'SF-PWR', 500, -1, 3],
  [-1, 'FG-TC220', 260, 0, 5],
  [0, 'FG-PM30', 300, 1, 6],
]

/* ============================ 主流程 ============================ */

const TODAY = new Date('2026-10-29T00:00:00+08:00')
const day = (offset) => {
  const d = new Date(TODAY.getTime() + offset * 86400000)
  return d.toISOString().slice(0, 10)
}

const byCode = {}
const itemIds = {}
const partnerIds = {}
const whIds = {}
const wcIds = {}

async function main() {
  const auth = await post('/api/auth/login', { username: USER, password: PASS })
  token = auth.accessToken
  console.log('登录成功\n')

  console.log('① 物料分类')
  for (const c of CATEGORIES) {
    const saved = await step(c.name, () => post('/api/masterdata/item-categories', c))
    if (saved) byCode[c.code] = saved.id
  }

  console.log('② 供应商与客户')
  for (const s of SUPPLIERS) {
    const saved = await step(s[1], () => post('/api/masterdata/partners', {
      code: s[0], name: s[1], partnerType: s[2], contactName: s[3], phone: s[4], address: s[5],
      currency: 'CNY', paymentTermsDays: s[6], status: 'ACTIVE',
    }))
    if (saved) partnerIds[s[0]] = saved.id
  }
  for (const c of CUSTOMERS) {
    const saved = await step(c[1], () => post('/api/masterdata/partners', {
      code: c[0], name: c[1], partnerType: c[2], contactName: c[3], phone: c[4], address: c[5],
      currency: 'CNY', paymentTermsDays: c[6], creditLimit: c[7], status: 'ACTIVE',
    }))
    if (saved) partnerIds[c[0]] = saved.id
  }

  console.log('③ 仓库与库位')
  const WAREHOUSES = [
    ['WH-MAIN', '主仓', 'GENERAL', '一号厂区 A 幢'],
    ['WH-FG', '成品仓', 'FINISHED', '一号厂区 B 幢'],
  ]
  for (const w of WAREHOUSES) {
    const saved = await step(w[1], () => post('/api/inventory/warehouses', {
      code: w[0], name: w[1], warehouseType: w[2], address: w[3], status: 'ACTIVE',
    }))
    if (saved) whIds[w[0]] = saved.id
  }
  const LOCATIONS = [
    ['WH-MAIN', 'LOC-EC', '电子料区'],
    ['WH-MAIN', 'LOC-ME', '结构件区'],
    ['WH-MAIN', 'LOC-PK', '包材区'],
    ['WH-MAIN', 'LOC-SF', '半成品区'],
    ['WH-MAIN', 'LOC-AX', '辅料区'],
    ['WH-FG', 'LOC-FGA', '成品 A 区'],
    ['WH-FG', 'LOC-FGB', '成品 B 区'],
  ]
  const locIds = {}
  for (const [wh, code, name] of LOCATIONS) {
    const saved = await step(`库位 ${name}`, () => post('/api/inventory/locations', {
      warehouseId: whIds[wh], code, name, status: 'ACTIVE',
    }))
    if (saved) locIds[code] = saved.id
  }

  console.log('④ 工作中心')
  for (const wc of WORK_CENTERS) {
    const saved = await step(wc[1], () => post('/api/masterdata/work-centers', {
      code: wc[0], name: wc[1], department: wc[2], capacityMinutesPerDay: wc[3],
      efficiencyPercent: wc[4], costRatePerHour: wc[5], status: 'ACTIVE',
    }))
    if (saved) wcIds[wc[0]] = saved.id
  }

  console.log('⑤ 物料')
  for (const it of ITEMS) {
    const [code, name, spec, type, uom, cat, purchased, sold, manufactured, cost, price, lead, minOrder, safety, lot] = it
    const saved = await step(`${code} ${name}`, () => post('/api/masterdata/items', {
      code, name, spec, itemType: type, uomCode: uom, categoryId: byCode[cat],
      purchased, purchaseLeadTimeDays: purchased ? lead : 0, purchaseUomCode: purchased ? uom : undefined,
      purchaseFactor: purchased ? 1 : undefined, minOrderQty: purchased ? minOrder : 0,
      orderMultiple: purchased ? 100 : 1, safetyStock: purchased ? safety : 0,
      sold, salePrice: sold ? price : 0, manufactured,
      lotControlled: lot, standardCost: cost, currency: 'CNY', taxRate: TAX, status: 'ACTIVE',
    }))
    if (saved) itemIds[code] = saved.id
  }

  console.log('⑥ BOM')
  for (const [parent, lines] of Object.entries(BOMS)) {
    const bom = await step(`BOM ${parent}`, () => post('/api/masterdata/boms', {
      itemId: itemIds[parent], version: 'V1', baseQty: 1, effectiveFrom: day(-120),
      lines: lines.map(([code, qty], i) => ({
        lineNo: (i + 1) * 10, componentItemId: itemIds[code], qty, lossRate: 0.02, phantom: false,
      })),
    }))
    if (bom) await step(`  → 发布 ${parent}`, () => post(`/api/masterdata/boms/${bom.id}/release`))
  }

  console.log('⑦ 工艺路线')
  for (const [parent, ops] of Object.entries(ROUTINGS)) {
    const routing = await step(`工艺 ${parent}`, () => post('/api/masterdata/routings', {
      itemId: itemIds[parent], version: 'V1',
      operations: ops.map(([name, wc, setup, run], i) => ({
        seq: (i + 1) * 10, workCenterId: wcIds[wc], name, setupMinutes: setup, runMinutesPerUnit: run,
      })),
    }))
    if (routing) await step(`  → 发布 ${parent}`, () => post(`/api/masterdata/routings/${routing.id}/release`))
  }

  console.log('⑧ 期初库存')
  await get('/api/finance/accounts') // 触发科目表播种
  for (const [wh, groups] of Object.entries(OPENING)) {
    for (const [loc, lines] of Object.entries(groups)) {
      const doc = await step(`${wh}/${loc} 期初入库`, () => post('/api/inventory/documents', {
        docType: 'INBOUND', warehouseId: whIds[wh], locationId: locIds[loc],
        bizDate: day(-40), remark: '期初建账',
        lines: lines.map(([code, qty, cost]) => ({ itemId: itemIds[code], qty, unitCost: cost })),
      }))
      if (doc) await step('  → 过账', () => post(`/api/inventory/documents/${doc.id}/post`))
    }
  }

  console.log('⑨ 销售订单')
  const salesOrders = []
  for (const [offset, customer, wh, terms, lines, remark] of SALES_ORDERS) {
    const doc = await step(`${customer} ${day(offset)}`, () => post('/api/sales/orders', {
      partnerId: partnerIds[customer], warehouseId: whIds[wh], bizDate: day(offset),
      requiredDate: day(offset + 20), paymentTermDays: terms, remark,
      lines: lines.map(([code, qty, price]) => ({
        itemId: itemIds[code], qty, unitPrice: price, taxRate: TAX,
      })),
    }))
    if (!doc) continue
    // 最后一张停在草稿，让列表里出现不同状态
    if (offset === 0) { salesOrders.push(doc); continue }
    const submitted = await step('  → 提交', () => post(`/api/sales/orders/${doc.id}/submit`))
    const approved = submitted && submitted.status !== 'APPROVED'
      ? await step('  → 审批', () => post(`/api/sales/orders/${doc.id}/approve`))
      : submitted
    salesOrders.push(approved || doc)
  }

  console.log('⑩ 销售发货')
  // 前七张已审批订单按订单行发货（部分发足、部分留悬念）
  const shipPlan = [
    [0, [1.0, 1.0]], [1, [1.0]], [2, [0.6, 0.0]], [3, [1.0, 0.5]],
    [4, [0.5]], [5, [0.8, 0.0]], [6, [1.0]],
  ]
  for (const [idx, ratios] of shipPlan) {
    const order = salesOrders[idx]
    if (!order || order.status !== 'APPROVED') continue
    const lines = order.lines.map((l, i) => ({
      orderLineId: l.id,
      qty: +(l.qty * (ratios[i] ?? 0)).toFixed(3),
    })).filter((l) => l.qty > 0)
    if (!lines.length) continue
    const ship = await step(`发货 ${order.docNo}`, () => post(`/api/sales/orders/${order.id}/ship`, {
      warehouseId: whIds['WH-FG'], bizDate: day(-3 + idx), remark: '成品出库',
      lines,
    }))
    if (ship) await step('  → 过账', () => post(`/api/sales/shipments/${ship.id}/post`))
  }

  console.log('⑪ 采购订单与收料')
  for (const [offset, supplier, wh, terms, lines] of PURCHASE_ORDERS) {
    const doc = await step(`${supplier} ${day(offset)}`, () => post('/api/purchase/orders', {
      partnerId: partnerIds[supplier], warehouseId: whIds[wh], bizDate: day(offset),
      requiredDate: day(offset + 15), paymentTermDays: terms,
      lines: lines.map(([code, qty, price]) => ({
        itemId: itemIds[code], qty, unitPrice: price, taxRate: TAX,
      })),
    }))
    if (!doc) continue
    const submitted = await step('  → 提交', () => post(`/api/purchase/orders/${doc.id}/submit`))
    const approved = submitted && submitted.status !== 'APPROVED'
      ? await step('  → 审批', () => post(`/api/purchase/orders/${doc.id}/approve`))
      : submitted
    // 最近两张不收货，让采购列表里出现"待收货"
    if (offset >= -3 || !approved) continue
    const receive = await step('  → 收料', () => post(`/api/purchase/orders/${doc.id}/receive`, {
      warehouseId: whIds[wh], bizDate: day(offset + 8), remark: '按单收料入库',
      lines: approved.lines.map((l) => ({ orderLineId: l.id, qty: l.qty })),
    }))
    if (receive) await step('  → 过账', () => post(`/api/purchase/receipts/${receive.id}/post`))
  }

  console.log('⑫ 生产订单')
  for (const [offset, code, qty, startOffset, endOffset] of PRODUCTION_ORDERS) {
    const order = await step(`${code} × ${qty}`, () => post('/api/production/orders', {
      itemId: itemIds[code], qtyPlanned: qty, warehouseId: whIds['WH-MAIN'],
      plannedStart: day(startOffset), plannedEnd: day(endOffset), remark: '按 MRP 建议与插单排产',
    }))
    if (!order) continue
    if (offset > -3) continue // 最近两张停在下达前
    const released = await step('  → 下达', () => post(`/api/production/orders/${order.id}/release`))
    if (!released) continue
    if (offset > -6) continue // 中间两张停在已下达/待领料
    // 重新取一次：领料行的字段名是 openQty，不是 qty
    const detail = await get(`/api/production/orders/${order.id}`)
    const due = (detail.lines || []).filter((l) => (l.openQty ?? 0) > 0)
    if (!due.length) continue
    const issue = await step('  → 领料', () => post(`/api/production/orders/${order.id}/issue`, {
      bizDate: day(offset + 2), remark: '按组件清单领料',
      lines: due.map((l) => ({ orderLineId: l.id, qty: l.qtyLocked > 0 ? l.qtyLocked : l.openQty })),
    }))
    if (!issue) continue
    await step('  → 完工入库', () => post(`/api/production/orders/${order.id}/complete`, {
      qty, warehouseId: whIds['WH-FG'], bizDate: day(offset + 5), remark: '完工入成品仓',
    }))
  }

  console.log('⑬ MRP 运算')
  await step('净需求运算（45 天展望）', () => post('/api/mrp/runs', {
    horizonDays: 45, demandFrom: day(-10), demandTo: day(45),
    includeSafety: true, maxLevels: 5, remark: '官网演示：按现有订单与库存做一次净需求排程',
  }))

  console.log('⑭ 收款与付款')
  const receivables = await get('/api/finance/receivables?size=50').catch(() => ({ content: [] }))
  const list = Array.isArray(receivables) ? receivables : (receivables.content || [])
  let n = 0
  for (const r of list) {
    if (n >= 4) break
    const amount = +(r.totalAmount * 0.4).toFixed(2)
    const ok = await step(`收款 ${r.docNo || r.partnerName} ${amount}`, () =>
      post(`/api/finance/receivables/${r.id}/payment`, {
        amount, bizDate: day(-1), method: 'BANK', reference: '网银转账', remark: '按合同回款',
      }))
    if (ok) n++
  }
  const payables = await get('/api/finance/payables?size=50').catch(() => ({ content: [] }))
  const plist = Array.isArray(payables) ? payables : (payables.content || [])
  let m = 0
  for (const p of plist) {
    if (m >= 3) break
    const amount = +(p.totalAmount * 0.5).toFixed(2)
    const ok = await step(`付款 ${p.docNo || p.partnerName} ${amount}`, () =>
      post(`/api/finance/payables/${p.id}/payment`, {
        amount, bizDate: day(-1), method: 'BANK', reference: '网银转账', remark: '按账期付款',
      }))
    if (ok) m++
  }

  console.log('\n———— 完成 ————')
  if (problems.length) {
    console.log(`失败 ${problems.length} 项：`)
    problems.forEach((p) => console.log('  ·', p))
  } else {
    console.log('全部步骤成功。')
  }
}

main().catch((err) => {
  console.error(err)
  process.exit(1)
})
