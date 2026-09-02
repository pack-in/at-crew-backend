// k6 부하 시나리오 — 한계 처리량 측정용. 읽기 전용 엔드포인트만 때린다.
// 설계: docs/operations/baseline/README.md
//
// 실행(부하는 반드시 로컬에서 만든다. 서버에 k6를 설치하면 부하 생성기가 측정 대상의
// CPU·메모리를 먹어서 값이 오염된다):
//
//   # 1) 오리진 직접 — SSH 터널로 앱에 바로 붙는다. Cloudflare 레이트리밋에 걸리지 않고
//   #    보안그룹·Cloudflare 설정을 전혀 건드리지 않는다.
//   ssh -i ~/.ssh/<키페어>.pem -N -L 18080:127.0.0.1:8080 ec2-user@<EC2 #1> &
//   BASE_URL=http://127.0.0.1:18080 k6 run scripts/baseline/load-test.js
//
//   # 2) 엣지 포함 — 실사용자와 같은 경로. Cloudflare에 측정 IP 레이트리밋 예외가 먼저 필요하다.
//   BASE_URL=https://api.at-crew.com k6 run scripts/baseline/load-test.js
//
// 경고
// - 앱·MariaDB·(통합 후)Elasticsearch가 한 인스턴스의 메모리를 나눠 쓰고 **스왑이 없다.**
//   메모리가 모자라면 OOM 킬러가 컨테이너를 죽인다. 백업이 성공한 직후에 수행한다.
// - t4g.medium은 버스터블이다. CPU 크레딧이 소진되면 처리량이 급락하므로, 측정 중
//   CloudWatch `CPUCreditBalance`를 같이 봐야 한다. 크레딧이 남은 채로 꺾였는지,
//   크레딧이 떨어져서 꺾였는지에 따라 "한계 처리량"의 의미가 완전히 달라진다.
// - 쓰기 엔드포인트는 하나도 넣지 않는다. 아래 목록은 전부 SecurityConfig에서
//   permitAll로 선언된 GET이다.

import http from 'k6/http';
import { check } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://127.0.0.1:18080';
// 최대 도달 RPS. 꺾이는 지점을 못 찾으면 올려서 다시 돈다.
const PEAK_RPS = Number(__ENV.PEAK_RPS || 200);
// 한 단계를 유지하는 시간. 짧으면 버스터블 크레딧 덕에 실제보다 좋게 나온다.
const STAGE = __ENV.STAGE || '1m';

// 엔드포인트별로 나눠 봐야 병목이 MariaDB인지 Elasticsearch인지 구분된다.
const latency = new Trend('ep_latency', true);
const failures = new Rate('ep_failed');

const ENDPOINTS = [
  { name: 'community_artworks',   path: '/api/community/artworks?size=20',      backend: 'mariadb' },
  { name: 'community_authors',    path: '/api/community/authors?size=20',       backend: 'mariadb' },
  { name: 'community_banners',    path: '/api/community/banners',               backend: 'mariadb' },
  { name: 'recruit_job_postings', path: '/api/recruit/job-postings?size=20',    backend: 'mariadb' },
  { name: 'search',               path: '/api/search?q=일러스트&size=20',        backend: 'elasticsearch' },
  { name: 'billing_catalog',      path: '/api/billing/catalog',                 backend: 'app' },
];

export const options = {
  discardResponseBodies: true,
  scenarios: {
    // 도착률(arrival rate) 기반이다. VU 기반으로 하면 서버가 느려질수록 부하도 같이
    // 줄어서 한계점이 흐려진다 — 서버 상태와 무관하게 초당 요청 수를 밀어 넣어야
    // 꺾이는 지점이 드러난다.
    ramp: {
      executor: 'ramping-arrival-rate',
      startRate: 5,
      timeUnit: '1s',
      preAllocatedVUs: 50,
      maxVUs: 500,
      stages: [
        { target: 5,                       duration: '30s' },
        { target: Math.round(PEAK_RPS * 0.1),  duration: STAGE },
        { target: Math.round(PEAK_RPS * 0.25), duration: STAGE },
        { target: Math.round(PEAK_RPS * 0.5),  duration: STAGE },
        { target: Math.round(PEAK_RPS * 0.75), duration: STAGE },
        { target: PEAK_RPS,                    duration: STAGE },
        { target: 0,                           duration: '30s' },
      ],
    },
  },
  // 중단 조건을 미리 고정한다. 임계를 넘으면 k6가 테스트를 즉시 끝낸다 —
  // "어디까지 버티나"를 보려다 서비스를 정지시키는 걸 막는 장치다.
  thresholds: {
    'http_req_failed':   [{ threshold: 'rate<0.01', abortOnFail: true, delayAbortEval: '20s' }],
    'http_req_duration': [{ threshold: 'p(95)<3000', abortOnFail: true, delayAbortEval: '20s' }],
  },
};

export default function () {
  const ep = ENDPOINTS[Math.floor(Math.random() * ENDPOINTS.length)];
  const res = http.get(`${BASE_URL}${ep.path}`, {
    tags: { endpoint: ep.name, backend: ep.backend },
    timeout: '10s',
  });
  latency.add(res.timings.duration, { endpoint: ep.name, backend: ep.backend });
  failures.add(res.status >= 500 || res.status === 0, { endpoint: ep.name });
  check(res, { 'status < 500': (r) => r.status > 0 && r.status < 500 });
}

export function handleSummary(data) {
  // 결과 문서에 그대로 붙일 수 있게 마크다운 표로 낸다.
  const m = data.metrics;
  const g = (k, f) => (m[k] && m[k].values[f] !== undefined ? m[k].values[f].toFixed(1) : '측정 실패');
  const lines = [
    '| 지표 | 값 |',
    '|---|---|',
    `| 총 요청 수 | ${m.http_reqs ? m.http_reqs.values.count : '측정 실패'} |`,
    `| 평균 RPS | ${m.http_reqs ? m.http_reqs.values.rate.toFixed(1) : '측정 실패'} |`,
    `| 응답시간 p50 (ms) | ${g('http_req_duration', 'med')} |`,
    `| 응답시간 p95 (ms) | ${g('http_req_duration', 'p(95)')} |`,
    `| 응답시간 p99 (ms) | ${g('http_req_duration', 'p(99)')} |`,
    `| 응답시간 최대 (ms) | ${g('http_req_duration', 'max')} |`,
    `| 실패율 | ${m.http_req_failed ? (m.http_req_failed.values.rate * 100).toFixed(2) + '%' : '측정 실패'} |`,
  ];
  return {
    stdout: '\n' + lines.join('\n') + '\n\n(엔드포인트별 분해는 --out json 결과의 ep_latency 태그를 쓴다)\n',
    'load-test-summary.json': JSON.stringify(data, null, 2),
  };
}
