# cdlab-chat

## 산출물 — 과제 필수 제출물 매핑

각 문서별 직접 작성 여부에 따라 이모지를 달았습니다.

- ✍️: 직접 작성한 문서
- 🤖: ai로 작성한 문서

- **실행 방법 / 환경 구성**: 본 README 의 아래 절들 (동작 검증 / PostgreSQL 재시작·초기화) 🤖
- **주요 의사결정 요약**: [docs/design-decision.md](docs/design-decision.md) ✍️
- **API 명세**: swagger ui — `http://localhost:8080/swagger-ui.html` (아래 "API 문서" 절 참조). 수동 검증 시퀀스는 [docs/manual-verification.md](docs/manual-verification.md) 🤖
- **ERD + 핵심 DDL**: [src/main/resources/db/migration/V1__init_schema.sql](src/main/resources/db/migration/V1__init_schema.sql) 🤖
- **주요 쿼리 + 인덱스 근거 + 병목 설명**: [docs/db-design.md](docs/db-design.md) ✍️
- **설계 문서** (재연결 / 중복 처리 / 확장성 / 관측 / 장애 대응): [docs/design-doc.md](docs/design-doc.md) ✍️
- **이벤트 기반 상태 복원**: [docs/event-replay.md](docs/event-replay.md) ✍️ — 리플레이 전략 / 중복 및 순서 정합성 / 복원 비용 등. timeline API 구현 자체는 `GET /api/sessions/{id}/timeline?at=...`

---

## 동작 검증

수동 테스트 절차: [docs/manual-verification.md](docs/manual-verification.md)

## API 문서 (Swagger UI)

스프링부트 기동 후 다음 주소에서 확인 가능합니다.

- swagger ui: http://localhost:8080/swagger-ui.html
- openapi 스펙 (json): http://localhost:8080/v3/api-docs

`X-User-Id` 헤더 인증은 위 경로를 우회하도록 `UserAuthenticationFilter.shouldNotFilter` 에 화이트리스트로 처리하였습니다.

## PostgreSQL 재시작 / 초기화

기동 / 재시작 (데이터 보존):

```bash
docker compose up -d         # 최초 기동 / 컨테이너 재생성
docker compose restart       # 컨테이너만 재시작 (volume 유지)
```

**완전 초기화** (Flyway V1 을 처음부터 다시 적용해야 할 때 — 예: 필수구현 단계에서 V1 을 직접 수정해 checksum mismatch 가 난 경우):

```bash
docker compose down -v       # 컨테이너 + named volume 제거 → 데이터 wipe
docker compose up -d         # 재기동 시 빈 DB 에 V1 부터 새로 적용
```

> `-v` 가 핵심. 빠뜨리면 volume(`cdlab_chat_postgres_data`) 이 살아있어 다음 부팅에서 동일한 checksum 오류가 반복된다.
