# 다음 세션 시작 가이드 (2026-08-04 점검 시점)

> 이 문서는 세션 인수인계용 체크리스트다. 장기 로드맵 전체는 `docs/roadmap.md`가 정본이고,
> 이 문서는 "지금 당장 뭐부터 볼지"만 정리한다. 작업 완료 후 이 파일은 삭제해도 된다.

## 2026-08-12 진행 상황 (배포 마무리 점검 + 비밀번호 재설정 구현)

**병렬 워크트리(`turban`/`danhandev-feature-launch-milestone-mvp`) 발견**: 이 세션과 별개로 "출시 마일스톤" 전체(billing/portfolio/i18n/설정)가 진행 중이었음을 뒤늦게 확인. 이 세션에서 먼저 만들었던 `payment` 모듈 설계는 전부 폐기하고 `billing` 모듈(더 진척된 쪽)로 통합하기로 결정 — 상세는 `docs/design/billing-module-design.md`(다른 워크트리) 참고.

**turban 병합 계획 검토 완료(2026-08-13) — 아직 병합할 단계 아님**:
- turban은 main보다 **27개 커밋 뒤처짐**(merge-base `299ba5b`). 그중 **`0aa5c39`(prod HTTPS 강제 제거 — `NoClassDefFoundError`로 앱이 기동 자체를 못 했던 크래시 수정)가 turban에 없음** — 지금 상태로 배포하면 재발한다. turban을 main으로 rebase/merge할 때 이 커밋 흡수가 최우선.
- **Flyway 번호 충돌 확정**: main V16(`member_adult_content_visible`)·V17(`password_reset_tokens`)과 turban V16(`billing_subscriptions`)·V17(`portfolio_schema`)·V18(`artwork_portfolio_inclusion`)이 같은 번호에 다른 내용으로 충돌. 병합 시 turban V16~V18 → V19~V21로 재번호 필요.
- `SecurityConfig.java`/`application.yml`/`roadmap.md`/`CLAUDE.md`는 양쪽 다 수정해서 수동 병합 필요.
- turban은 2026-08-13 기준 REST Docs 에러 스펙 확장 작업이 커밋 안 된 채 진행 중 — 그 작업부터 마무리·커밋시키고 rebase 진행할 것.

**✅ 해결됨 — prod 521 복구**: root가 Cloudflare SSL/TLS 모드를 Flexible로 되돌려 `api.at-crew.com` 정상화 확인(2026-08-13). 다만 **Cloudflare DNS/SSL 편집 권한 요청은 아직 pending** — 다음에 같은 문제가 재발하면 또 root한테 요청해야 하는 상태 그대로임.

**PASS 본인 인증 영구 폐기**(사용자 결정, 2026-08-12) — 법인이 미국 기준으로 재편되며 한국 전용 서비스인 PASS 연동을 하지 않기로 확정. 로드맵 §1 갱신 완료. 성인 콘텐츠는 표시 토글(OFF/ON+항상 blur) 2단계가 영구 최종 형태. 남는 범위는 기업 인증(수동 심사)뿐 — `Company.verified` 스텁 필드에 실제 상태를 연결하는 작업이 다음 착수 후보(turban과 안 겹침, 확인됨).

**CI/CD 자동 배포 구축**: `.github/workflows/deploy.yml` 신규 — main push 시 빌드+테스트 통과 후 Docker Hub push(커밋 SHA 태그, latest 태그 동시 갱신) + EC2 SSH 재기동까지 자동화. **사용자가 GitHub 저장소 Settings에 Secrets 4개를 직접 등록해야 동작함**(`DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN`, `APP_HOST`, `EC2_SSH_KEY`) — 아직 미등록이라 워크플로우는 존재하지만 실행하면 실패한다.

**비밀번호 재설정 + 이메일 발송 인프라 구현 완료**: `docs/design/auth-email-custom-redesign.md` §7이 "별도 설계 필요" 초안이었던 부분을 Figma로 직접 확인(토큰 링크 방식, TTL 1시간 — 초안의 30분에서 정정)해 확정·구현. `com.atcrew.common.mail`(MailSender 포트 + Resend 어댑터, Flyway V17). `./gradlew build` 전체 그린 확인(354개 중 pre-existing flaky 2건은 격리 재실행 시 통과 — `SearchApiDocTest`/`EventPublicationRegistryTest`, 공유 Testcontainer 종료 경쟁 조건, 이 세션 변경과 무관). **root에게 `RESEND_API_KEY` 발급 요청 필요**(자체 가입 가능, 커스텀 도메인 발신 원하면 Cloudflare DNS 편집 권한도 필요 — 기존 pending 요청과 동일 건).

**prod 필수값 fail-fast 검증 추가**: `WORKER_CALLBACK_SECRET`/`ARTWORK_INTERNAL_SECRET`/`SEARCH_INTERNAL_SECRET`/R2 자격증명 4개가 값 누락 시 `dev-internal-secret`/`dummy-*`로 조용히 기동되던 문제를 `application-prod.yml`에서 fail-fast로 전환.

**다음 세션 우선순위**: (1) GitHub Secrets 4개 등록(사용자 작업) (2) PM 답변 받으면 RESEND_API_KEY 확보 (3) turban이 REST Docs 작업 마무리·커밋하면 rebase 진행(위 병합 계획 참고) (4) 기업 인증(로드맵 1번, PASS 폐기로 축소된 범위) 설계 착수 후보.

## 2026-08-07 진행 상황 (🎉 첫 배포 성공)

