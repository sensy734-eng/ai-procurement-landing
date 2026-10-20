/**
 * 界面冒烟：用系统 Chrome 无头打开各页面，检查渲染与运行时错误。
 *
 * 用法：
 *   cd web && node scripts/ui_smoke.mjs
 * 前置：后端 8080、前端 5173 均已启动。
 * 依赖 puppeteer-core（--no-save 安装，不入库）。
 * 说明：页面按角色走查——系统管理页用 admin，基础数据页用 caigou01，
 *       因为权限矩阵按功能模块清单的角色列配置：物料与供应商归采购员，
 *       仓库库位与字典归系统管理员（M1-3、M1-4）。
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
  { path: '/master/supplier', name: '10-supplier', wait: '.supplier-page', label: '供应商档案' }
]

const problems = []

async function loginAs(page, username) {
  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle2' })
  await page.evaluate(() => localStorage.clear())
  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle2' })
  await page.waitForSelector('input', { timeout: 15000 })
  const inputs = await page.$$('input')
  await inputs[0].type(username)
  await inputs[1].type('123456')
  await Promise.all([
    page.waitForNavigation({ waitUntil: 'networkidle2', timeout: 20000 }).catch(() => null),
    page.click('button.el-button--primary')
  ])
  await new Promise((resolve) => setTimeout(resolve, 1200))
  const afterLogin = page.url()
  if (!afterLogin.includes('/home')) {
    problems.push(`${username} 登录后未进入工作台，当前地址 ${afterLogin}`)
  }
  console.log(`已登录 ${username}，地址 ${afterLogin}`)
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
