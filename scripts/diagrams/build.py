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
    "nginx":   (450, 400, 100, 76, "nginx", ":80, :443", "neutral"),
    "app":     (580, 372, 330, 132, "Spring Boot 4", ":8080 API, :8081 관리", "blue"),
    "mariadb": (450, 575, 150, 64, "MariaDB", "Flyway V1–V35", "neutral"),
    "es":      (625, 575, 165, 64, "Elasticsearch", "조회 전용 색인", "neutral"),
    "alloy":   (790, 680, 140, 64, "Grafana Alloy", "", "pink"),
    "r2":      (470, 120, 180, 76, "Cloudflare R2", "원본, 변형본", "purple"),
    "worker":  (830, 120, 190, 76, "Cloudflare Worker", "이미지 변환", "purple"),
    "stripe":  (1040, 390, 190, 76, "Stripe", "결제/구독", "neutral"),
    "sentry":  (1040, 520, 190, 68, "Sentry", "에러", "pink"),
    "grafana": (1040, 650, 190, 68, "Grafana Cloud", "메트릭, 로그, 알람", "pink"),
    "discord": (1040, 790, 190, 68, "Discord", "P1 / P2 알람", "pink"),
}

ZONE = (420, 330, 520, 440, "EC2 (Private Subnet, Docker Compose)")

