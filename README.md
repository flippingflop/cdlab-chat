# cdlab-chat

## 동작 검증

수동 테스트 절차: [docs/manual-verification.md](docs/manual-verification.md)

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

## DB 컨벤션

- **외래 키(FK) 미사용**: 참조 무결성은 애플리케이션 계층(서비스 검증 / 도메인 invariant)에서 보장한다.
  - 이벤트 등 append-only / 대용량 테이블에서 FK 검증 비용을 회피
  - JSONB 내부 값은 FK 제약이 불가하므로, 일반 컬럼도 동일 정책으로 일관성 유지
