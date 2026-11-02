/**
 * 表格溢出体检：逐页打开（按角色），比较每张表格的内容宽与容器宽。
 *
 * 用法：cd web && node scripts/table_overflow.mjs
 * 前置：后端 8080、前端 5173 已启动。
 *
 * 为什么单独做这个：Element Plus 的 el-table 在列宽合计超过容器时，
 * 右侧 fixed 列会盖住中间列（而不是让用户明显看到滚动条），
 * 截图上看就是「金额被操作列压住」这类破相，肉眼逐页找很慢。
 */
import puppeteer from 'puppeteer-core'

const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5173'

const CASES = [
  { user: 'admin', pages: ['/system/users', '/system/roles', '/system/approval', '/system/params', '/system/logs', '/master/warehouse', '/master/dict', '/system/kb', '/assistant/audit'] },
  { user: 'caigou01', pages: ['/master/material', '/master/supplier', '/request', '/order', '/stock', '/stock/flow', '/price', '/price/deviation'] },
  { user: 'cangku01', pages: ['/arrival', '/arrival/register', '/inbound'] },
  { user: 'zhuguan01', pages: ['/request/todo', '/order/todo'] }
]

async function loginAs(page, user) {
  // 与 ui_smoke 保持同一套做法：清令牌后整页刷新，再用事件派发填表
  // （puppeteer 的 type 在连续切换账号时会丢输入）
  await page.goto(BASE + '/login', { waitUntil: 'networkidle2' })
  await page.evaluate(() => localStorage.clear())
  await page.reload({ waitUntil: 'networkidle2' })
  await page.waitForSelector('input', { timeout: 15000 })
  await page.evaluate((name) => {
    const inputs = document.querySelectorAll('input')
    const setValue = (el, value) => {
      el.value = value
      el.dispatchEvent(new Event('input', { bubbles: true }))
    }
    setValue(inputs[0], name)
    setValue(inputs[1], '123456')
  }, user)
  await new Promise((r) => setTimeout(r, 300))
  await page.click('button.el-button--primary')
  for (let i = 0; i < 30; i++) {
    await new Promise((r) => setTimeout(r, 400))
    if (page.url().includes('/home')) return
  }
  throw new Error(user + ' 登录未进入工作台：' + page.url())
}

const browser = await puppeteer.launch({
  executablePath: CHROME,
  headless: 'new',
  args: ['--no-sandbox']
})
const page = await browser.newPage()
const WIDTH = Number(process.env.W || 1440)
await page.setViewport({ width: WIDTH, height: 900, deviceScaleFactor: 1 })
console.log('视口宽 ' + WIDTH)

let current = ''
const rows = []
for (const group of CASES) {
  if (group.user !== current) {
    await loginAs(page, group.user)
    current = group.user
  }
  for (const path of group.pages) {
    await page.goto(BASE + path, { waitUntil: 'networkidle2' })
    await new Promise((r) => setTimeout(r, 900))
    const found = await page.evaluate(() => {
      const out = []
      document.querySelectorAll('.el-table').forEach((t, i) => {
        const wrap = t.querySelector('.el-scrollbar__wrap') || t.querySelector('.el-table__body-wrapper')
        const visible = t.getBoundingClientRect().width
        if (!wrap || !visible) return
        const cols = [...t.querySelectorAll('.el-table__header col')].map((c) => parseFloat(c.width) || 0)
        // 行高不一致 = 某一列内容换行把行撑高了，截图上就是「有的行两行字」
        const heights = [...t.querySelectorAll('.el-table__body-wrapper .el-table__row')]
          .map((r) => Math.round(r.getBoundingClientRect().height))
        const uniq = [...new Set(heights)]
        out.push({
          index: i,
          visible: Math.round(visible),
          content: Math.round(wrap.scrollWidth),
          sumCols: Math.round(cols.reduce((a, b) => a + b, 0)),
          rowHeights: uniq
        })
      })
      return out
    })
    found.forEach((f) => rows.push({ user: group.user, path, ...f }))
  }
}

await browser.close()

const ragged = rows.filter((r) => (r.rowHeights || []).length > 1)
console.log('行高不一致（有内容换行）的表格：' + ragged.length)
ragged.forEach((r) => console.log(`  ${r.user.padEnd(9)} ${r.path.padEnd(22)} 表${r.index}  行高 ${r.rowHeights.join(' / ')}`))
console.log('')

const bad = rows.filter((r) => r.content - r.visible > 2)
console.log('页面表格总数 ' + rows.length + '，溢出 ' + bad.length + '\n')
if (bad.length) {
  console.log('溢出清单（容器 / 内容 / 差值）')
  bad
    .sort((a, b) => b.content - b.visible - (a.content - a.visible))
    .forEach((r) => {
      console.log(`  ${r.user.padEnd(9)} ${r.path.padEnd(22)} 表${r.index}  ${r.visible} / ${r.content}  +${r.content - r.visible}`)
    })
} else {
  console.log('全部表格在容器内，无横向溢出。')
}
