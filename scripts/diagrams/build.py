#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""README 다이어그램 생성기.

docs/assets/ 아래 SVG 두 개를 만든다.

  architecture.svg  이 스크립트가 좌표를 직접 계산해 그린다.
  modules.svg       docs/assets/modules.mmd 를 mermaid로 렌더한 결과다.

왜 SVG를 커밋해 두는가 — README를 GitHub 밖 뷰어에서 열면 mermaid 코드펜스가
차트가 아니라 소스 그대로 보이고, <picture>/<img> 태그는 코드 블록으로 노출된다.
미리 렌더해 두고 마크다운 이미지 문법으로 넣으면 렌더러를 가리지 않는다.

사용법:
    python3 scripts/diagrams/build.py            # 둘 다
    python3 scripts/diagrams/build.py modules    # 하나만

modules 렌더에는 Chrome이 필요하다(mermaid가 텍스트 폭을 재려면 실제 레이아웃 엔진이
있어야 해서 jsdom으로는 대체되지 않는다). CHROME_BIN 환경변수로 경로를 지정할 수 있고,
없으면 아래 CHROME_CANDIDATES를 순서대로 찾는다.
"""
import html
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ASSETS = os.path.join(ROOT, "docs", "assets")

CHROME_CANDIDATES = [
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
    "/Applications/Chromium.app/Contents/MacOS/Chromium",
    "google-chrome",
    "chromium",
    "chromium-browser",
]

FONT = ("ui-sans-serif,-apple-system,'Segoe UI',Roboto,'Helvetica Neue',Arial,"
        "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif")


# ---------------------------------------------------------------- architecture

W, H, SHIFT = 1300, 840, -60

# 흰 배경 카드로 고정한다. 마크다운 이미지 문법에서는 <picture>를 쓸 수 없어
# 라이트/다크 두 벌을 두더라도 테마에 따라 고를 방법이 없다.
C = dict(page="#ffffff", zone_fill="#f6f8fa", zone_stroke="#d1d9e0", card="#ffffff",
         border="#d1d9e0", text="#1f2328", muted="#59636e",
         neutral="#6e7781", blue="#0969da", purple="#8250df", pink="#bf3989")

# 이름: (x, y, 너비, 높이, 제목, 부제, 강조색)
BOXES = {
    "client":  (40, 400, 140, 76, "클라이언트", "웹, 앱", "neutral"),
    "cf":      (225, 400, 150, 76, "Cloudflare", "DNS, CDN, WAF", "neutral"),
    "nginx":   (450, 400, 100, 76, "nginx", ":80", "neutral"),
    "app":     (580, 372, 330, 132, "Spring Boot 4", ":8080 API, :8081 관리", "blue"),
    "mariadb": (450, 575, 150, 64, "MariaDB", "Flyway V1–V34", "neutral"),
    "es":      (625, 575, 165, 64, "Elasticsearch", "조회 전용 색인", "neutral"),
    "alloy":   (790, 680, 140, 64, "Grafana Alloy", "", "pink"),
    "r2":      (470, 120, 180, 76, "Cloudflare R2", "원본, 변형본", "purple"),
    "worker":  (830, 120, 190, 76, "Cloudflare Worker", "이미지 변환", "purple"),
    "stripe":  (1040, 390, 190, 76, "Stripe", "결제/구독", "neutral"),
    "sentry":  (1040, 520, 190, 68, "Sentry", "에러", "pink"),
    "grafana": (1040, 650, 190, 68, "Grafana Cloud", "메트릭, 로그, 알람", "pink"),
    "discord": (1040, 790, 190, 68, "Discord", "P1 / P2 알람", "pink"),
}

ZONE = (420, 330, 520, 440, "EC2 #1 (Docker Compose)")

# (경로, 색, 라벨, 라벨좌표, 정렬, 양쪽화살표)
# 화살표가 서로 교차하지 않도록 좌표를 맞춰 둔 것이라 박스를 옮기면 같이 손봐야 한다.
EDGES = [
    ("M180,438 H219",      "neutral", "",                            None,       "middle", False),
    ("M550,438 H574",      "neutral", "",                            None,       "middle", False),
    ("M375,438 H444",      "neutral", "",                            None,       "middle", False),
    ("M640,504 L528,569",  "neutral", "JPA + Flyway",                (520, 543), "end",    False),
    ("M726,504 L710,569",  "neutral", "색인/조회",                    (762, 545), "middle", False),
    ("M110,400 V158 H464", "purple",  "① 직접 업로드 (presigned URL)", (300, 148), "middle", False),
    ("M650,158 H824",      "purple",  "③ 원본 읽기, 변형 쓰기",        (737, 148), "middle", True),
    ("M900,372 V202",      "purple",  "② 비동기 트리거",              (890, 300), "end",    False),
    ("M980,196 V400 H916", "purple",  "④ webhook",                   (992, 300), "start",  False),
    ("M1040,440 H916",     "neutral", "웹훅",                        (978, 430), "middle", False),
    ("M910,480 L1034,548", "pink",    "ERROR",                       (952, 528), "start",  False),
    ("M860,504 V674",      "pink",    "메트릭, 로그",                 (870, 600), "start",  False),
    ("M930,706 L1034,690", "pink",    "원격 쓰기",                    (982, 672), "middle", False),
    ("M1135,718 V784",     "pink",    "",                            None,       "middle", False),
]

ARIA = ("AT-CREW 시스템 아키텍처. 클라이언트 요청은 Cloudflare와 nginx를 거쳐 EC2의 Spring Boot 앱에 닿는다. "
        "이미지는 클라이언트가 R2에 직접 올린 뒤 Cloudflare Worker가 변환해 webhook으로 되돌려준다. "
        "관측 신호는 Alloy와 Sentry를 통해 Grafana Cloud와 Discord로 나간다.")


def _esc(s):
    return html.escape(s, quote=True)


def build_architecture(out_path):
    o = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" width="{W}" height="{H}" '
         f'role="img" aria-label="{_esc(ARIA)}">',
         '<title>AT-CREW 시스템 아키텍처</title>', '<defs>']

    # 화살표는 색마다 marker를 따로 둔다. context-stroke는 지원이 고르지 않다.
    for key in ("neutral", "blue", "purple", "pink"):
        o.append(f'<marker id="a-{key}" viewBox="0 0 10 10" refX="9" refY="5" '
                 f'markerWidth="7" markerHeight="7" orient="auto-start-reverse">'
                 f'<path d="M0,0 L10,5 L0,10 z" fill="{C[key]}"/></marker>')
    o.append('</defs>')
    o.append(f'<rect x="0" y="0" width="{W}" height="{H}" rx="12" fill="{C["page"]}"/>')
    o.append(f'<g transform="translate(0,{SHIFT})">')

    zx, zy, zw, zh, zlabel = ZONE
    o.append(f'<rect x="{zx}" y="{zy}" width="{zw}" height="{zh}" rx="14" fill="{C["zone_fill"]}" '
             f'stroke="{C["zone_stroke"]}" stroke-width="1.5" stroke-dasharray="7 5"/>')
    o.append(f'<text x="{zx+18}" y="{zy+26}" font-family="{FONT}" font-size="13" '
             f'font-weight="600" fill="{C["muted"]}">{_esc(zlabel)}</text>')

    # 박스가 화살표를 덮도록 선을 먼저 깐다.
    for d, key, label, lxy, anchor, dbl in EDGES:
        col = C[key]
        dash = ' stroke-dasharray="6 4"' if key == "pink" else ''
        start = f' marker-start="url(#a-{key})"' if dbl else ''
        o.append(f'<path d="{d}" fill="none" stroke="{col}" stroke-width="1.8"{dash} '
                 f'marker-end="url(#a-{key})"{start}/>')
        if label and lxy:
            o.append(f'<text x="{lxy[0]}" y="{lxy[1]}" font-family="{FONT}" font-size="12.5" '
                     f'text-anchor="{anchor}" fill="{col}">{_esc(label)}</text>')

    for name, (x, y, w, h, title, sub, key) in BOXES.items():
        col = C[key] if key != "neutral" else C["border"]
        o.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="10" fill="{C["card"]}" '
                 f'stroke="{col}" stroke-width="{2 if key != "neutral" else 1.4}"/>')
        cx = x + w / 2
        if name == "app":
            o.append(f'<text x="{cx}" y="{y+38}" font-family="{FONT}" font-size="16" font-weight="700" '
                     f'text-anchor="middle" fill="{C["text"]}">{_esc(title)}</text>')
            o.append(f'<text x="{cx}" y="{y+60}" font-family="{FONT}" font-size="12.5" '
                     f'text-anchor="middle" fill="{C["muted"]}">{_esc(sub)}</text>')
            o.append(f'<line x1="{x+24}" y1="{y+78}" x2="{x+w-24}" y2="{y+78}" '
                     f'stroke="{C["border"]}" stroke-width="1"/>')
            o.append(f'<text x="{cx}" y="{y+100}" font-family="{FONT}" font-size="12.5" font-weight="600" '
                     f'text-anchor="middle" fill="{C["blue"]}">10개 도메인 모듈 (Spring Modulith)</text>')
            o.append(f'<text x="{cx}" y="{y+118}" font-family="{FONT}" font-size="11.5" '
                     f'text-anchor="middle" fill="{C["muted"]}">모듈 간 통신은 공개 인터페이스와 이벤트로만</text>')
            continue
        ty = y + (h / 2 + 6) if not sub else y + h / 2 - 2
        o.append(f'<text x="{cx}" y="{ty}" font-family="{FONT}" font-size="14.5" font-weight="600" '
                 f'text-anchor="middle" fill="{C["text"]}">{_esc(title)}</text>')
        if sub:
            o.append(f'<text x="{cx}" y="{y+h/2+18}" font-family="{FONT}" font-size="12" '
                     f'text-anchor="middle" fill="{C["muted"]}">{_esc(sub)}</text>')

    o.append('</g></svg>')
    with open(out_path, "w", encoding="utf-8") as f:
        f.write("\n".join(o))
    return out_path


# --------------------------------------------------------------------- modules

# htmlLabels를 켜두면 라벨이 foreignObject 안의 HTML로 나오고, 거기 닫히지 않은 <br>가
# 남아 독립 SVG 파일이 XML로 파싱되지 않는다. 최상위에 꺼야 실제로 적용된다.
MERMAID_HTML = """<!doctype html><meta charset="utf-8"><body><div id="out"></div>
<script src="https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.min.js"></script>
<script>
mermaid.initialize({
  startOnLoad: false, theme: 'base', htmlLabels: false, securityLevel: 'strict',
  fontFamily: %(font)s,
  flowchart: { htmlLabels: false, useMaxWidth: false, padding: 12 },
  themeVariables: {
    background: '#ffffff', primaryColor: '#ffffff', primaryTextColor: '#1f2328',
    primaryBorderColor: '#d1d9e0', lineColor: '#6e7781',
    clusterBkg: '#f6f8fa', clusterBorder: '#d1d9e0', fontSize: '15px' }
});
mermaid.render('g', %(src)s).then(r => { document.getElementById('out').innerHTML = r.svg; });
</script>"""


def _find_chrome():
    env = os.environ.get("CHROME_BIN")
    if env:
        if os.path.exists(env) or shutil.which(env):
            return env
        sys.exit(f"CHROME_BIN이 가리키는 실행 파일을 찾을 수 없다: {env}")
    for c in CHROME_CANDIDATES:
        if os.path.exists(c):
            return c
        found = shutil.which(c)
        if found:
            return found
    sys.exit("Chrome을 찾지 못했다. CHROME_BIN 환경변수로 경로를 지정한다.")


def render_mermaid(mmd_path, out_path):
    chrome = _find_chrome()
    with open(mmd_path, encoding="utf-8") as f:
        src = f.read()

    with tempfile.TemporaryDirectory() as tmp:
        page = os.path.join(tmp, "render.html")
        with open(page, "w", encoding="utf-8") as f:
            f.write(MERMAID_HTML % {"font": json.dumps(FONT), "src": json.dumps(src)})
        dom = subprocess.run(
            [chrome, "--headless", "--disable-gpu", "--virtual-time-budget=20000",
             "--dump-dom", "file://" + page],
            capture_output=True, text=True, timeout=120,
        ).stdout

    m = re.search(r'(<svg[^>]*id="g"[\s\S]*?</svg>)', dom)
    if not m:
        sys.exit("mermaid 렌더 결과에서 SVG를 찾지 못했다. mmd 문법을 확인한다.")
    svg = m.group(1)

    if "xmlns=" not in svg[:250]:
        svg = svg.replace("<svg ", '<svg xmlns="http://www.w3.org/2000/svg" ', 1)

    # <img>로 불러올 때 고유 크기가 없으면 300x150으로 뭉개진다. viewBox에서 뽑아 박는다.
    vb = re.search(r'viewBox="0 0 ([\d.]+) ([\d.]+)"', svg)
    w, h = round(float(vb.group(1))), round(float(vb.group(2)))
    svg = re.sub(r'\swidth="[^"]*"', f' width="{w}"', svg, count=1)
    if " height=" not in svg[:300]:
        svg = svg.replace(f'width="{w}"', f'width="{w}" height="{h}"', 1)
    svg = re.sub(r'\sstyle="max-width:[^"]*"', "", svg, count=1)
    svg = re.sub(r"<br\s*>", "<br/>", svg)

    if "foreignObject" in svg:
        sys.exit("foreignObject가 남았다. htmlLabels가 적용되지 않은 것이라 독립 SVG로 못 쓴다.")

    with open(out_path, "w", encoding="utf-8") as f:
        f.write(svg)
    return out_path


# ------------------------------------------------------------------------ main

def main():
    targets = sys.argv[1:] or ["architecture", "modules"]
    unknown = [t for t in targets if t not in ("architecture", "modules")]
    if unknown:
        sys.exit(f"모르는 대상: {', '.join(unknown)} (architecture | modules)")

    if "architecture" in targets:
        p = build_architecture(os.path.join(ASSETS, "architecture.svg"))
        print(f"생성 {os.path.relpath(p, ROOT)} ({os.path.getsize(p)} bytes)")
    if "modules" in targets:
        p = render_mermaid(os.path.join(ASSETS, "modules.mmd"),
                           os.path.join(ASSETS, "modules.svg"))
        print(f"생성 {os.path.relpath(p, ROOT)} ({os.path.getsize(p)} bytes)")


if __name__ == "__main__":
    main()
