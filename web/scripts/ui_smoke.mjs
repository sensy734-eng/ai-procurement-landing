/**
 * 界面冒烟：用系统 Chrome 无头打开各页面，检查渲染与运行时错误。
 *
 * 用法：
 *   cd web && node scripts/ui_smoke.mjs
 * 前置：后端 8080、前端 5173 均已启动。
 * 依赖 puppeteer-core（--no-save 安装，不入库）。
 * 说明：页面按角色走查——系统管理页用 admin，基础数据页与采购订单用 caigou01，
 *       我的待办与订单待办分别用 zhuguan01（审批待办归主管）。
 *       因为权限矩阵按功能模块清单的角色列配置：物料与供应商归采购员，
 *       仓库库位与字典归系统管理员（M1-3、M1-4）。
 *       轮次 5 起补 P7 采购订单与 P9 订单待办；轮次 6 起补 P10 到货验收列表、
 *       P11 验收登记（仓管员 cangku01）与主管视角的验收统计，共十七页。
 */
import puppeteer from 'puppeteer-core'
import fs from 'node:fs'
import path from 'node:path'

const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5173'
const SHOT_DIR = path.resolve('scripts/screenshots')

const adminPages = [
  { path: '/home', name: '01-home', wait: '.page-card, .home-page', label: '工作台' },
  { path: '/system/users', name: '02-users', wait: '.user-page', label: '用户管理' },
  { path: '/system/roles', name: '03-roles', wait: '.role-page', label: '角色权限' },
  { path: '/system/approval', name: '04-approval', wait: '.approval-page', label: '审批配置' },
  { path: '/system/params', name: '05-params', wait: '.param-page', label: '参数配置' },
  { path: '/system/logs', name: '06-logs', wait: '.log-page', label: '日志审计' },
  { path: '/master/warehouse', name: '07-warehouse', wait: '.warehouse-page', label: '仓库与库位' },
  { path: '/master/dict', name: '08-dict', wait: '.dict-page', label: '业务字典' }
]

const buyerPages = [
  { path: '/master/material', name: '09-material', wait: '.material-page', label: '物料档案' },
  { path: '/master/supplier', name: '10-supplier', wait: '.supplier-page', label: '供应商档案' },
  { path: '/request', name: '11-request', wait: '.request-page', label: '采购申请' },
  { path: '/order', name: '13-order', wait: '.order-page', label: '采购订单' }
]

// 仓管员：到货验收全流程（登记页用订单 1 的 query 打开，避免依赖下拉时序）
const keeperPages = [
  { path: '/arrival', name: '15-arrival', wait: '.arrival-page', label: '到货验收列表' },
  { path: '/arrival/register?orderId=1', name: '16-arrival-register', wait: '.register-page', label: '验收登记' }
]

const managerPages = [
  { path: '/request/todo', name: '12-todo', wait: '.todo-page', label: '我的待办' },
  { path: '/order/todo', name: '14-order-todo', wait: '.todo-page', label: '订单待办' },
  { path: '/arrival', name: '17-arrival-stat', wait: '.arrival-page', label: '到货验收（主管统计）' }
]

const problems = []

async function loginAs(page, username) {
  // 连续切换账号：先清令牌再整页刷新（SPA 同路径 goto 是 no-op）。
  // 填表用 evaluate 派发 input 事件—— puppeteer 的 type 在第三次切换时
  // 会稳定丢失输入（疑似与 Vue 响应式时序有关），事件派发方式可复现。
  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle2' })
  await page.evaluate(() => localStorage.clear())
  await page.reload({ waitUntil: 'networkidle2' })
  await page.waitForSelector('input', { timeout: 15000 })
  await page.evaluate((user) => {
    const inputs = document.querySelectorAll('input')
    const setValue = (el, value) => {
      el.value = value
      el.dispatchEvent(new Event('input', { bubbles: true }))
    }
    setValue(inputs[0], user)
    setValue(inputs[1], '123456')
  }, username)
  await new Promise((resolve) => setTimeout(resolve, 300))
  await page.click('button.el-button--primary')
  // 登录成功是 SPA 跳转（无整页刷新），轮询地址即可
  for (let i = 0; i < 20; i++) {
    await new Promise((resolve) => setTimeout(resolve, 500))
    if (page.url().includes('/home')) {
      console.log(`已登录 ${username}，地址 ${page.url()}`)
      return
    }
  }
  problems.push(`${username} 登录后未进入工作台，当前地址 ${page.url()}`)
}

async function main() {
  fs.mkdirSync(SHOT_DIR, { recursive: true })
  const browser = await puppeteer.launch({
    executablePath: CHROME,
    headless: 'new',
    args: ['--no-sandbox', '--disable-dev-shm-usage', '--window-size=1440,900']
  })
  const page = await browser.newPage()
  await page.setViewport({ width: 1440, height: 900 })

  page.on('console', (msg) => {
    if (msg.type() === 'error') {
      problems.push(`[console] ${msg.text().slice(0, 240)}`)
    }
  })
  page.on('pageerror', (error) => {
    problems.push(`[pageerror] ${String(error).slice(0, 240)}`)
  })
  page.on('response', (response) => {
    if (response.status() >= 400) {
      problems.push(`[http ${response.status()}] ${response.url()}`)
    }
  })

  await loginAs(page, 'admin')
  await page.screenshot({ path: path.join(SHOT_DIR, '00-login-admin.png') })

  for (const item of adminPages) {
    await visit(page, item)
  }

  await loginAs(page, 'caigou01')
  await page.screenshot({ path: path.join(SHOT_DIR, '00-login-buyer.png') })

  for (const item of buyerPages) {
    await visit(page, item)
  }

  await loginAs(page, 'cangku01')
  await page.screenshot({ path: path.join(SHOT_DIR, '00-login-keeper.png') })

  for (const item of keeperPages) {
    await visit(page, item)
  }

  await loginAs(page, 'zhuguan01')
  await page.screenshot({ path: path.join(SHOT_DIR, '00-login-manager.png') })

  for (const item of managerPages) {
    await visit(page, item)
  }

  await browser.close()

  if (problems.length) {
    console.log('\n存在问题：')
    for (const problem of problems) {
      console.log('  - ' + problem)
    }
    process.exit(1)
  }
  console.log('\n界面冒烟通过，截图在 scripts/screenshots/')
}

async function visit(page, item) {
  await page.goto(BASE + item.path, { waitUntil: 'networkidle2' })
  try {
    await page.waitForSelector(item.wait, { timeout: 12000 })
  } catch (error) {
    problems.push(`${item.label} 未渲染出预期容器（${item.wait}）`)
  }
  // 等接口数据回填，避免把加载态计成 0 行
  await new Promise((resolve) => setTimeout(resolve, 1600))
  const text = await page.evaluate(() => document.body.innerText.slice(0, 200))
  // 表格页数 el-table 行，卡片式页面（角色、审批节点）数卡片
  const rowCount = await page.evaluate(
    () => document.querySelectorAll('.el-table__row, .role-item, .node-card').length
  )
  await page.screenshot({ path: path.join(SHOT_DIR, item.name + '.png'), fullPage: true })
  console.log(`${item.label} 数据行数 ${rowCount}｜首屏文本 ${text.replace(/\n/g, ' ').slice(0, 70)}`)
}

main().catch((error) => {
  console.error('界面冒烟异常', error)
  process.exit(1)
})
