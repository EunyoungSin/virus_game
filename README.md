# 검문소
AI 기반 감염자 판별 텍스트 게임.<br>
바이러스 봉쇄 도시의 출입관리소 직원이 되어 방문자와 대화를 통해 감염 여부를 추리하고 출입을 허가/거부하는 1인 텍스트 어드벤처.

## 기술 스택
- Backend: Spring Boot 3 (Java 21, Gradle Kotlin DSL), JPA/Hibernate
- Frontend: Vue 3 + Vite + TypeScript, vue-router, pinia, axios
- DB: PostgreSQL 16
- AI: Google Gemini API (방문자 NPC 대화 생성, provider 추상화 예정)
- 인증: 게스트 전용(deviceId 기반 JWT 발급). 회원가입/로그인 화면, 이메일/비밀번호, 계정 전환은 없음

## 디렉토리 구조
```
backend/    Spring Boot 애플리케이션
frontend/   Vue 3 SPA
docker-compose.yml   로컬 개발용 PostgreSQL
```

현재 상태: 게스트 인증(디바이스ID 기반 JWT 발급, 로그인/회원가입 화면 없음),
Game 생성/요약/일시정지 API(**게임 생성 자체에는 개수 제한이 없음** — 저장 슬롯 5개가 실질적
제약),
Visitor 생성 로직(게임 생성 시 아키타입 1~12번 기반 12명 자동 생성) + `next-visitor` 조회 API,
Conversation API(Gemini 연동, provider 추상화 + 대화 이력 조회)에 이어
Decision API(판정 + 신뢰도 반영 + Day 전환 + 엔딩 계산), 게임 결과 조회 API,
유저 전역 저장 슬롯(최대 5개) API까지 구현되었습니다.

판정 규칙("전원 거부" 필승 전략 방지용 오탐 페널티): 비감염자를 REJECT(오탐)할 때마다 신뢰도 10점
감소. **감염자를 ADMIT해도 신뢰도에는 즉시 영향이 없습니다** — 그 대가는 감점이 아니라 최종 엔딩
등급으로만 반영됩니다. 신뢰도가 게임 도중 **20 이하로 떨어지면 남은 일차와 무관하게 즉시
FINISHED/BAD로 조기 종료**됩니다(신뢰 붕괴/소요 사태 서사). 조기 종료되지 않으면 하루 판정이 끝날
때마다 다음 날로 전환되고, 마지막 날 방문자까지 전원 판정되면 FINISHED로 종료되며 `game_results`에
결과가 기록됩니다.

엔딩 계산 (`GameFinishService`):
1. 기본 등급은 **감염자를 몇 명 ADMIT 했는지**로 결정 — 0명=BEST(백신 개발·도시 자유화),
   1~2명=NORMAL(백신 개발되었으나 일부 사망), 3명 이상=BAD(`ending_reason=INFECTION_SPREAD`,
   도시 멸망)
2. 무고한 방문자(비감염자)를 **3명 이상 오탐 거부**했다면 위 등급을 한 단계 하향합니다
   (BEST→NORMAL, NORMAL→BAD, BAD는 그대로) — 감염자를 전혀 통과시키지 않았어도 적용되어
   "의심스러우면 무조건 거부"가 안전한 전략이 되지 않게 합니다.
3. 신뢰 붕괴(20 이하)나 유휴 타임아웃으로 조기 종료된 경우는 위 계산과 무관하게 이미
   BAD로 확정되며, `ending_reason`도 각각 `TRUST_COLLAPSE`/`IDLE_TIMEOUT`으로 고정됩니다.

BAD 엔딩은 `ending_reason`(`games`/`game_results` 컬럼) 하나로 세 갈래를 명확히 구분합니다 —
더 이상 `total_processed` 값으로 유추하지 않습니다: `INFECTION_SPREAD`(12명 전원 처리 후에도
감염자 3명 이상 통과), `TRUST_COLLAPSE`(신뢰도 20 이하 조기 종료), `IDLE_TIMEOUT`(유휴 타임아웃,
아래 참고). 프론트 `content/story.ts`의 `resolveEndingEpilogue()`가 이 값으로 에필로그 텍스트를
분기합니다.