**EC2 #1에서 앱+MariaDB가 실제로 돌아가고, nginx를 거쳐 실제 API가 응답하는 것까지 확인 완료**
(`curl -H 'Host: api.at-crew.com' http://127.0.0.1/api/community/banners` → `200 {"code":"SUCCESS","data":[]}`).
`deploy/deploy.sh`로 빌드→푸시→배포 파이프라인 자체도 왕복 검증됨. 그 과정에서 **prod 프로필로 실제
기동한 게 이번이 처음이라, 로컬/테스트에서는 한 번도 안 걸렸던 결함 3개**를 찾아 고쳤다:

1. **`SecurityConfig`의 `requiresChannel()`이 런타임에 죽음** — `if (isProd()) { requiresChannel(...) }`
   블록이 참조하는 `ChannelDecisionManager`가 Spring Security 7.0.5에 없어 `NoClassDefFoundError`로
   앱이 아예 기동을 못 했다. 게다가 이 코드는 애초에 우리 아키텍처와도 안 맞았음 — Cloudflare Flexible
   모드는 origin에 평문 HTTP로 전달하는데 origin이 HTTPS를 강제하면 Cloudflare발 트래픽이 전부 막힌다.
   블록 전체를 제거(HSTS 헤더는 그대로 유지 — 그건 브라우저 대상이라 무관). 커밋 `0aa5c39`.
2. **`docker-compose.app.yml`의 `depends_on`이 "컨테이너 시작"만 기다리고 "MariaDB가 실제 접속
   가능한 시점"은 안 기다림** — 첫 배포 때 app이 초기화 중인 MariaDB에 `Connection refused`로 붙었다가
   죽었다(`restart: always`로 우연히 살아남는 구조였음). `mariadb`에 healthcheck 추가하고 app이
   `condition: service_healthy`로 기다리게 함.
3. **Firebase 자격증명 경로가 호스트 경로라 컨테이너 안에서 안 보임** — `.env`의
   `FIREBASE_CREDENTIALS_PATH`는 EC2 호스트 파일시스템 기준인데, 앱은 Docker 컨테이너 안에서 돌아서
   그 경로가 안 보여 `FileNotFoundException`. `deploy/firebase-credentials.json`을 컨테이너 안
   `/app/firebase-credentials.json`으로 볼륨 마운트하고 경로를 그걸로 강제 덮어씀.

2·3번 커밋 `858ac3b`. **교훈**: 로컬 테스트가 아무리 그린이어도 `SPRING_PROFILES_ACTIVE=prod`로 실제
기동해보기 전까지는 prod 전용 분기(`isProd()` 같은)가 안전하다고 확신할 수 없다 — 이번 것들 전부
"코드는 있었지만 이번이 최초 실행"이었던 경로에서 나왔다.

## 2026-08-10 진행 상황

**DNS 연결 + 인증 플로우 전체 스모크 테스트 성공**: root가 `at-crew.com` DNS 편집 권한으로
`api.at-crew.com` A레코드(Proxied)를 EC2 #1 탄력적 IP로 연결해줌. Worker `SERVER_CALLBACK_URL`도
`https://api.at-crew.com/internal/media/images/processed`로 재등록 완료(더 이상 임시 tunnel 아님).

- **Cloudflare→origin 연결이 521(Web Server Is Down)로 처음엔 실패** — 원인은 SSL/TLS 모드가
  Flexible이 아니었던 것(nginx가 80만 열어둔 상태라 Full류 모드면 Cloudflare가 443으로 origin에
  못 붙음). root한테 Flexible로 변경 요청해서 해소.
- **Cloudflare 멤버 권한 정리 필요성 발견**: DNS 편집("Authentication error")과 SSL/TLS 설정(메뉴
  자체가 안 보임) 둘 다 막혀 있어서 매번 root한테 요청해야 했다 — AWS IAM 자기 자신 키 관리 권한
  부재와 같은 패턴. 앞으로 반복 요청을 줄이려고 **남은 프로젝트 기간에 필요할 걸로 예상되는 권한을
  한 번에 정리해서 root한테 요청**함(AWS: IAM 자기 자신 자격증명 관리, Cloudflare: at-crew.com
  DNS+SSL/TLS 편집 또는 Administrator role). 응답 대기 중.
- **회원가입 → 로그인 → 인증 API(성인 콘텐츠 표시 토글) → 재로그인으로 값 영속 확인 → DB 직접 정리**
  전체 플로우를 실제 prod 서버에서 왕복 검증 완료. `POST /api/auth/email/register`(201, JWT+UUIDv7
  발급) → `POST /api/auth/email/login` → `PATCH /api/members/me/adult-content`(204, JWT 인증
  통과) → 재로그인해서 `adultContentVisible: true` 반영 확인 → 테스트 계정은 MariaDB에서 직접 삭제
  (소프트 삭제 API 대신 hard delete — 실사용자 탈퇴가 아니라 순수 테스트 쓰레기라서). 배포 파이프라인
  전체(nginx→앱→MariaDB, JWT 발급/검증, Flyway 스키마)가 실사용 흐름으로 검증된 상태.

**recruit·media 파이프라인 스모크 테스트도 이어서 완료**: 구인글 생성(`POST
/api/recruit/job-postings`, `roles`/`genres` enum 값 정상 반영 — 태그 정규화 작업이 실 prod에서도
검증됨) → R2 presigned URL 발급(`POST /api/artwork/images/presign`, 실제 서명된 R2 URL 확인, R2
자격증명 정상) → media 콜백 수신 엔드포인트(`POST /internal/media/images/processed`)를
`X-Internal-Secret` 정상/오류 값으로 각각 204/401 확인. 테스트 데이터는 전부 DB에서 직접 정리
(가입 3건 hard delete, 구인글 1건 삭제 — 실사용자 탈퇴가 아니라 순수 테스트라 소프트 삭제 API 대신
직접 SQL로 정리).

