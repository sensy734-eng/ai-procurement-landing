/**
 * 把 docs/diagrams/ 下的 SVG 渲染成同尺寸 PNG（2 倍像素密度），便于目视检查排版。
 *
 * 用法：cd web && node scripts/render_svg.mjs ../../docs/diagrams
 * 依赖 puppeteer-core 与系统 Chrome（与 ui_smoke.mjs 同一约定，--no-save 安装，不入库）。
 * 为什么需要：SVG 本身是交付物，但看图得用位图——文字溢出、压线这类问题只有渲染出来才发现。
 */
import puppeteer from 'puppeteer-core'
import fs from 'node:fs'
import path from 'node:path'

const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const dir = path.resolve(process.argv[2] || '../../docs/diagrams')
const files = fs.readdirSync(dir).filter((f) => f.endsWith('.svg'))

const browser = await puppeteer.launch({
  executablePath: CHROME,
  headless: 'new',
  args: ['--no-sandbox', '--font-render-hinting=none'],
})
const page = await browser.newPage()

for (const f of files) {
  const svg = fs.readFileSync(path.join(dir, f), 'utf8')
  const m = svg.match(/width="(\d+)"\s+height="(\d+)"/)
  if (!m) { console.log('跳过（读不到尺寸）', f); continue }
  const w = Number(m[1]), h = Number(m[2])
  await page.setViewport({ width: w, height: h, deviceScaleFactor: 2 })
  await page.setContent(`<!doctype html><meta charset="utf-8">
    <body style="margin:0;background:#fff">${svg}</body>`, { waitUntil: 'load' })
  await page.screenshot({ path: path.join(dir, f.replace(/\.svg$/, '.png')) })
  console.log('已渲染', f, `${w}x${h}`)
}

await browser.close()