# (경로, 색, 라벨, 라벨좌표, 정렬, 양쪽화살표)
# 화살표가 서로 교차하지 않도록 좌표를 맞춰 둔 것이라 박스를 옮기면 같이 손봐야 한다.
EDGES = [
    ("M180,438 H219",      "neutral", "",                            None,       "middle", False),
    ("M550,438 H574",      "neutral", "",                            None,       "middle", False),
    ("M375,438 H444",      "neutral", "Tunnel",                      (410, 428), "middle", False),
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

ARIA = ("AT-CREW 시스템 아키텍처. 클라이언트 요청은 Cloudflare에서 Cloudflare Tunnel을 타고 프라이빗 서브넷의 "
        "nginx를 거쳐 Spring Boot 앱에 닿는다. 인스턴스에 열린 인바운드 포트는 없다. "
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


# ------------------------------------------------------------------ infra

# 인프라 구성도. architecture.svg가 "요청이 어떤 컴포넌트를 지나는가"를 그린다면 이쪽은
# "그 컴포넌트가 어느 네트워크 경계 안에 있는가"를 그린다. #110으로 전용 VPC가 생기면서
# 서브넷·라우팅 경계가 보안 설명의 핵심이 됐는데 논리 그림에는 그게 드러나지 않는다.
#
# 공개 저장소이므로 인스턴스 ID·탄력적 IP·VPC/보안그룹 ID는 넣지 않는다(deploy/README.md 원칙).
# 서브넷 CIDR은 RFC1918 사설 대역이라 그대로 둔다 — 없으면 그림이 읽히지 않는다.

IW, IH = 1340, 700

# (x, y, w, h, 라벨, 색키, 점선여부)
IZONES = [
    (430,  40, 870, 620, "AWS ap-northeast-2 (서울)",        "muted",   True),
    (462,  86, 806, 552, "at-crew VPC (10.20.0.0/16)",      "purple",  False),
    (494, 196, 742, 420, "가용 영역 ap-northeast-2a",         "muted",   True),
    (520, 306, 250, 130, "Public subnet (10.20.0.0/24)",    "blue",    False),
    (812, 246, 396, 340, "Private subnet (10.20.1.0/24)",   "pink",    False),
]

IBOXES = {
    "users":   (40, 250, 130, 66, "사용자", "웹, 앱", "neutral"),
    "cf":      (206, 240, 168, 88, "Cloudflare", "DNS, WAF, Tunnel", "purple"),
    "ops":     (40, 440, 150, 76, "GitHub Actions", "배포 워크플로", "neutral"),
    "ssm":     (222, 448, 152, 62, "AWS SSM", "IAM 통제", "blue"),
    "igw":     (586, 116, 118, 56, "IGW", "", "neutral"),
    "net":     (760, 110, 250, 62, "인터넷 아웃바운드", "R2, Stripe, Grafana", "neutral"),
    "nat":     (546, 352, 198, 62, "NAT 인스턴스", "", "neutral"),
    "server":  (840, 296, 340, 262, "앱 서버 (t4g.medium)", "", "blue"),
}

# 앱 서버 카드 안에 쌓아 그릴 줄. (텍스트, 색키, 굵게)
SERVER_LINES = [
    ("cloudflared — 터널 종단", "purple", False),
    ("nginx :80, :443", "neutral", False),
    ("─", "border", False),
    ("Docker Compose", "muted", True),
    ("app :8080 / :8081", "blue", False),
    ("mariadb, elasticsearch", "neutral", False),
    ("alloy (관측)", "pink", False),
]

IEDGES = [
    ("M170,283 H200",          "neutral", "",                    None,        "middle", False),
    ("M374,284 H1010 V292",    "purple",  "Tunnel (아웃바운드 연결)", (620, 274), "middle", True),
    ("M190,478 H216",          "neutral", "",                    None,        "middle", False),
    ("M374,479 H836",          "blue",    "SSM 원격 명령 (SSH·개방 포트 없음)", (620, 469), "middle", False),
    ("M838,383 H750",          "neutral", "아웃바운드",             (794, 373),  "middle", False),
    ("M645,352 V178",          "neutral", "",                    None,        "middle", False),
    ("M704,144 H752",          "neutral", "",                    None,        "middle", False),
]

INOTE = ("미구성(#110 Phase 1·2 잔여): 두 번째 가용 영역, ALB, MariaDB Replica. "
         "현재는 단일 AZ·단일 인스턴스이며 인바운드 포트를 열지 않는다.")

INFRA_ARIA = ("AT-CREW 인프라 구성도. 서울 리전의 전용 VPC 안에 가용 영역 하나가 있고, "
              "퍼블릭 서브넷에는 NAT 인스턴스가, 프라이빗 서브넷에는 앱 서버가 있다. "
              "사용자 트래픽은 Cloudflare Tunnel로, 배포는 AWS SSM으로 들어오며 인바운드 포트는 없다.")


def build_infra(out_path, *, w=None, h=None, zones=None, edges=None, boxes=None,
                card_lines=None, note=None, aria=None, title="AT-CREW 인프라 구성도"):
    """인프라 구성도를 그린다.

    인자를 주지 않으면 현재 구성(I* 상수)을 그린다. HA 청사진처럼 다른 구성을 그릴 때만
    데이터를 넘긴다 — 렌더 로직은 하나만 두고 데이터만 갈아끼운다.
    """
    w = w or IW
    h = h or IH
    zones = zones if zones is not None else IZONES
    edges = edges if edges is not None else IEDGES
    boxes = boxes if boxes is not None else IBOXES
    card_lines = card_lines if card_lines is not None else {"server": SERVER_LINES}
    note = note if note is not None else INOTE
    aria = aria if aria is not None else INFRA_ARIA

    o = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w} {h}" width="{w}" height="{h}" '
         f'role="img" aria-label="{_esc(aria)}">',
         f'<title>{_esc(title)}</title>', '<defs>']
    for key in ("neutral", "blue", "purple", "pink"):
        o.append(f'<marker id="i-{key}" viewBox="0 0 10 10" refX="9" refY="5" '
                 f'markerWidth="7" markerHeight="7" orient="auto-start-reverse">'
                 f'<path d="M0,0 L10,5 L0,10 z" fill="{C[key]}"/></marker>')
    o.append('</defs>')
    o.append(f'<rect x="0" y="0" width="{w}" height="{h}" rx="12" fill="{C["page"]}"/>')

    for zx, zy, zw, zh, label, key, dashed in zones:
        stroke = C[key] if key in C else C["zone_stroke"]
        dash = ' stroke-dasharray="7 5"' if dashed else ''
        o.append(f'<rect x="{zx}" y="{zy}" width="{zw}" height="{zh}" rx="12" fill="none" '
                 f'stroke="{stroke}" stroke-width="1.5"{dash}/>')
        o.append(f'<text x="{zx+16}" y="{zy+24}" font-family="{FONT}" font-size="12.5" '
                 f'font-weight="600" fill="{stroke}">{_esc(label)}</text>')

    for d, key, label, lxy, anchor, dbl in edges:
        col = C[key]
        start = f' marker-start="url(#i-{key})"' if dbl else ''
        o.append(f'<path d="{d}" fill="none" stroke="{col}" stroke-width="1.8" '
                 f'marker-end="url(#i-{key})"{start}/>')
        if label and lxy:
            o.append(f'<text x="{lxy[0]}" y="{lxy[1]}" font-family="{FONT}" font-size="12.5" '
                     f'text-anchor="{anchor}" fill="{col}">{_esc(label)}</text>')

    for name, (x, y, bw, bh, btitle, sub, key) in boxes.items():
        col = C[key] if key != "neutral" else C["border"]
        o.append(f'<rect x="{x}" y="{y}" width="{bw}" height="{bh}" rx="10" fill="{C["card"]}" '
                 f'stroke="{col}" stroke-width="{2 if key != "neutral" else 1.4}"/>')
        cx = x + bw / 2
        if name in card_lines:
            o.append(f'<text x="{cx}" y="{y+30}" font-family="{FONT}" font-size="14.5" font-weight="700" '
                     f'text-anchor="middle" fill="{C["text"]}">{_esc(btitle)}</text>')
            ly = y + 58
            for text, ckey, bold in card_lines[name]:
                if text == "─":
                    o.append(f'<line x1="{x+26}" y1="{ly-8}" x2="{x+bw-26}" y2="{ly-8}" '
                             f'stroke="{C["border"]}" stroke-width="1"/>')
                    ly += 12
                    continue
                fill = C.get(ckey, C["text"])
                weight = "600" if bold else "400"
                o.append(f'<text x="{cx}" y="{ly}" font-family="{FONT}" font-size="12.5" '
                         f'font-weight="{weight}" text-anchor="middle" fill="{fill}">{_esc(text)}</text>')
                ly += 26
            continue
        ty = y + (bh / 2 + 6) if not sub else y + bh / 2 - 2
        o.append(f'<text x="{cx}" y="{ty}" font-family="{FONT}" font-size="14" font-weight="600" '
                 f'text-anchor="middle" fill="{C["text"]}">{_esc(btitle)}</text>')
        if sub:
            o.append(f'<text x="{cx}" y="{y+bh/2+18}" font-family="{FONT}" font-size="11.5" '
                     f'text-anchor="middle" fill="{C["muted"]}">{_esc(sub)}</text>')

    o.append(f'<text x="40" y="{h-24}" font-family="{FONT}" font-size="12" '
             f'fill="{C["muted"]}">{_esc(note)}</text>')
    o.append('</svg>')
    with open(out_path, "w", encoding="utf-8") as f:
        f.write("\n".join(o))
    return out_path