- **테스트 중 발견(버그 아님, 참고 사항)**: `CreateJobPostingRequest`처럼 `boolean`(Boolean 아님) 필드가
  많은 record는 JSON에 그 필드가 하나라도 빠지면 Jackson이 record 생성자에 `null`을 못 넣어서 전체
  요청이 `COMMON_INVALID_INPUT`(400)으로 거부된다 — 필드별 검증 메시지가 아니라 뭉뚱그려진 에러라
  원인 파악이 어려웠다. 프론트가 이런 DTO를 보낼 때는 boolean 필드를 전부 명시적으로 채워야 함(생략
  불가) — 프론트 연동 문서에 남겨둘 만한 함정.

**남은 것**: root 응답 대기(권한) → EC2 #2 xpack.security 등 하드닝 재검토.

## 2026-08-07 진행 상황 (EC2 프로비저닝 완료)

**AWS IAM 인증 완료·EC2 2대 실제 생성 완료**: root(sehandev)로부터 전용 IAM 사용자(`at-crew-be`,
Account `820010786587`) Access Key 발급받아 `aws configure` 완료(리전 `ap-northeast-2` — laiteu
인스턴스로 실제 확인함, 추정 아니었음). laiteu와 같은 기본 VPC(`vpc-9f11ccf4`) 재사용해 EC2 #1(앱+MariaDB,
`i-0987d8df61c4b84d3`, 탄력적 IP `43.201.142.212`)·EC2 #2(Elasticsearch, `i-07b421fdc2d3f5aff`,
프라이빗 IP `172.31.25.215`만) 생성. 보안 그룹·키페어(`at-crew-key`)까지 다 만들고 SSH 왕복(앱 서버 직접,
검색 서버는 앱 서버 경유) 검증 완료. 상세는 `deploy/README.md` "프로비저닝된 리소스" 표.

- **중간에 뚫린 구멍 하나 있었음**: 검색 서버 보안 그룹에 SSH를 "내 홈 IP"로만 열었는데, 그 서버는
  애초에 퍼블릭 IP가 없어서 홈 IP로는 절대 못 닿는 구성이었다 — 실제 SSH 테스트(앱 서버 경유)를 해보고서야
  발견, 앱 서버의 보안 그룹을 소스로 하는 규칙으로 교체해 해결. **교훈**: 보안 그룹 규칙은 "말이 되는지"
  눈으로만 보지 말고 실제 접속 테스트로 확인할 것 — 특히 프라이빗 서브넷 리소스는 "누구 IP를 허용하느냐"보다
  "애초에 그 경로로 패킷이 갈 수 있느냐"부터 따져야 함.
- AMI는 Amazon Linux 2023(Ubuntu 아님) — `deploy/README.md` 초안이 `apt` 기준으로 잘못 적혀 있던 걸
  `dnf`로 정정함.
**서버 소프트웨어 설치까지 완료 (같은 날 이어서 진행)**:
- EC2 #1: Docker+docker-compose(v2.29.7 standalone 바이너리, dnf에 compose 플러그인이 없어서)+nginx 설치,
  `nginx/api.at-crew.com.conf` 적용해 기동 확인(`/etc/nginx/conf.d/`). `~/at-crew-backend/deploy/`에
  `docker-compose.app.yml`·`.env.example` 업로드해둠 — 아직 `.env`로 채우지 않음.
- EC2 #2: Docker+docker-compose 설치, `docker.elastic.co/elasticsearch/elasticsearch:9.2.8` 이미지까지
  받아서 실제로 띄우고 앱 서버에서 `172.31.25.215:9200` 접근되는 것까지 검증 완료.
- **⚠️ 설계에서 놓쳤던 것 — 프라이빗 서브넷은 아웃바운드도 막힌다**: EC2 #2가 처음부터 퍼블릭 IP가
  없다 보니 dnf 저장소도 GitHub도 전혀 못 닿아서(NAT Gateway 없음) `dnf install docker`부터 실패했다.
  "퍼블릭 IP 없음 = 인바운드만 차단"이라고 생각했는데 아웃바운드(인터넷 나가는 것) 자체가 막히는 거였음 —
  NAT Gateway는 상시 과금이라 안 쓰기로 한 결정은 유지하고, 대신 **설치·이미지 pull 때만 임시로 탄력적
  IP를 붙였다가 끝나면 바로 뗌**(ES는 이미지만 로컬에 캐시되면 이후 재시작 때 인터넷이 필요 없음)으로
  해결. 지금은 다시 완전히 프라이빗 상태로 돌아가 있음(`PublicIpAddress: None` 확인함). **다음에 이
  서버에 뭔가 더 설치해야 하면 이 패턴을 반복할 것.**
- **다음 세션(사용자 직접 필요)**: `.env`는 R2 키·JWT 시크릿·Firebase 서비스 계정 JSON 등 로컬에만 있는
  실제 비밀값이 필요해서 에이전트가 대신 채울 수 없음 — `deploy/.env.example`을 참고해서 EC2 #1의
  `~/at-crew-backend/deploy/.env`로 직접 채워 넣을 것(scp로 옮기고 git에는 올리지 말 것). 그 다음
  Cloudflare DNS 연결(`api.at-crew.com`, 도메인 접근 권한은 아직 root한테 요청 안 함) → Worker
  `SERVER_CALLBACK_URL` 재등록 → `deploy/deploy.sh`로 첫 배포.

