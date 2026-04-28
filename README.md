# cdlab-chat

## 동작 검증

수동 테스트 절차: [docs/manual-verification.md](docs/manual-verification.md)

## DB 컨벤션

- **외래 키(FK) 미사용**: 참조 무결성은 애플리케이션 계층(서비스 검증 / 도메인 invariant)에서 보장한다.
  - 이벤트 등 append-only / 대용량 테이블에서 FK 검증 비용을 회피
  - JSONB 내부 값은 FK 제약이 불가하므로, 일반 컬럼도 동일 정책으로 일관성 유지
