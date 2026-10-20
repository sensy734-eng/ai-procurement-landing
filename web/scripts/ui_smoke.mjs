/**
 * 轮次 2 界面冒烟：用系统 Chrome 无头打开各页面，检查渲染与运行时错误。
 *
 * 用法：
 *   cd web && node scripts/ui_smoke.mjs
 * 前置：后端 8080、前端 5173 均已启动。
 * 依赖 puppeteer-core（--no-save 安装，不入库）。
 */
import puppeteer from 'puppeteer-core'
import fs from 'node:fs'
import path from 'node:path'

const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5173'
const SHOT_DIR = path.resolve('scripts/screenshots')

const pages = [
  { path: '/home', name: '01-home', wait: '.page-card, .home-page', label: '工作台' },
  { path: '/system/users', name: '02-users', wait: '.user-page', label: '用户管理' },
  { path: '/system/roles', name: '03-roles', wait: '.role-page', label: '角色权限' },
  { path: '/system/approval', name: '04-approval', wait: '.approval-page', label: '审批配置' },
  { path: '/system/params', name: '05-params', wait: '.param-page', label: '参数配置' },
  { path: '/system/logs', name: '06-logs', wait: '.log-page', label: '日志审计' }
]

const problems = []

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

  // 登录
  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle2' })
  await page.waitForSelector('input', { timeout: 15000 })
  const inputs = await page.$$('input')
  await inputs[0].type('admin')
  await inputs[1].type('123456')
  await page.screenshot({ path: path.join(SHOT_DIR, '00-login.png') })
  await Promise.all([
    page.waitForNavigation({ waitUntil: 'networkidle2', timeout: 20000 }).catch(() => null),
    page.click('button.el-button--primary')
  ])
  await new Promise((resolve) => setTimeout(resolve, 1200))
  const afterLogin = page.url()
  console.log(`登录后地址 ${afterLogin}`)
  if (!afterLogin.includes('/home')) {
    problems.push(`登录后未进入工作台，当前地址 ${afterLogin}`)
  }

  for (const item of pages) {
    await page.goto(BASE + item.path, { waitUntil: 'networkidle2' })
    try {
      await page.waitForSelector(item.wait, { timeout: 12000 })
    } catch (error) {
      problems.push(`${item.label} 未渲染出预期容器（${item.wait}）`)
    }
    await new Promise((resolve) => setTimeout(resolve, 900))
    const text = await page.evaluate(() => document.body.innerText.slice(0, 200))
    const rowCount = await page.evaluate(() => document.querySelectorAll('.el-table__row').length)
    await page.screenshot({ path: path.join(SHOT_DIR, item.name + '.png'), fullPage: true })
    console.log(`${item.label} 表格行数 ${rowCount}｜首屏文本 ${text.replace(/\n/g, ' ').slice(0, 70)}`)
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

main().catch((error) => {
  console.error('界面冒烟异常', error)
  process.exit(1)
})