유휴 타임아웃(`ending_reason=IDLE_TIMEOUT`): 게임 화면을 열어둔 채 **실질 행동**(대화/판정/
검사키트/저장/불러오기) 없이 10분이 지나면 자동으로 BAD 엔딩 처리됩니다("근무 수칙 1항 — 자리를
오래 비우지 말 것"의 서사적 페이백). 판정 기준은 달력 시간이 아니라 **게임 화면이 실제로 열려있던
시간**입니다 — 프론트가 `GamePlayView`가 마운트되어 있고 `document.visibilityState === 'visible'`인
동안에만 30초 간격으로 `POST /api/games/{gameId}/heartbeat`를 호출합니다(탭이 백그라운드로
전환되면 전송을 멈추고, 그 시간은 유휴로 카운트되지 않습니다). 서버 로직(`HeartbeatService`):
하트비트 간격이 2분을 초과했다 재개되면 "방금 복귀"로 간주해 유휴 시계를 리셋하고(며칠 만에
다시 접속하는 정상적인 사용 패턴을 배제하기 위함), 그렇지 않고 마지막 실질 행동 이후 10분을
초과했다면 그 시점에 즉시 `FINISHED`/`BAD`/`IDLE_TIMEOUT`으로 강제 종료합니다. **명시적으로
일시정지(PAUSED)한 게임은 대상에서 제외**됩니다(자리를 비울 때 일시정지를 누르도록 자연스럽게
유도). `conversations`/`test-kit`/`decision`/`save`/`load` 등 실질 행동 API는 호출 시 모두
`last_action_at`과 `last_heartbeat_at`을 함께 갱신합니다.

증상·감염 판별: 증상은 감염 여부와 1:1로 대응하지 않습니다. `infection_stage`별로 증상 발현 확률이
다르게 생성 시점에 확정됩니다(NONE 20~25%, EARLY 10~15%, INCUBATION 30~40%, LATE 70~80% — 비감염자도
알레르기/흡연/과로 등으로 증상을 보일 수 있고, 감염 초기는 오히려 증상이 옅습니다). 대화만으로는
100% 확신이 불가능하도록 설계되어 있고, 확정적 판별 수단은 검사키트뿐입니다: `POST
/api/games/{gameId}/visitors/{visitorId}/test-kit`을 쓰면 해당 방문자의 `infected` 값을 그대로
반환하고 `resources_left.testKit`을 1 차감합니다(자원 소진 시 409, 게임당 기본 3개).

Visitor 생성 규칙: 12종 아키타입(1~12번)이 매 게임 정확히 1회씩 등장(= 총원과 아키타입 수가 같아
1:1 배정). 하루 일수(`game.days`, 기본 4)와 하루당 인원(`game.visitors-per-day`, 기본 3)은
`application.yml`에서 조정 가능한 값이며 하드코딩되어 있지 않습니다(단, days × visitorsPerDay는
반드시 아키타입 총수와 같아야 하며, 다르면 `VisitorGenerationService.generateVisitors()`가
`IllegalStateException`을 던집니다). 감염자는 `game.min-infected`~`game.max-infected`(기본 4~6명)
범위에서 랜덤 확정되며, **12명 중 어느 아키타입 인스턴스가 감염될지는 완전히 무작위** — 레드헤링
아키타입이라고 감염에서 배제되지 않습니다. 1일차는 "불안" 비중을 높게, 마지막 날에 가까워질수록
"침착·뻔뻔" 비중을 높여 후반부 난이도를 올립니다.

레드헤링 아키타입(9·10·12번, `red_herring_type = true`): 수배자·언론/기자·밀입국 브로커 동행자.
"레드헤링"은 **감염과 무관한 확실한 거짓말 사유를 갖고 있다는 뜻일 뿐, 항상 비감염이라는 보장은
아닙니다** — 이들도 다른 아키타입과 동일하게 무작위로 감염될 수 있습니다. 성직자/구호단체(11번)는
`plausible_lie_reasons`가 "감염은폐" 하나뿐이라 감염과 무관한 거짓말이 사실상 발생하지 않는
"솔직" 캐릭터입니다.

**저장 슬롯(유저 전역, 최대 5개, 완전 스냅샷 방식)**: 저장 슬롯은 게임이 아니라 **유저** 소유
자원입니다 — 한 게임이 슬롯 5개를 전부 차지할 수도, 여러 게임이 슬롯을 나눠 가질 수도 있습니다.
"진행 상태"(games/visitors/conversations)는 플레이 중 계속 실시간으로 갱신되지만, 세션을 나가는
것만으로는 아무것도 저장되지 않습니다. `POST /api/games/{gameId}/save`에 `{"slotNo": 1~5}`를
보내면 그 슬롯에 현재 상태의 **완전한 스냅샷**이 upsert됩니다 — 숫자 상태(day/trust/resources)뿐
아니라 그 시점까지의 모든 visitor 판정과 conversation 전체가 JSONB로 함께 저장됩니다
(`visitor_decisions_snapshot`, `conversations_snapshot`). 같은 슬롯에 다시 저장하면 이전 게임이
다른 게임의 스냅샷을 갖고 있었더라도 완전히 교체됩니다.

`POST /api/games/{gameId}/load`에 `{"slotNo": N}`을 보내면 그 슬롯이 **실제로 가리키는 게임**을
복원합니다 — 요청 경로의 `{gameId}`는 참고용일 뿐 신뢰하지 않습니다(슬롯의 `game_id`만 신뢰). 복원은
타임스탬프 비교로 일부만 되돌리는 방식이 아니라 **전부 초기화 후 스냅샷만 재적용**하는 방식입니다:
해당 게임의 모든 visitor 판정을 null로 리셋한 뒤 스냅샷에 있는 것만 재적용하고, 모든 conversation을
지운 뒤 스냅샷 내용을 그대로 재삽입합니다. 이 방식 덕분에 슬롯을 어떤 순서로 선택해도 항상 정확히
같은 결과가 나옵니다. `game_results`(완료 이력)는 절대 건드리지 않습니다 — FINISHED였던 게임을
과거 시점으로 되돌려도 그 전에 쌓인 완료 기록은 그대로 남습니다.

`GET /api/users/{userId}/saves`가 곧 "사건 이어하기" 화면입니다 — 슬롯 1~5 전체를 점유/빈 슬롯으로
반환하며, 점유된 슬롯은 어느 게임의 몇 일차인지·신뢰도·저장 시각과, 그 게임이 FINISHED라면
엔딩 타입/사유까지 포함합니다. `DELETE /api/users/{userId}/saves/{slotNo}`는 그 슬롯 하나만
비웁니다 — 슬롯이 가리키던 게임(games/visitors/conversations)은 전혀 건드리지 않습니다("게임
완전 삭제"와는 다른, 훨씬 가벼운 작업입니다).

**같은 게임이 여러 번 완료될 수 있습니다**: 슬롯으로 과거 시점으로 되돌린 뒤 다시 플레이해서
또 완료하면, `game_results`에 새 row가 추가로 쌓입니다(덮어쓰지 않음 — `game_id` UNIQUE 제약을
제거했습니다). 게임이 완료(FINISHED)되어도 그 게임을 가리키던 저장 슬롯은 **지워지지 않습니다**
— 완료된 게임도 슬롯을 통해 과거 시점으로 이어할 수 있어야 하기 때문입니다.

**저장 슬롯 vs 엔딩 기록 보관소**: 이 둘은 서로 다른 데이터와 화면입니다. 저장 슬롯(`game_saves`)은
체크포인트로, 어느 게임의 어느 시점이든 담을 수 있고 게임이 끝나도 사라지지 않습니다. 엔딩 기록
보관소(`game_results`, `GET /api/users/{userId}/endings`)는 완료될 때마다 쌓이는 읽기 전용 이력으로,
저장/불러오기 버튼이 없고 삭제도 되지 않습니다(같은 gameId가 여러 번 나타날 수 있음). 라우트도 진행
중 사건(슬롯 목록, `/games`)과 종결 기록 보관소(`/archive`)를 분리했습니다.

게임 삭제: `DELETE /api/games/{gameId}`는 IN_PROGRESS/PAUSED 게임을 완전히 삭제합니다
(conversations → visitors → 이 게임을 가리키는 저장 슬롯 전체(0~5개) → games 순 cascade).
FINISHED 게임은 이미 결과가 엔딩 기록 보관소에 남아 있어야 하므로 삭제할 수 없고 409를 반환합니다
("삭제 대신 보관"). **현재 이 API를 호출하는 프론트 UI는 없습니다** — "사건 이어하기" 화면이
게임 목록에서 슬롯 목록(`SlotPicker.vue`, `GameListView.vue`)으로 바뀌면서, ✕ 버튼은 슬롯 하나만
비우는 `DeleteSaveSlot`만 호출합니다(공용 `ConfirmModal.vue`로 확인받은 뒤 실행). 게임 자체를
통째로 지우는 화면은 아직 없습니다 — "사건 이어하기 = 슬롯 목록"으로 화면을 재정의하면서 생긴
간극으로, 필요하면 별도로 "내 사건 전체 관리" 같은 화면을 추가해야 합니다.

## 로컬 실행

### 1. DB 기동
```bash
docker compose up -d
```

### 2. 백엔드
```bash
cd backend
./gradlew bootRun
```
기본적으로 `local` 프로필이 활성화되며(`application-local.yml`), 기동 시 `schema.sql`이 자동 적용됩니다.
`local` 프로필에는 개발용 JWT 시크릿이 이미 들어있어 별도 설정 없이 바로 기동됩니다. `local` 프로필이 아닌
환경에서는 `JWT_SECRET` 환경변수(32바이트 이상)를 반드시 지정해야 합니다.
`curl localhost:8080/actuator/health` 로 정상 기동을 확인할 수 있습니다.

`schema.sql`은 `CREATE TABLE IF NOT EXISTS`라서 신규 설치에만 적용되고, 이미 떠있는 DB의 기존
테이블에는 컬럼을 추가해주지 않습니다. 스키마가 바뀔 때마다 `backend/src/main/resources/migrations/`에
번호가 매겨진 idempotent(`ADD COLUMN IF NOT EXISTS`) 마이그레이션 스크립트를 추가하며, 이미 구동 중인
DB에는 수동으로 적용해야 합니다:
```bash
psql "$DATABASE_URL" -f backend/src/main/resources/migrations/002_add_symptom_columns.sql
psql "$DATABASE_URL" -f backend/src/main/resources/migrations/003_add_ending_reason_and_heartbeat.sql
psql "$DATABASE_URL" -f backend/src/main/resources/migrations/004_rework_game_saves_to_slots.sql
```
004는 `game_saves`를 드롭 후 새 구조로 재생성합니다 — 로컬 개발 DB 기준으로 기존 저장 데이터
보존 없이 진행하는 파괴적 마이그레이션입니다(운영 환경이라면 데이터 이관 스크립트가 별도로
필요합니다).

인증 API 동작 확인 예시 (유일한 인증 엔드포인트 — 로그인/회원가입/계정전환 없음):
```bash
# 게스트 로그인 (deviceId로 유저 조회/자동 생성 + JWT 발급)
# deviceId는 클라이언트(프론트)가 생성한 UUID v4를 그대로 사용한다. 서버는 생성하지 않는다.
curl -X POST localhost:8080/api/auth/guest \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"11111111-1111-1111-1111-111111111111"}'
```

Game API 동작 확인 예시 (`<token>`은 위에서 발급받은 JWT):
```bash
# 새 게임 생성 (개수 제한 없음 — 저장 슬롯 5개가 실질적 제약)
curl -X POST localhost:8080/api/games -H "Authorization: Bearer <token>"

# 게임 요약 조회
curl localhost:8080/api/games/<gameId>/summary -H "Authorization: Bearer <token>"

# 일시정지
curl -X PATCH localhost:8080/api/games/<gameId>/pause -H "Authorization: Bearer <token>"

# 하트비트 (게임 화면이 열려있는 동안 30초 간격 호출, 유휴 타임아웃 판정용)
curl -X POST localhost:8080/api/games/<gameId>/heartbeat -H "Authorization: Bearer <token>"

# 다음 대기 방문자 조회 (숨김 필드 제외 DTO)
curl localhost:8080/api/games/<gameId>/next-visitor -H "Authorization: Bearer <token>"

# 방문자에게 질문 (Gemini 호출 + 대화 기록 저장)
curl -X POST localhost:8080/api/games/<gameId>/visitors/<visitorId>/conversations \
  -H "Content-Type: application/json" -H "Authorization: Bearer <token>" \
  -d '{"question":"최근 어디 다녀오셨나요?","topicTag":"TRAVEL"}'

# 해당 방문자와 나눈 대화 이력 조회 (게임 상태·판정 여부와 무관하게 조회 가능)
curl localhost:8080/api/games/<gameId>/visitors/<visitorId>/conversations -H "Authorization: Bearer <token>"

# 검사키트 사용 (감염 여부 확정 반환 + 자원 1개 차감, 소진되면 409)
curl -X POST localhost:8080/api/games/<gameId>/visitors/<visitorId>/test-kit -H "Authorization: Bearer <token>"

# 판정 (현재 대기 중인 방문자에게만 가능, 순서를 벗어나면 409)
curl -X POST localhost:8080/api/games/<gameId>/visitors/<visitorId>/decision \
  -H "Content-Type: application/json" -H "Authorization: Bearer <token>" \
  -d '{"decision":"ADMIT"}'

# 게임 결과 조회 (FINISHED 상태일 때만 조회 가능, 그 전엔 404. 여러 번 완료됐다면 가장 최근 것)
curl localhost:8080/api/games/<gameId>/result -H "Authorization: Bearer <token>"

# 저장 슬롯 목록 (유저 전역 5개, "사건 이어하기" 화면 그 자체)
curl localhost:8080/api/users/<userId>/saves -H "Authorization: Bearer <token>"

# 지정한 슬롯(1~5)에 현재 진행 상태의 완전한 스냅샷 저장 (덮어쓰기 가능)
curl -X POST localhost:8080/api/games/<gameId>/save \
  -H "Content-Type: application/json" -H "Authorization: Bearer <token>" -d '{"slotNo":1}'

# 지정한 슬롯이 가리키는 게임으로 완전 복원 (경로의 gameId는 참고용, 슬롯의 game_id가 우선)
curl -X POST localhost:8080/api/games/<gameId>/load \
  -H "Content-Type: application/json" -H "Authorization: Bearer <token>" -d '{"slotNo":1}'

# 슬롯 하나만 비우기 (그 슬롯이 가리키던 게임은 건드리지 않음, 저장본 없어도 204)
curl -X DELETE localhost:8080/api/users/<userId>/saves/1 -H "Authorization: Bearer <token>"

# 게임 자체를 완전히 삭제 (IN_PROGRESS/PAUSED만 가능, FINISHED는 409) — 현재 프론트 UI 없음
curl -X DELETE localhost:8080/api/games/<gameId> -H "Authorization: Bearer <token>"

# 엔딩 기록 보관소 (완료될 때마다 누적, 같은 gameId가 여러 번 나타날 수 있음, 최신순)
curl localhost:8080/api/users/<userId>/endings -H "Authorization: Bearer <token>"
```

게임 규칙 파라미터(하드코딩 아님, `application.yml`의 `game.*` 또는 환경변수로 조정):
`GAME_DAYS`(기본 4), `GAME_VISITORS_PER_DAY`(기본 3), `GAME_MIN_INFECTED`(기본 4), `GAME_MAX_INFECTED`(기본 6).
`GAME_DAYS × GAME_VISITORS_PER_DAY`는 항상 `visitor_archetypes`에 시딩된 아키타입 총수(12)와 같아야 합니다.

Conversation API를 쓰려면 `GEMINI_API_KEY` 환경변수(Google AI Studio에서 발급)가 필요합니다.
`local` 프로필에는 안전상 기본 키를 넣어두지 않았으므로, 키 없이 호출하면 502가 반환됩니다.
`gemini.model`/`gemini.fallback-model` 기본값은 `gemini-flash-latest`/`gemini-flash-lite-latest`
(버전 고정 이름 대신 별칭을 사용해 모델 폐기에 덜 취약하도록 함)이고,
Gemini 3.x 계열은 `maxOutputTokens` 예산 일부를 보이지 않는 "thinking" 토큰이 먼저 소모하므로
`gemini.max-output-tokens`를 200~300이 아닌 1024로 넉넉히 잡아야 답변이 중간에 끊기지 않습니다.

### 3. 프론트엔드
```bash
cd frontend
cp .env.example .env   # 필요 시 API base URL 수정
npm install
npm run dev
```
`http://localhost:5173` 에서 확인합니다. 백엔드는 기본적으로 `http://localhost:5173` 출처만
CORS로 허용합니다(`cors.allowed-origins`, 필요 시 `CORS_ALLOWED_ORIGINS` 환경변수로 변경).

화면 흐름: 로그인(게스트 시작) → **타이틀**(`/title`, `TitleView.vue` — 신규 사건 개시/사건
이어하기/기록 보관소 3버튼) → 신규는 곧장 플레이로, 이어하기는 저장 슬롯 목록(`/games`)으로, 기록
보관소는 엔딩 아카이브(`/archive`)로 분기 → 플레이(방문자 정보 확인 → 질문/답변 → 입장 허용·거부
판정, 판정마다 정오 피드백과 신뢰도 표시) → 결과(엔딩 타입 및 통계, "타이틀로"/"기록 보관소에서
보기" 버튼). 인증 정보(JWT, deviceId)는 `localStorage`에 저장되며, 라우터 가드가 미인증 접근을
로그인 화면으로 돌려보냅니다. 방문자별 대화는 새로고침해도 사라지지 않도록, 방문자를 불러올 때마다
`GET .../conversations`로 DB에 저장된 이전 대화를 다시 불러옵니다.

**"사건 이어하기"는 게임 목록이 아니라 저장 슬롯 목록입니다.** `GameListView.vue`(`/games`,
타이틀의 "사건 이어하기"로 진입)는 `GET /api/users/{userId}/saves`로 슬롯 5개를 조회해
`SlotPicker.vue`(mode=LOAD, entry-context=FROM_TITLE)로 보여줍니다. 여기서 점유된 슬롯을
클릭하면 **별도 확인 없이 바로** 그 슬롯이 가리키는 게임으로 `load`하고 곧장 플레이
화면(`game-play`)으로 들어갑니다(돌아갈 원래 게임 화면 자체가 없는 진입 경로라 되돌릴 진행
상태가 없기 때문) — 로드에 성공한 게임의 `gameId`로 라우팅하므로 선택한 슬롯이 다른 게임을
가리켜도 정상 동작합니다. ✕ 버튼은 공용 `ConfirmModal.vue`로 확인받은 뒤 `DELETE
/api/users/{userId}/saves/{slotNo}`를 호출해 슬롯만 비웁니다. FINISHED 상태를 가리키는 슬롯도
그대로 남아있고 여전히 클릭 가능합니다(완료된 게임도 슬롯을 통해 과거 시점으로 이어할 수 있음).
엔딩 기록 보관소(`ArchiveView.vue`, `/archive`)는 여전히 완전히 분리된 읽기 전용 화면입니다.

플레이 화면에는 "저장"과 "불러오기"가 서로 독립된 버튼으로 있습니다. "저장"은 항상
`SlotPicker.vue`(mode=SAVE)를 엽니다 — 빈 슬롯을 클릭하면 확인 없이 바로 저장되고, 이미 기록이
있는 슬롯을 클릭하면 그 슬롯이 **현재 플레이 중인 게임과 같은 게임**을 가리키는지에 따라
`ConfirmModal.vue`의 문구·워터마크만 달라집니다 — 같은 게임의 다른 시점이면 "슬롯 갱신"(quarantine
워터마크), 다른 게임이면 "슬롯 충돌"(stampRed 워터마크, 대상 게임 번호·일차·종료 여부 표시).
"불러오기"는 `SlotPicker.vue`(mode=LOAD, entry-context=FROM_IN_GAME)를 열어 슬롯 5개 전체(현재
게임으로 한정하지 않음)를 보여주고, 점유된 슬롯 클릭 시 `ConfirmModal.vue`("이 시점으로
되돌리기")로 확인받은 뒤 `load`합니다 — 로드한 게임이 지금 플레이 중인 게임과 같으면 화면 상태만
그 자리에서 갱신하고, 다르면 그 게임의 플레이 화면으로 라우팅합니다. 이 두 SlotPicker의 하단
버튼 구성은 `entry-context`로 갈립니다: SAVE는 "취소하고 돌아가기" 하나, LOAD·FROM_IN_GAME은
"취소하고 돌아가기"(원래 게임 화면으로)와 "타이틀로 돌아가기" 둘 다.

"← 목록으로" 버튼은 확인이나 API 호출 없이 곧장 타이틀로 이동합니다 — 라이브 게임 상태
(games/visitors/conversations)는 그대로 둔 채 화면만 바뀌므로, 이후 "사건 이어하기"나 플레이
화면의 "불러오기"로 다시 들어오면 이어서 진행할 수 있습니다. 저장 여부를 묻지 않는 이유는
저장이 오직 "저장" 버튼을 통한 명시적 스냅샷으로만 이뤄지기 때문입니다.

스토리 텍스트(`src/content/story.ts`, 스키마·API 추가 없이 프론트 정적 콘텐츠로만 관리):
- **인트로**: 게임을 새로 시작할 때 1회만(`localStorage.checkpoint.introShown.<gameId>`) 내부
  공문이 스캔 리빌로 나타나고, 곧바로 "1일차" 타이틀 비트로 이어짐(`IntroBriefing.vue`). 항상
  스킵 가능.
- **일자별 서브타이틀**: 일자 전환 연출의 "다음 날 열림" 단계에 day_index별 한 줄 추가.
- **일지 텍스트**: 같은 연출의 "서류철 닫힘" 단계, 요약 도장 아래에 그날까지 누적으로 통과시킨
  감염자 수(`GameSummaryResponse.infectedAdmittedSoFar` — 기존 `visitors.infected`/`decision`
  컬럼만으로 계산, 스키마 변경 없음) 기준 3단계 분기로 노출. 4일차(마지막 날)는 별도의 "엔딩 예고"
  문구 표를 쓰며, 이때는 "다음 날 열림" 단계 없이 요약만 보여준 뒤 곧바로 결과 화면으로 넘어감.
- **엔딩 에필로그**: 결과 화면에서 `ending_type`(BEST/NORMAL/BAD)과, BAD인 경우
  `ending_reason`(INFECTION_SPREAD/TRUST_COLLAPSE/IDLE_TIMEOUT)으로 분기 — 각각 BAD-A/B/C
  텍스트(`resolveEndingEpilogue()`). `ending_reason`은 스토리 텍스트만을 위해 새로 추가한 컬럼은
  아니고, 애초에 세 가지 조기·정상 종료 사유를 구분하기 위해 `games`/`game_results`에 도입된
  필드다(이 컬럼 추가는 위 002 마이그레이션과 달리 003 마이그레이션에서 실제 스키마 변경으로
  처리했다 — 이 프로젝트에서 "스키마 변경 없음" 원칙은 스토리 텍스트 자체에는 적용되지만, 유휴
  타임아웃처럼 새 게임 메커닉이 필요한 기능에는 적용되지 않는다).

슬롯 목록의 "사건 #N"은 `games` 테이블의 전역 `id`를 그대로 노출합니다(예전 게임 목록 화면에서는
유저별로 다시 매긴 번호를 썼지만, 슬롯 목록은 여러 게임을 뒤섞어 보여줄 수 있어 실제 `gameId`가
더 명확합니다).

UI 톤: "관료적 공포" 컨셉(형광등 조명, 서류/클립보드, 판정 도장) — 색상·타이포 토큰은
`src/styles/tokens.css`, 시그니처 인터랙션(판정 도장 임팩트)은 `src/components/StampOverlay.vue`,
형광등 깜빡임은 `src/components/FlickeringLight.vue`에 있습니다. 저장 덮어쓰기·슬롯 삭제·기록
복원 등 모든 확인 다이얼로그는 `src/components/ConfirmModal.vue` 하나를 config(문구·워터마크
색상·버튼 라벨)로 채워 재사용합니다 — 화면마다 레이아웃이 같고 텍스트만 다르던 모달 4개를
통합한 것입니다. 'Special Elite'/'Noto Serif KR'/'JetBrains Mono' 폰트를 Google Fonts에서
불러오므로 최초 로드 시 인터넷 연결이 필요합니다. `prefers-reduced-motion`을 존중해 도장
임팩트·형광등 깜빡임 모두 정적 버전으로 대체됩니다.

하루가 끝날 때(3번째 방문자 판정 직후) `src/components/DayTransitionOverlay.vue`가 재생됩니다.
저장을 묻는 단계는 없습니다 — 저장은 오직 플레이 화면의 "저장" 버튼을 통해서만 이뤄지고, 일자
전환에는 저장 로직이 전혀 없습니다.

1. **정지**(0.3~0.5s, 자동): 마지막 판정 도장 임팩트가 가라앉은 직후 화면이 잠깐 완전히
   멈춥니다 — 형광등 깜빡임도 이 순간만 멈춥니다.
2. **서류철 닫힘 + 도장 3개 + 일지 텍스트**(도장은 자동, 이후 클릭 대기): 와이프 전환 후
   "N일차 종료" / 처리 인원 / 신뢰도 변화 도장이 900ms 간격으로 하나씩 찍힙니다(신뢰도 변화가
   0인 날은 `신뢰도 변화 없음 (±0)`처럼 명시적으로 표기해 "신뢰도가 0이 됐다"로 오독되지 않게
   합니다). 도장 3개가 다 찍히고 최소 1초가 더 지나면 **클릭 없이 자동으로**
   `content/story.ts`의 일지 텍스트(그날까지 누적 감염자 허가 수 기준 3단계 분기)가 페이드인으로
   나타납니다. 이 일지 텍스트 자체가 클릭 트리거이며(레이블만 있는 "확인" 버튼은 두지 않고, 서사
   텍스트를 감싼 `<button>`을 씁니다), 클릭해야만 다음 단계로 넘어갑니다.
3. **다음 날 도입 문구**(4일차 제외, 클릭 대기): "N+1일차" 타이틀과 그날의 도입 문구가
   나타나며, 이 문구도 그 자체가 클릭 트리거입니다. 이 시점에 다음 방문자를 이미 화면 뒤에
   흐릿하게(투명도 약 18%, `inert`로 상호작용 차단) 렌더링해두고, 문구를 클릭하면 짧게 페이드인해
   또렷해집니다. 4일차 마지막 방문자 판정 후에는 이 단계 없이 2단계의 일지 텍스트를 클릭하면
   곧바로 결과 화면으로 넘어갑니다.

애니메이션(1·2단계의 대기 시간)은 최초 1회(브라우저당, `localStorage` 플래그)는 스킵 불가이고
이후부터는 우측 상단 "스킵" 버튼이 나타나지만, 일지 텍스트·다음 날 도입 문구 클릭은 애니메이션이
아니라 필수 사용자 입력이라 스킵 대상이 아니며 클릭할 때까지 무기한 대기해도 정상입니다.
`prefers-reduced-motion`에서는 와이프/도장 스태거 없이 정적 카드로 즉시 대체됩니다.
"오늘 감염자 허가 수"는 서버가 감염 여부를 내려주지 않으므로, 프론트가 그날 자신이 내린
판정들(`decision`+`correct`)만으로 역산합니다(ADMIT인데 오판정이면 감염자였다는 뜻).

WSL에서 이 저장소가 `/mnt/c/...` 같은 Windows 드라이브 마운트에 있으면 파일 변경 감지(inotify)가
안 되는 경우가 있어, `vite.config.ts`에서 `server.watch.usePolling: true`로 폴링 방식을 사용합니다.
(순수 Linux 파일시스템에서는 필요 없지만 켜져 있어도 무해합니다.)