## 2026-08-07 진행 상황 (병렬 워크트리 작업 2건 완료)

**로드맵 #6 설정 나머지 — 완료** (커밋 `66958b7`/`5e608c6`/`26ab7dd`, 병합 `97a177c`): 로그아웃(`POST
/api/auth/logout`), 비밀번호 변경(`POST /api/auth/email/password-change`, EMAIL provider 전용),
마케팅 동의·성인 콘텐츠 표시 토글(`PATCH /api/members/me/marketing-agreement`·`/adult-content`) 4개
엔드포인트 신설. `MemberInfo`에 두 필드 읽기 경로 추가. 비밀번호 변경 성공 시 전체 refresh token 폐기
(재로그인 필요 — 프론트 UX 확인 필요할 수 있음). 본인/기업 인증(로드맵 1)·언어 칩(로드맵 7)·카카오 문의는
의도적으로 스코프 밖.

**recruit 검색 태그 정규화 — 완료** (커밋 `b3201a7`~`c33f13d`, 병합 `f1e952a`): Notion 정본 목록 기준
`Genre`(29종)·`MaterialTarget`(7종) enum 신설. **핵심 버그 수정**: recruit(JobPosting/TeamPosting/
JobSeekingPost)의 `roles`가 애초에 `List<String>` 자유텍스트였던 게 검색 필터(`ArtworkRole.name()`) 매칭
불가의 진짜 원인이었음 — `List<ArtworkRole>`로 교정(Artwork.roles는 원래도 정상이었음). recruit
생성/수정 API가 이제 자유 문자열 대신 enum 상수명을 요구함(**API 계약 변경** — 프론트 연동 시 확인 필요).

**⚠️ 병합 중 발견·수정**: 두 워크트리가 서로 모르는 채 각자 `V14`를 채번해 Flyway 버전이 충돌했음
(`V14__artwork_tag_enum_normalization.sql` vs `V14__member_adult_content_visible.sql`) — 병합 직후
`MemberModuleTests`로 실제 마이그레이션 적용을 검증하다가 발견, 후자를 `V16`으로 재채번해 해소(커밋
`4804e54`). **교훈**: 여러 워크트리를 병렬로 돌릴 땐 병합 후 반드시 Flyway 버전 충돌부터 확인할 것
(`ls db/migration | sort`로 중복 번호 눈으로 확인 + Testcontainers 테스트 1개 실행으로 실제 기동 검증).

## 2026-08-07 진행 상황 (이전 항목)

**prod 인프라 구성 확정** (`docs/design/mariadb-migration-design.md` §10-1에 상세 근거 반영):
- **EC2 1대**: 앱 서버 + MariaDB 같이 운영(laiteu와 동일한 self-hosted 패턴, RDS 안 씀 — 포트폴리오 목적상 관리형 DB 운영 경험이 필요 없다고 판단해 비용 우선)
- **EC2 1대**: Elasticsearch 전용(리소스 경합 방지 위해 앱 서버와 분리, 자체관리)
- **Cloudflare R2**: 미디어 스토리지(기존 결정 유지)
- **AWS 계정**: laiteu와 같은 계정(`sehandev` 소유) 재사용. at-crew 전용 IAM 사용자(EC2FullAccess + RDS~~FullAccess~~ 불필요해짐 + AWSBudgetsFullAccess + Billing 정보 접근 토글) 발급을 계정 소유자에게 요청한 상태 — **RDS 정책은 안 써도 되니 요청 문구에서 빼도 됨**, 이 부분은 다음 세션에서 재확인 필요.
- **기각된 대안**: Cloudflare D1 — SQLite 기반이라 JDBC 드라이버가 없어 JPA/Hibernate 앱에서 연결 자체가 불가능(Worker 바인딩/HTTP API 전용). "테넌트별 다중 DB 샤딩" 철학이라 이 프로젝트의 단일 스키마 모듈형 모놀리식과도 안 맞음. 비용 절감 병목은 DB가 아니라 EC2 컴퓨팅이라 실익도 작음.
- **비용 관리**: 사용자가 직접 예산 알림을 걸 수 있도록 IAM에 AWSBudgetsFullAccess + Billing 콘솔 접근 토글도 같이 요청함. NAT Gateway 사용 금지(비용 폭탄 원인), Elasticsearch EC2는 퍼블릭 IP 없이 프라이빗으로(2024년부터 AWS가 퍼블릭 IPv4 자체에 과금) — 다음 세션에서 실제 프로비저닝 시 지킬 것.
- **미완료**: root(sehandev)로부터 IAM 키 발급 대기 중. 발급되면 `aws configure`(로컬에서 직접, 채팅에 키 값 붙여넣지 말 것 — 지난 세션에 한 번 실수로 노출됨) → EC2 프로비저닝(앱+MariaDB, Elasticsearch) 순서로 진행.

**배포 스캐폴드 준비 완료 (2026-08-07, IAM 키 대기 중 미리 작업)**: `Dockerfile`(로컬에서 실제 빌드+컨테이너
기동까지 검증 완료), `deploy/docker-compose.app.yml`(EC2 #1: app+mariadb), `deploy/docker-compose.search.yml`
(EC2 #2: elasticsearch), `deploy/nginx/api.at-crew.com.conf`, `deploy/deploy.sh`, `deploy/.env.example`
전부 커밋 완료 — 상세는 `deploy/README.md`. 도메인은 `api.at-crew.com` 확정, Cloudflare SSL 모드는
Flexible 전제. **다음 세션에서 IAM 키 받으면**: EC2 #1/#2 생성 → 보안 그룹 설정(§ 위 "비용 관리" 참고,
ES는 퍼블릭 IP 없이 앱 서버 보안 그룹만 9200 허용) → `deploy/README.md`의 "최초 1회 설정" 그대로 진행. **prod 보안 결정 2건도 반영 완료**: Swagger UI/API
문서는 prod에서 비활성화(`application-prod.yml`의 `springdoc.api-docs/swagger-ui.enabled: false` —
SecurityConfig가 `/swagger-ui/**`를 permitAll로 열어둬서 꺼두지 않으면 API 스펙이 공개됨), `CORS_ALLOWED_ORIGINS`
는 `https://at-crew.com`(끝 슬래시 없이 — Origin 헤더 규격상 슬래시 붙으면 매칭 안 됨)으로 확정
→ `deploy/.env.example`을 `.env`로 복사해 실값 채우기 → Cloudflare DNS A레코드 연결 → Worker
`SERVER_CALLBACK_URL` 재등록.

**로드맵 P5(이벤트 레지스트리 JDBC 전환 + Mongo 제거) — 완료 (2026-08-07, 백그라운드 워커)**: 전체 테스트 310개 그린, gitleaks 클린, 4개 커밋(`8d316d2`/`db94c78`/`7661857`/`270c999`). `spring-modulith-events-jdbc-2.0.6.jar`의 공식 v2 MariaDB 스키마를 그대로 복사해 `V13__modulith_event_publication.sql`로 커밋(설계 문서가 지정한 V2는 이미 다른 마이그레이션이 선점해 V13으로 채번). UUID 왕복·스키마 타입·재기동 재발행 3가지를 검증하는 `EventPublicationRegistryTest` 신규 작성(재발행 옵션을 false로 끄면 테스트가 실패하는 것까지 네거티브 컨트롤로 확인). 중간에 Gradle daemon stall이 2회 있었는데, 원인은 전날 세션에서 안 끈 `./gradlew bootRun`이 데몬을 점유한 것(P5 자체 버그 아님) — 프로세스 종료로 해결.

**⚠️ P5에서 발견한 실제 결함 — 이미지 처리 동시성 레이스 → ✅ 수정 완료 (2026-08-07)**: Mongo 이벤트 레지스트리 시절엔 발행 등록에 Mongo 왕복이 끼어 리스너 호출이 우연히 직렬화됐었는데, JDBC 전환으로 그 우연한 보호막이 사라지면서 표면화됨. `RecruitMediaEventListener`/`ArtworkMediaEventListener`가 같은 게시글의 이미지 이벤트 두 건을 각각 `REQUIRES_NEW` 트랜잭션으로 처리할 때 서로의 갱신을 못 보고 경합하면 양쪽 다 `readyFor()` false로 남아 **게시글이 PENDING에 영구히 갇힐 수 있었음** — 실제 프로덕션에서 Cloudflare Worker가 이미지별 webhook을 동시에 보내면 바로 재현되는 시나리오였다. 부모 행 `PESSIMISTIC_WRITE` 락(`findByIdForUpdate`)으로 리스너 실행을 직렬화해 수정하고, 실제 동시 발행 경합 테스트 2건을 추가했다(네거티브 컨트롤로 락 제거 시 재현 확인). 상세는 아래 "지금 바로 처리할 것" 0번.

**참고(범위 밖)**: `SearchApiDocTest` 간헐 실패는 P5 이전부터 있던 기존 결함(공유 Elasticsearch Testcontainer가 컨텍스트 종료 시 같이 죽는 구조적 flakiness) — P5와 무관, stash 비교로 확인됨. 싱글톤 컨테이너 패턴 도입 등 별도 정리 필요.

## 2026-08-06 진행 상황

Cloudflare Worker 배포 및 전체 파이프라인(트리거→이미지 변환→콜백) 검증까지 완료했다.

- R2 버킷은 팀 공용 계정 `sehandev-account`(Account ID `8ffe00cd867bc560cfef7b6ab0711b14`)에
  `at-crew-storage`라는 이름으로 이미 2026-07-11에 만들어져 있었음 — 애초에 개인 계정
  `Danhandev@gmail.com's Account`가 아니라 이 계정을 썼어야 했다. `wrangler.toml`에 `account_id`를
  고정해 매번 계정 선택 프롬프트가 안 뜨게 함. `application.yml`/`wrangler.toml`/README의 버킷 이름도
  설계 당시 가정했던 `at-crew-media`에서 실제 버킷명 `at-crew-storage`로 전부 정정함.
- `wrangler dev --remote`로 실제 Cloudflare Images를 통해 이미지 변환 파이프라인을 먼저 검증함(테스트
  이미지 업로드 → Worker 트리거 → 변환된 original/thumb/thumb-adult 다운로드 → 육안 확인, 정상). 이 과정에서
  `src/index.js`의 실버그 발견: `env.IMAGES...output({...})`이 Promise를 반환하는데 `await` 없이 바로
  `.response()`를 체이닝해서 "response is not a function" 에러 발생 — `encodeOriginal`/`encodeThumb`를
  `async` 함수로 바꾸고 `output()` 결과를 `await`한 뒤 `.response()`를 호출하도록 수정(커밋 완료).
- `wrangler deploy`로 실배포 완료 — `https://at-crew-media-worker.sehandev.workers.dev`. 프로덕션
  시크릿(`CALLBACK_SECRET`/`INTERNAL_SECRET`/`SERVER_CALLBACK_URL`)도 `wrangler secret put`으로 등록함.
  로컬 서버를 `cloudflared tunnel --url http://localhost:8080`로 임시 공개해 배포된 Worker가 실제로
  콜백을 보내는지까지 왕복 검증함 — Worker 트리거(202) → Cloudflare Images 처리 → R2 저장 → 터널 경유
  콜백 → 서버 `X-Internal-Secret` 검증(204) 전부 확인, `MediaCallbackService.process()`가 매칭되는
  `media_assets` 행이 없을 때 조용히 no-op하는 것도 코드로 확인(정상 설계 — 예외 아님).
- **다음 세션 시작 전 확인**: 배포된 Worker의 `SERVER_CALLBACK_URL` 시크릿이 위 검증 때 쓴 임시
  `trycloudflare.com` 터널 주소를 그대로 가리키고 있다 — 그 터널 터미널을 닫으면 배포된 Worker의
  콜백은 다시 실패한다. 실제 운영에서는 로드맵 P6(prod 호스팅 확정)에서 서버가 고정 공인 URL을
  가진 뒤 `wrangler secret put SERVER_CALLBACK_URL`로 재등록해야 함. 그 전까지는 이 상태가
  "로컬 개발 중 수동 검증 가능" 정도의 임시 상태임을 인지할 것.
- 테스트로 R2에 올린 `raw/test.jpg`, `raw/test-prod.jpg`와 그 변환 결과물은 정리 필요(안 지웠으면
  `wrangler r2 object delete`로 삭제).

## 2026-08-04 점검 결과

media 모듈 추출(이슈 [#42](https://github.com/pack-in/at-crew-backend/issues/42), PR
[#43](https://github.com/pack-in/at-crew-backend/pull/43)) main 병합 완료. **가장 중요한 후속 조치**:
recruit 이미지 업로드가 실제로 동작하려면 Cloudflare Worker 스크립트가 트리거/콜백 페이로드를
`artworkId` 단일 필드에서 `ownerType`+`ownerId`로 받도록 바뀌어야 한다. 롤아웃 순서는
`docs/design/media-module-design.md` §9.2에 정리돼 있다(서버 선배포 → Worker 배포 → 구 경로 제거 —
순서를 반대로 하면 기존 artwork 업로드가 깨진다는 걸 로컬 검증으로 확인함).

**2026-08-05**: Worker 관리 위치를 이 레포 `cloudflare-worker/`(wrangler로 버전관리)로 확정하고
스캐폴드(`wrangler.toml`, `src/index.js`, `README.md`)를 작성했다. `src/index.js`는
`ownerType`/`ownerId`/`imageKeys`/`variantProfile` 신규 계약, Cloudflare Images 바인딩(`env.IMAGES`)으로
원본 avif 변환·3:4 294px 썸네일 크롭·성인물 blur(20)까지 구현한 상태 — **미검증**(실제 Cloudflare
계정에 배포해본 적 없음). 남은 건 전부 이 레포 밖 사용자 작업: Cloudflare 계정 생성, `wrangler login`,
R2 버킷 생성(`at-crew-media` — artwork·recruit 공용, 버킷 이름은 2026-08-05 세션에서 `atcrew-artwork` →
`atcrew-media` → `at-crew-media`로 정정), 시크릿 등록(`wrangler secret put`), `wrangler dev --remote`로 로컬
검증, `wrangler deploy`, 서버 쪽 `WORKER_TRIGGER_URL`/`R2_*`/`WORKER_CALLBACK_SECRET`/
`ARTWORK_INTERNAL_SECRET` 환경변수 실제 값 채우기. 상세 절차는 `cloudflare-worker/README.md` 참고.

## 2026-08-03 점검 결과

`main` 최신 커밋이 2026-08-01 마지막 세션 종료 시점(`324ae60`)과 동일 — 그 사이 커밋 없음.
GitHub Actions 권한 이슈(#38)는 `release.yml` 삭제로, 설계-구현 불일치(#39)는 문서 정정으로 해소. 나머지
TODO/스텁 3건은 소스에 그대로 존재(아래 표 참고).

부수 정리: 지난 세션에서 worker 에이전트가 남긴 로컬 잔여 브랜치(`worktree-agent-*`, `feat/recruit-rest-docs`)와
stale remote-tracking refs를 정리했다(원격 브랜치는 이미 PR 병합 시 삭제되어 있었음).

## 완료된 것 (이력)

- **2026-08-04**: media 모듈 추출 — artwork에 내장된 Presigned URL/Worker/webhook/재시도/고아정리
  파이프라인을 범용 `media` 모듈로 뽑아 artwork·recruit이 공용 소비하도록 재설계(이슈
  [#42](https://github.com/pack-in/at-crew-backend/issues/42), PR
  [#43](https://github.com/pack-in/at-crew-backend/pull/43)). recruit 게시글(구인글/팀원모집글/구직글)
  이미지 업로드도 이번에 같이 구현됨(자식 테이블 3개, 발행 상태와 독립된 imageProcessingStatus).
  설계 QA·병합 과정에서 결함 다수 발견해 반영 완료(READY 전환 조건, Worker 롤아웃 순서, 영구삭제 후
  media_assets 잔존, 빈 이름 충돌 등 — 상세는 `docs/design/media-module-design.md` §11). main 병합,
  전체 `./gradlew build` 그린, CI 통과. **미완료**: Cloudflare Worker 스크립트 배포(위 점검 결과 참고),
  동시 webhook 경쟁 조건(artwork 원본부터 있던 기존 위험 수준이라 의도적으로 범위 밖).
- **2026-08-03**: 이슈 [#39](https://github.com/pack-in/at-crew-backend/issues/39) 해결 — `docs/design/global-timezone-strategy.md` §3.3.1을 실제 구현에 맞게 정정. 최초 설계는 `timezone` nullable+UTC 폴백이었으나, 구현 시 `Member.validateTimezone()`이 가입 경로(자체·소셜 공통)에서 null을 거부하도록 확정해 폴백 로직 자체가 불필요해졌다 — 버그가 아니라 더 단순한 확정 설계였음을 문서에 반영.
- **2026-08-03**: `release.yml`(release-please) 삭제로 이슈 [#38](https://github.com/pack-in/at-crew-backend/issues/38)
  해소. 실패 로그 확인 결과, 워크플로우 YAML에 `permissions: pull-requests: write`를 명시해도
  "GitHub Actions is not permitted to create or approve pull requests" 에러가 발생 — 이건 조직/저장소
  설정의 하드 게이트(`can_approve_pull_request_reviews`)이고 YAML 권한 블록으로 우회 불가함을 확인.
  `laiteu-be`(같은 pack-in 조직)의 `auto-pr.yml`도 API로 확인하니 동일하게 `false` — 다만 마지막 성공
  실행이 2026-07-07(조직이 이후 정책을 강화한 것으로 추정)이라 laiteu-be도 지금 트리거되면 똑같이
  막힐 가능성이 높음. 조직 admin 권한이 없어 근본 해결은 보류하고, 대신 이슈/PR 생성은 Claude가 `gh`
  CLI(개인 인증 토큰 사용, GITHUB_TOKEN과 무관)로 수행하고 merge/승인은 사람이 직접 하는 워크플로우로
  전환 — release-please 자동화 자체가 불필요해져 워크플로우 파일 삭제. 조직 admin이 나중에
  `https://github.com/organizations/pack-in/settings/actions`에서 "Allow GitHub Actions to create and
  approve pull requests"를 풀면 재도입 검토(git 이력에 원본 파일 남아있음).
- **2026-08-01**: search/company의 recruit 포트 스텁 → 실구현 교체(PR [#41](https://github.com/pack-in/at-crew-backend/pull/41), 이슈 [#36](https://github.com/pack-in/at-crew-backend/issues/36)), 최근 본 작가 자동 기록 이벤트 연동(PR [#40](https://github.com/pack-in/at-crew-backend/pull/40), 이슈 [#37](https://github.com/pack-in/at-crew-backend/issues/37)), 관련 설계 문서 3건 정합성 정정 — 전부 main 병합, 테스트 그린
- **2026-07-31**: 로드맵 대규모 갱신(Polar 결제·PASS 인증·recruit 스코프 확대·i18n/관리자/OG카드 신규 항목), 글로벌 시간대 UTC 전환(PR #30), MariaDB P4(PR #32), recruit 모듈 구현(PR #34), recruit REST Docs(PR #35)

recruit 모듈(구인글/팀원모집글/구직글/지원/끌어올리기/관심작가/포트연동/최근본작가) 스코프는 이제 전부 완료 상태다.

## 지금 바로 처리할 것 (우선순위순)

### 0. ~~이미지 처리 동시성 레이스 수정~~ — ✅ 수정 완료 (2026-08-07)
`RecruitMediaEventListener`/`ArtworkMediaEventListener`가 같은 게시글의 이미지 이벤트 두 건을 각각
`REQUIRES_NEW` 트랜잭션으로 동시 처리하면 서로의 갱신을 못 보고 경합해 양쪽 다 `readyFor()` false로
남던 결함(**게시글이 PENDING에 영구히 갇힘**). Mongo 이벤트 레지스트리 시절엔 우연히 직렬화돼
드러나지 않았는데 P5(JDBC 전환)로 그 보호막이 사라지며 표면화됐다.

**수정**: 부모 행에 `PESSIMISTIC_WRITE` 락을 걸어 같은 게시글/작품에 대한 리스너 실행을 직렬화한다.
`ArtworkRepository`/`JobPostingRepository`/`TeamPostingRepository`/`JobSeekingPostRepository`에
락 전용 `findByIdForUpdate`를 신설하고(기존 `findById`는 다른 호출부 컨텐션을 피하려 그대로 둠),
두 리스너가 이미지 상태를 읽기 전에 이 메서드로 부모 행을 잠근다. locking read는 REPEATABLE READ
스냅샷과 무관하게 항상 최신 커밋을 읽으므로, 락을 기다린 두 번째 트랜잭션은 첫 트랜잭션의 갱신을 보고
정상적으로 READY로 전이한다.

**검증**: `RecruitModuleTests.같은_구인글의_이미지_이벤트가_동시에_도착해도_READY로_전이된다`,
`ArtworkModuleTests.같은_작품의_이미지_이벤트가_동시에_도착해도_READY로_전환된다` —
`ExecutorService` + `CountDownLatch`로 두 이벤트를 실제 동시 발행하는 경합 테스트(설계 §7 리스크 3).
락을 일시 제거하면 두 테스트가 재현성 있게 실패하는 것까지 네거티브 컨트롤로 확인했다.

### 1. recruit 검색 후속 과제 (PR #41에서 의도적으로 남긴 것)
지금 동작에 문제는 없지만, 데이터가 늘거나 기획이 확정되면 손봐야 하는 항목들이다.

1. **태그 정본 목록 정규화** — 완료함(2026-08-07). Notion 정본 목록으로 `com.atcrew.artwork.Genre`(29종)·
   `com.atcrew.artwork.MaterialTarget`(7종) enum을 신설하고, `Artwork.genres`/`Material.targets`와
   recruit 3종(`JobPosting`/`TeamPosting`/`JobSeekingPost`)의 `roles`/`genres`, `SearchQuery`,
   요청 DTO까지 전 계층을 enum으로 통일했다. recruit의 자유 텍스트가 `ArtworkRole.name()` 필터와
   매칭되지 않던 것이 근본 원인이었고 이로써 해소됨. 담당 업무(`ArtworkRole`)·연령대(`AgeRating`)는
   이미 정본과 일치해 변경하지 않았다. 정본 밖 값 정리는 `V14`/`V15` 마이그레이션이 담당하며,
   `SearchQuery.java` TODO와 `search-module-design.md` §1.4/§9-2 항목도 함께 해소함
2. **recruit 검색의 ES 색인 이관** — 완료함(2026-08-05). RDB `LIKE` + EXISTS 서브쿼리 기반이던
   `RecruitSearchService`/`RecruitSearchQueryRepository`(recruit 모듈)를 삭제하고, artwork와 동일하게
   `search` 모듈이 `recruit_posts` ES 인덱스를 소유·질의하도록 이관함 — `RecruitSearchIndexer`/
   `RecruitReindexService`/`search.internal.persistence.RecruitSearchQueryRepository` 참고
3. **병합 검색의 알려진 제약** — 포트폴리오와 recruit을 함께 요청하면 (a) 정렬은 항상 최신순 고정
   (관련도 점수를 소스 간 비교할 수 없음), (b) 작품 분야·창작 유형·연령대·소재 대상 필터가 걸리면
   recruit은 결과에서 제외. (a)(b)는 의도된 동작. (c) 커서 밀리초 충돌 경계 누락 문제는 소스별
   서브커서(`MergedSearchCursor`) 방식으로 재설계해 해소함 — `SearchServiceImpl.searchMerged` 참고
4. **관심 작가 검색 후보 로딩** — `q`가 있으면 해당 기업이 좋아요한 작가 ID를 전부 읽어 member 모듈에
   넘기고 `IN` 절로 페이지네이션한다. 좋아요 규모가 커지면(수천 건) 개선 필요
5. **`CompanyInfo.hasOpenJobPosting` 조회 비용** — 기업 프로필 단건 조회마다 recruit `exists` 쿼리가
   1회 추가된다. 목록 API가 생기면 N+1이 되므로 그때 일괄 조회/캐시 검토

### 2. 코드에 남아 있는 스텁·TODO 지도 (착수 전 참고용)
다른 모듈 작업 시 함께 풀어야 하는 것들. 각 항목 옆이 선행 조건이다. (2026-08-03 grep으로 전부 재확인)

| 위치 | 내용 | 선행 |
|---|---|---|
| `RecruitServiceImpl` boostJobPosting/boostTeamPosting | 끌어올리기 구매 개수 확인·차감 | 로드맵 5(Polar 결제) |
| `JobPostingController` 관리자 섹션, `BannerController` | 인증만 요구, Role 검증 없음 | 로드맵 8(관리자 Role 체계) |
| `Company.verified` | 항상 false, API 미노출 | 로드맵 1(기업 인증) |
| `ArtworkField.PRINT_COMIC` | 피그마 화면끼리 충돌 — 홈(6107:24822)은 출판만화, 검색(5752:29315)은 웹소설. 기획서 홈-R01·업로드-R06은 출판만화 쪽이고 홈 프레임이 더 최신 | 기획 확정 필요 |
| `ActiveRegion`(company) | 피그마에서 옵션 값 특정 실패 | 피그마 확인 |
| search 분석기 | Phase 1은 `standard`, nori 도입 미정 | 검색 품질 이슈 발생 시 |

## 로드맵에 남은 큰 항목 (설계부터 필요, `docs/roadmap.md` 참고)

이 항목들은 코드 작업 전에 설계 결정이 더 필요해 아직 착수하지 않았다.

| 순서 | 항목 | 착수 전 필요한 것 |
|---|---|---|
| 1 | 본인/기업 인증(verification) | PASS 연동 방식 상세 설계(SDK/API 계약), `Member` 인증 상태 필드 스키마 |
| 5 | 결제/구독(Polar→Stripe, `turban` 워크트리 별도 진행 중) | Stripe API 연동 상세 설계(Checkout·웹훅 이벤트 스펙). 이메일 발송 인프라는 2026-08-12 구현 완료(`com.atcrew.common.mail`, Resend) — 재사용 가능 |
| 6 | 설정 나머지 | 비교적 작음, 아무 때나 착수 가능 |
| 7 | 다국어(i18n) | `MessageSource`/로케일 전략 설계, `Member.primaryLanguage`/`postLanguages` 스키마 |
| 8 | 관리자/모더레이션 콘솔 | 관리자 Role 체계 설계(member 모듈에 아직 없음) |
| 9 | 소셜 공유 메타(OG 카드) | 서버 렌더링 방식 vs 메타 엔드포인트 방식 결정 |
| 0 (마이그레이션) | MariaDB P5·P6 | P5: Modulith 이벤트 레지스트리 전환+Mongo 제거. P6: prod 접속정보(호스팅 형태 확정 필요) |

## 참고 문서

- `docs/roadmap.md` — 로드맵 정본, 각 항목 Figma/기획서 근거 포함
- `docs/AT-CREW_서비스기획서_전체_20260728.xlsx` — 서비스 기획서 정본(요구사항/정책/화면/플로우/기능명세/QA)
- `docs/design/recruit-module-design.md` — recruit 모듈 상세 설계(2026-08-01 갱신 완료)
- `docs/design/mariadb-migration-design.md` — MariaDB 전환 상세(§6 P5·P6 미착수, §13에 artwork 전환 함정 기록)
- `docs/design/global-timezone-strategy.md` — 시간대 설계(확정 상태, 이슈 #39 문서 정합성 정정 완료)
