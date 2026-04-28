# 수동 테스트 방법

본 문서는 cdlab-chat 의 동작을 수동으로 검증하는 절차를 도메인별로 정리한다.
도메인이 늘어나면 섹션을 아래로 추가한다.

## 사전 조건

```bash
# 1. PostgreSQL 컨테이너 기동
docker compose up -d

# 2. 애플리케이션 기동
./gradlew bootRun
```

애플리케이션 기본 포트는 `8080`.

---

## common

### Healthcheck — `GET /api/health`

DB roundtrip(`SELECT 1`) 후 정수 `1` 을 응답한다.
일반 헬스체크는 DB 호출을 하지 않지만, 본 엔드포인트는 controller-service-repository 계층 구조 검증을 1차 목적으로 하기 때문에 의도적으로 roundtrip 을 포함한다.

```bash
curl -i http://localhost:8080/api/health
```

기대 응답:

```
HTTP/1.1 200
Content-Type: application/json
Content-Length: 1

1
```

검증 포인트:

- HTTP 200
- 응답 본문 `1` (DB 가 `SELECT 1` 을 정상 처리)
- 애플리케이션 로그에 SQL 실행 흔적 (Hibernate SQL 로깅 활성화 시)
