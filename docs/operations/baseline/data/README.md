# 측정 원본 데이터

측정 결과 문서(`../*.md`)의 표는 사람이 읽으라고 요약한 것이고, **비교와 재분석의 근거는
이 폴더의 CSV다.** 문서의 표만 남기면 나중에 "그때 40 RPS 구간은 어땠나"를 다시 볼 수 없다.

## 파일

| 파일 | 내용 |
|---|---|
| `<날짜>-loadtest-<조건>.csv` | 15초 구간별 RPS·p50·p95·p99·max·표본수 |
| `<날짜>-loadtest-<조건>-endpoints.csv` | 엔드포인트별 p50·p95·p99·표본수 |
| `<날짜>-resource-<조건>.csv` | 2초 간격 서버 자원 샘플 — 메모리 여유, 로드애버리지, HikariCP active, 힙, 프로세스 RSS |

`<조건>`은 측정을 가르는 변수를 쓴다. 2026-09-02 회차는 데이터 규모가 변수였으므로
`empty-db`(85행) / `10k`(작품 1만) / `100k`(작품 10만)다.

## 원본 k6 JSON을 두지 않는 이유

`k6 run --out json=`이 내는 파일은 한 번 실행에 **수백 MB에서 1 GB**다(2026-09-02 빈 DB
측정은 849 MB). 저장소에 넣을 크기가 아니고, 넣어도 그대로는 아무도 읽지 못한다.

대신 집계 스크립트를 정본으로 둔다.

```bash
k6 run --out json=/tmp/raw.json scripts/baseline/load-test.js
./scripts/baseline/summarize-k6.py /tmp/raw.json --label <조건> \
    --out docs/operations/baseline/data/$(date -u +%F)-loadtest-<조건>.csv \
    --endpoint-out docs/operations/baseline/data/$(date -u +%F)-loadtest-<조건>-endpoints.csv
```

**집계는 반드시 이 스크립트로 한다.** 분위수 계산 방식이 회차마다 달라지면 숫자를 나란히
놓는 것 자체가 의미를 잃는다. k6 자신의 요약(`handleSummary`)은 측정 전체의 단일 분위수라
"어느 RPS에서 꺾였는가"를 알 수 없어 비교에 쓸 수 없다.

## 비교하는 법

```bash
# 같은 조건의 회차를 나란히 본다
column -s, -t docs/operations/baseline/data/*-loadtest-100k.csv

# 꺾이는 지점만 추린다 — p95가 처음 임계를 넘는 구간
awk -F, 'NR==1 || $5>500' docs/operations/baseline/data/2026-09-02-loadtest-100k.csv
```

**조건이 다른 회차를 비교하지 않는다.** 2026-09-02 측정이 보여준 것이 정확히 이 함정이다 —
같은 코드·같은 사양인데 데이터 규모만 달라서 950 RPS와 15 RPS가 나왔다. `label` 열이
그래서 있다.
