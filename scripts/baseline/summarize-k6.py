#!/usr/bin/env python3
"""k6 원본 JSON을 비교 가능한 CSV와 마크다운 표로 집계한다.

왜 필요한가: k6가 내는 요약(`handleSummary`)은 측정 전체의 단일 분위수라서 "어느 RPS에서
꺾였는가"를 알 수 없다. 꺾이는 지점이 곧 한계 처리량이므로 구간별로 다시 집계해야 한다.
집계 방식이 측정마다 달라지면 비교가 안 되므로 이 스크립트를 정본으로 둔다.

원본 JSON은 레포에 넣지 않는다 — 한 번 실행에 수백 MB에서 1 GB까지 나온다.
이 스크립트로 만든 CSV(수 KB)만 `docs/operations/baseline/data/`에 남긴다.

실행:
  k6 run --out json=raw.json scripts/baseline/load-test.js
  ./scripts/baseline/summarize-k6.py raw.json --label 100k \\
      --out docs/operations/baseline/data/2026-09-02-loadtest-100k.csv
"""
import argparse, collections, json, sys


def percentile(values, p):
    """정렬된 표본에서 백분위 값. k6의 summaryTrendStats와 같은 nearest-rank 방식."""
    if not values:
        return float("nan")
    s = sorted(values)
    return s[min(len(s) - 1, int(p * len(s)))]


def parse(path):
    """k6 JSON 스트림에서 http_req_duration 포인트만 뽑는다.

    파일이 1 GB에 달하므로 통째로 읽지 않고 한 줄씩 처리한다.
    """
    for line in open(path, encoding="utf-8"):
        try:
            d = json.loads(line)
        except ValueError:
            continue
        if d.get("type") != "Point" or d.get("metric") != "http_req_duration":
            continue
        data = d["data"]
        t = data["time"][11:19]
        h, m, s = t.split(":")
        yield int(h) * 3600 + int(m) * 60 + int(s), data.get("tags", {}), data["value"]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("raw", help="k6 --out json 결과 파일")
    ap.add_argument("--out", required=True, help="구간별 시계열 CSV 출력 경로")
    ap.add_argument("--label", default="", help="측정 조건 꼬리표(예: 100k). CSV 첫 열에 들어간다")
    ap.add_argument("--bucket", type=int, default=15, help="집계 구간(초). 기본 15")
    ap.add_argument("--endpoint-out", help="엔드포인트별 CSV 출력 경로(선택)")
    args = ap.parse_args()

    buckets = collections.defaultdict(list)
    by_ep = collections.defaultdict(list)
    for sec, tags, v in parse(args.raw):
        buckets[sec // args.bucket * args.bucket].append(v)
        by_ep[(tags.get("endpoint", "?"), tags.get("backend", "?"))].append(v)
    if not buckets:
        sys.exit("http_req_duration 포인트가 없다 — --out json 결과 파일이 맞는지 확인할 것")

    t0 = min(buckets)
    with open(args.out, "w", encoding="utf-8") as f:
        f.write("label,elapsed_s,rps,p50_ms,p95_ms,p99_ms,max_ms,n\n")
        for b in sorted(buckets):
            a = buckets[b]
            f.write("%s,%d,%.1f,%.1f,%.1f,%.1f,%.1f,%d\n" % (
                args.label, b - t0, len(a) / args.bucket,
                percentile(a, .50), percentile(a, .95), percentile(a, .99), max(a), len(a)))

    if args.endpoint_out:
        with open(args.endpoint_out, "w", encoding="utf-8") as f:
            f.write("label,endpoint,backend,p50_ms,p95_ms,p99_ms,n\n")
            for (ep, be), a in sorted(by_ep.items(), key=lambda kv: -percentile(kv[1], .5)):
                f.write("%s,%s,%s,%.1f,%.1f,%.1f,%d\n" % (
                    args.label, ep, be,
                    percentile(a, .50), percentile(a, .95), percentile(a, .99), len(a)))

    # 문서에 붙일 수 있게 표로도 낸다.
    print("| 경과(초) | RPS | p50(ms) | p95(ms) | p99(ms) |")
    print("|---|---|---|---|---|")
    for b in sorted(buckets):
        a = buckets[b]
        print("| %d | %.1f | %.1f | %.1f | %.1f |" % (
            b - t0, len(a) / args.bucket, percentile(a, .50), percentile(a, .95), percentile(a, .99)))
    print("\n집계 대상 %d건, 구간 %d초, CSV: %s" % (sum(len(v) for v in buckets.values()), args.bucket, args.out))


if __name__ == "__main__":
    main()