# --------------------------------------------------------------- infra (HA 청사진)

# 고가용성 전환 후의 구성도. 지금은 만들지 않고 코드로만 정의돼 있다
# (deploy/terraform/ha-blueprint.tf, var.ha_enabled = false).
# infra.svg가 "지금 무엇이 있는가"라면 이쪽은 "무엇으로 바뀔 수 있는가"다.
# 근거와 전환 트리거는 docs/design/ha-expansion-path.md에 있다.

HW, HH = 1340, 830

HZONES = [
    (430,  40, 870, 750, "AWS ap-northeast-2 (서울)",       "muted",  True),
    (462,  86, 806, 682, "at-crew VPC (10.20.0.0/16)",     "purple", False),
    (494, 186, 742, 268, "가용 영역 ap-northeast-2a",        "muted",  True),
    (760, 220, 452, 220, "Private subnet (10.20.1.0/24)",  "pink",   False),
    (516, 340, 220,  96, "Public subnet (10.20.0.0/24)",   "blue",   False),
    (494, 478, 742, 268, "가용 영역 ap-northeast-2c",        "muted",  True),
    (760, 512, 452, 220, "Private subnet (10.20.2.0/24)",  "pink",   False),
]

HBOXES = {
    "users":   (40, 250, 130, 66, "사용자", "웹, 앱", "neutral"),
    "cf":      (206, 236, 168, 92, "Cloudflare", "DNS, WAF, Tunnel", "purple"),
    "ops":     (40, 450, 150, 76, "GitHub Actions", "배포 워크플로", "neutral"),
    "ssm":     (222, 458, 152, 62, "AWS SSM", "IAM 통제", "blue"),
    "igw":     (586, 116, 118, 52, "IGW", "", "neutral"),
    "net":     (760, 112, 250, 56, "인터넷 아웃바운드", "R2, Stripe, Grafana", "neutral"),
    "nat":     (534, 374, 184, 52, "NAT 인스턴스", "", "neutral"),
    "server1": (790, 254, 202, 178, "앱 #1 (t4g.medium)", "", "blue"),
    "server2": (790, 546, 202, 178, "앱 #2 (t4g.medium)", "", "blue"),
    "rds1":    (1016, 292, 176, 80, "RDS Primary", "MariaDB Multi-AZ", "purple"),
    "rds2":    (1016, 584, 176, 80, "RDS Standby", "자동 전환 60~120초", "muted"),
}

