# -*- coding: utf-8 -*-
"""
极简 SVG 绘图工具：够画流程图，不引第三方库。

为什么手写 SVG 而不是拉 mermaid / graphviz：
  1) 中文排版要可控——流程图的文字必须整体在框内、不折行错位，通用工具的自动布局做不到；
  2) 输出是矢量，插进 Word/PPT 放大不失真，答辩投影也清楚；
  3) 零依赖，与项目「不引额外组件」的取向一致。

文字宽度按经验估算（中文 1.0em、ASCII 0.55em），用于居中与自动折行。
"""
import math


def esc(s):
    return (str(s).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"))


def measure(s, fs):
    """估算字符串渲染宽度（px）。中文按全角、其余按 0.55 倍字号。"""
    w = 0.0
    for ch in s:
        if ord(ch) > 0x2E80:          # CJK 及全角标点
            w += fs * 1.0
        elif ch in "（）【】〔〕「」，。：；、":
            w += fs * 1.0
        elif ch in "iljI.,:;'|!":
            w += fs * 0.30
        else:
            w += fs * 0.55
    return w


def wrap(s, maxw, fs):
    """按估算宽度折行，保持原有换行。中文逐字折，英文按空格折。"""
    out = []
    for para in str(s).split("\n"):
        line, cur = "", 0.0
        for ch in para:
            w = measure(ch, fs)
            if cur + w > maxw and line:
                out.append(line)
                line, cur = ch, w
            else:
                line += ch
                cur += w
        out.append(line)
    return out


class Svg:
    def __init__(self, w, h, bg="#ffffff"):
        self.w, self.h, self.bg = w, h, bg
        self.body = []
        self.defs = []

    # ---------------------------------------------------------------- 基础图元
    def rect(self, x, y, w, h, fill="#ffffff", stroke="none", rx=6, sw=1,
             dash=None, opacity=None):
        d = ' stroke-dasharray="%s"' % dash if dash else ""
        o = ' opacity="%s"' % opacity if opacity else ""
        self.body.append(
            '<rect x="%.1f" y="%.1f" width="%.1f" height="%.1f" rx="%s" fill="%s" '
            'stroke="%s" stroke-width="%s"%s%s/>'
            % (x, y, w, h, rx, fill, stroke, sw, d, o))

    def circle(self, cx, cy, r, fill="#ffffff", stroke="none", sw=1):
        self.body.append('<circle cx="%.1f" cy="%.1f" r="%.1f" fill="%s" stroke="%s" '
                         'stroke-width="%s"/>' % (cx, cy, r, fill, stroke, sw))

    def text(self, x, y, s, fs=13, fill="#1f2937", anchor="start", weight="normal",
             opacity=None, ls=None):
        o = ' opacity="%s"' % opacity if opacity else ""
        l = ' letter-spacing="%s"' % ls if ls else ""
        self.body.append(
            '<text x="%.1f" y="%.1f" font-size="%s" fill="%s" text-anchor="%s" '
            'font-weight="%s"%s%s>%s</text>'
            % (x, y, fs, fill, anchor, weight, o, l, esc(s)))

    def mtext(self, x, y, s, fs=13, fill="#1f2937", anchor="middle", lh=1.45,
              weight="normal", maxw=None):
        """多行文字，y 为首行基线；maxw 给定时先折行。"""
        lines = wrap(s, maxw, fs) if maxw else str(s).split("\n")
        n = len(lines)
        for i, ln in enumerate(lines):
            self.text(x, y + i * fs * lh, ln, fs, fill, anchor, weight)
        return n * fs * lh

    # ---------------------------------------------------------------- 箭头
    def _defs(self):
        if self.defs:
            return
        for name, color in (("ah", "#41556b"), ("ahL", "#8fa3b8"), ("ahR", "#c0562b")):
            self.defs.append(
                '<marker id="%s" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" '
                'markerHeight="7" orient="auto-start-reverse">'
                '<path d="M 0 0 L 10 5 L 0 10 z" fill="%s"/></marker>' % (name, color))

    def line(self, x1, y1, x2, y2, color="#41556b", sw=1.6, dash=None, arrow=True,
             head_len=7):
        """直线；arrow=True 时在 (x2,y2) 端加箭头，并按箭头长度回缩线段。"""
        self._defs()
        mk = "ah" if color == "#41556b" else ("ahL" if color == "#8fa3b8" else "ahR")
        if arrow:
            ang = math.atan2(y2 - y1, x2 - x1)
            x2i = x2 - head_len * math.cos(ang)
            y2i = y2 - head_len * math.sin(ang)
        else:
            x2i, y2i = x2, y2
        d = ' stroke-dasharray="%s"' % dash if dash else ""
        m = ' marker-end="url(#%s)"' % mk if arrow else ""
        self.body.append('<line x1="%.1f" y1="%.1f" x2="%.1f" y2="%.1f" stroke="%s" '
                         'stroke-width="%s"%s%s/>' % (x1, y1, x2i, y2i, color, sw, d, m))

    def poly(self, pts, color="#41556b", sw=1.6, dash=None, arrow=True, rx=8):
        """折线（圆角拐点），只在最后一段加箭头。"""
        self._defs()
        mk = "ah" if color == "#41556b" else ("ahL" if color == "#8fa3b8" else "ahR")
        d = "M %.1f %.1f" % pts[0]
        for i in range(1, len(pts) - 1):
            x0, y0 = pts[i - 1]
            x1, y1 = pts[i]
            x2, y2 = pts[i + 1]
            d += " L %.1f %.1f" % self._corner(x0, y0, x1, y1, rx)
            d += " Q %.1f %.1f %.1f %.1f" % (x1, y1, *self._corner(x1, y1, x2, y2, rx, back=True))
        if arrow:
            x1, y1 = pts[-2]
            x2, y2 = pts[-1]
            ang = math.atan2(y2 - y1, x2 - x1)
            d += " L %.1f %.1f" % (x2 - 7 * math.cos(ang), y2 - 7 * math.sin(ang))
        else:
            d += " L %.1f %.1f" % pts[-1]
        da = ' stroke-dasharray="%s"' % dash if dash else ""
        m = ' marker-end="url(#%s)"' % mk if arrow else ""
        self.body.append('<path d="%s" fill="none" stroke="%s" stroke-width="%s"%s%s/>'
                         % (d, color, sw, da, m))

    @staticmethod
    def _corner(x0, y0, x1, y1, r, back=False):
        dx, dy = x1 - x0, y1 - y0
        L = math.hypot(dx, dy) or 1
        r = min(r, L / 2)
        k = (r / L) if back else (1 - r / L)
        return (x0 + dx * k, y0 + dy * k)

    # ---------------------------------------------------------------- 复合图元
    def box(self, x, y, w, h, lines, fs=13, fill="#ffffff", stroke="#c8d4e0",
            fc="#1f2937", rx=8, sw=1.2, weight="normal", lh=1.4, align="middle",
            dash=None, sub=None, subfs=11, subfc="#6b7d90"):
        """带居中文字的框；lines 可为 str 或 list；sub 为框下小字。"""
        self.rect(x, y, w, h, fill, stroke, rx, sw, dash)
        if isinstance(lines, str):
            lines = [lines]
        # 估算行高与整体垂直居中
        n = len(lines)
        block_h = (n - 1) * fs * lh
        y0 = y + h / 2 - block_h / 2 + fs * 0.36
        for i, ln in enumerate(lines):
            self.text(x + w / 2, y0 + i * fs * lh, ln, fs, fc, "middle", weight)
        if sub:
            self.text(x + w / 2, y + h + subfs * 1.25, sub, subfs, subfc, "middle")

    def badge(self, x, y, s, fs=11, fill="#eef3f9", fc="#2e5f8a", h=None, padx=8,
              stroke="none"):
        w = measure(s, fs) + padx * 2
        h = h or fs * 1.9
        self.rect(x, y, w, h, fill, stroke, h / 2, 1)
        self.text(x + w / 2, y + h / 2 + fs * 0.36, s, fs, fc, "middle")
        return w

    def label_mid(self, x1, y1, x2, y2, s, fs=11, fc="#5a6b7b", bg="#ffffff", dy=-6):
        """在线段中点放文字（带底色遮挡线段）。"""
        mx, my = (x1 + x2) / 2, (y1 + y2) / 2 + dy
        w = measure(s, fs) + 6
        self.rect(mx - w / 2, my - fs * 0.95, w, fs * 1.55, bg, "none", 2, 0)
        self.text(mx, my, s, fs, fc, "middle")

    # ---------------------------------------------------------------- 输出
    def render(self):
        self._defs()
        return ('<svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" '
                'viewBox="0 0 %d %d" font-family="Microsoft YaHei, PingFang SC, '
                'Hiragino Sans GB, sans-serif">\n<defs>%s</defs>\n'
                '<rect width="%d" height="%d" fill="%s"/>\n%s\n</svg>'
                % (self.w, self.h, self.w, self.h, "".join(self.defs),
                   self.w, self.h, self.bg, "\n".join(self.body)))

    def save(self, path):
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write(self.render())


# ------------------------------------------------------------------ 配色
INK = "#1f2937"       # 主文字
INK2 = "#5a6b7b"      # 次文字
LINE = "#c8d4e0"      # 边框
BLUE = "#2e5f8a"      # 主色
BLUE_D = "#1f4a6d"
BLUE_L = "#eef3f9"
GREEN = "#2e7d5b"
GREEN_L = "#eaf4ef"
ORANGE = "#c0562b"
ORANGE_L = "#fbf0ea"
GREY_L = "#f5f7fa"
PURPLE = "#5b4b8a"