# mariadb가 RDS로 빠져 앱 서버 카드에서 사라진다 — 그만큼 인스턴스 메모리가 비워진다.
HA_SERVER_LINES = [
    ("cloudflared — 터널 replica", "purple", False),
    ("─", "border", False),
    ("Docker Compose", "muted", True),
    ("app :8080 / :8081", "blue", False),
    ("elasticsearch, alloy", "neutral", False),
]

HEDGES = [
    ("M170,283 H200",                 "neutral", "",                 None,        "middle", False),
    ("M374,266 H510 V300 H784",       "purple",  "Tunnel replica",   (628, 290),  "middle", False),
    ("M374,298 H486 V612 H784",       "purple",  "Tunnel replica",   (628, 602),  "middle", False),
    ("M374,478 H476 V464 H886 V436", "blue",    "SSM 원격 명령",      (640, 448),  "middle", False),
    ("M374,500 H476 V690 H784",       "blue",    "",                 None,        "middle", False),
    ("M994,336 H1012",                "neutral", "",                 None,        "middle", False),
    ("M996,624 H1232 V336 H1196",     "neutral", "",                 None,        "middle", False),
    ("M1104,374 V582",                "pink",    "동기 복제",          (1114, 470), "start",  True),
    ("M788,400 H722",                 "neutral", "",                 None,        "middle", False),
    ("M626,372 V172",                 "neutral", "아웃바운드",         (638, 258),  "start",  False),
    ("M704,142 H752",                 "neutral", "",                 None,        "middle", False),
]

HNOTE = ("청사진 — 아직 만들지 않았다(deploy/terraform/ha-blueprint.tf, ha_enabled = false). "
         "앱은 Tunnel replica로, DB는 RDS Multi-AZ로 자동 페일오버한다. 전환 트리거는 "
         "docs/design/ha-expansion-path.md 참고.")

HA_ARIA = ("AT-CREW 고가용성 청사진. 서울 리전 VPC 안에 가용 영역 두 개가 있고 각각의 "
           "프라이빗 서브넷에 앱 인스턴스가 하나씩 있다. Cloudflare Tunnel replica가 두 앱으로 "
           "트래픽을 보내고, RDS Multi-AZ가 Primary와 Standby를 동기 복제해 자동 전환한다.")


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
    known = ("architecture", "infra", "infra-ha", "modules")
    targets = sys.argv[1:] or list(known)
    unknown = [t for t in targets if t not in known]
    if unknown:
        sys.exit(f"모르는 대상: {', '.join(unknown)} ({' | '.join(known)})")

    if "infra" in targets:
        p = build_infra(os.path.join(ASSETS, "infra.svg"))
        print(f"생성 {p} ({os.path.getsize(p)} bytes)")

    if "infra-ha" in targets:
        p = build_infra(os.path.join(ASSETS, "infra-ha.svg"),
                        w=HW, h=HH, zones=HZONES, edges=HEDGES, boxes=HBOXES,
                        card_lines={"server1": HA_SERVER_LINES, "server2": HA_SERVER_LINES},
                        note=HNOTE, aria=HA_ARIA, title="AT-CREW 고가용성 청사진")
        print(f"생성 {p} ({os.path.getsize(p)} bytes)")

    if "architecture" in targets:
        p = build_architecture(os.path.join(ASSETS, "architecture.svg"))
        print(f"생성 {os.path.relpath(p, ROOT)} ({os.path.getsize(p)} bytes)")
    if "modules" in targets:
        p = render_mermaid(os.path.join(ASSETS, "modules.mmd"),
                           os.path.join(ASSETS, "modules.svg"))
        print(f"생성 {os.path.relpath(p, ROOT)} ({os.path.getsize(p)} bytes)")


if __name__ == "__main__":
    main()
