# 코드 컨벤션

본 문서는 `cdlab-chat` 프로젝트의 코드 작성 컨벤션을 정리한다.

---

## 1. 레이어 구조

```
Controller  →  Service  →  Repository  →  (DB)
   DTO          Entity         Entity
```

- **Controller**: HTTP 입출력. 인증된 사용자 주입(`@CurrentUser`), 요청 검증(`@Valid`), DTO 변환
- **Service**: 비즈니스 로직 + 트랜잭션 경계. Entity 를 반환 (DTO 변환은 Controller 책임)
- **Repository**: Spring Data JPA. 표준 메서드 + `@Query` 커스텀
- **Entity / DTO 분리**: Entity 는 Controller 응답에 직접 노출하지 않는다

---

## 2. Bean / 의존성 주입

- **생성자 주입** 만 사용. setter / field injection 금지
- `@RequiredArgsConstructor` + `final` 필드 패턴

```java
@Service
@RequiredArgsConstructor
public class SessionService {
    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;
}
```

---

## 3. Controller

- 매핑: `@RequestMapping("/api/{resource}")` — `/api` prefix 일관 유지
- 단일 메서드 = 단일 엔드포인트
- 파라미터 순서: `@CurrentUser` → `@PathVariable` → `@RequestParam` → `@Valid @RequestBody`
- 응답 래핑: `ApiResponse.of(...)`

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;

    @PostMapping
    public ApiResponse<CreateSessionResponse> create(
            @CurrentUser User currentUser,
            @Valid @RequestBody CreateSessionRequest request
    ) {
        Session session = sessionService.create(currentUser, request.getJoinerId());
        return ApiResponse.of(CreateSessionResponse.from(session));
    }
}
```

---

## 4. Service

- `@Service` + `@RequiredArgsConstructor`
- 쓰기 메서드에는 `@Transactional`, 읽기 전용은 `@Transactional(readOnly = true)`
- **메서드 본문은 단계별 주석으로 의도를 표시한다**:

```java
@Transactional
public Session create(User creator, Long joinerId) {
    // 1) 자기 자신과의 세션 차단
    if (creator.getId().equals(joinerId)) {
        throw new BusinessException(ErrorCode.SELF_SESSION_NOT_ALLOWED);
    }

    // 2) joiner 존재 확인
    if (!userRepository.existsById(joinerId)) {
        throw new BusinessException(ErrorCode.USER_NOT_FOUND);
    }

    // 3) 1쌍 1세션 사전 체크
    sessionRepository.findActiveBetween(creator.getId(), joinerId)
            .ifPresent(s -> { throw new BusinessException(ErrorCode.SESSION_ALREADY_EXISTS); });

    // 4) 신규 세션 저장 — DB partial unique 가 동시성 race 의 최종 보루
    try {
        return sessionRepository.save(new Session(creator.getId(), joinerId));
    } catch (DataIntegrityViolationException e) {
        throw new BusinessException(ErrorCode.SESSION_ALREADY_EXISTS);
    }
}
```

주석 스타일: `// N) <역할 요약>` — 단계 번호 + 한 줄. *왜* 가 비자명한 경우만 추가 한 줄.

---

## 5. DTO

### 5.1 명명 규약 (per-API DTO)

API 마다 별도의 Request / Response DTO 를 둔다. 명칭은 **컨트롤러 메서드명 prefix + 리소스 + Request/Response**:

| 컨트롤러 메서드                    | Request DTO          | Response DTO            |
|-----------------------------|----------------------|-------------------------|
| `SessionController#create`  | `CreateSessionRequest` | `CreateSessionResponse` |
| `MessageController#edit`    | `EditMessageRequest` | `EditMessageResponse`   |
| `EventController#saveEvent` | `SaveEventRequest`   | `SaveEventResponse`     |

이유:

- API 진화 시 DTO 변경이 다른 API 에 파급되지 않는다
- 응답 필드를 그 API 에 *필요한 것만* 담아 표면을 좁게 유지할 수 있다 (예: 생성 응답에서 `endedAt` 제외)

### 5.2 Request DTO

- **class + Lombok**:

```java
@Getter
@Setter
@NoArgsConstructor
public class CreateSessionRequest {

    @NotNull(message = "joinerId 는 필수입니다.")
    private Long joinerId;
}
```

- `@Setter` + `@NoArgsConstructor` 는 Jackson 역직렬화를 위한 표준 조합
- Bean Validation 어노테이션(`@NotNull`, `@Size`, ...) 은 *필드에* 부착
- 메시지는 한국어 한 문장

### 5.3 Response DTO

- **class + 불변(immutable)**:

```java
@Getter
@AllArgsConstructor
public class CreateSessionResponse {

    private final Long id;
    private final Long creatorId;
    // ... 다른 final 필드들

    public static CreateSessionResponse from(Session session) {
        return new CreateSessionResponse(
                session.getId(),
                session.getCreatorId()
                // ...
        );
    }
}
```

- 모든 필드 `final` — 응답은 한 번 만들어진 후 변경되지 않는다
- `@AllArgsConstructor` 로 생성자 노출 (Jackson 직렬화는 getter 만 사용 → setter 불필요)
- **Entity → DTO 변환은 `static from(Entity)` factory 메서드** 에서 수행

---

## 6. 예외 / ErrorCode

- 비즈니스 예외는 `BusinessException(ErrorCode)` 한 종류만 사용
- 새로운 예외 상황 발생 시 `ErrorCode` enum 에 항목 추가:

```java
SESSION_ALREADY_EXISTS(HttpStatus.CONFLICT, "동일한 두 사용자 사이의 진행 중인 세션이 이미 존재합니다."),
```

- `HttpStatus` + 한국어 메시지 한 문장
- 의미가 다른 상황은 별도 ErrorCode — 메시지만 다른 두 항목을 만들지 않는다 (응답 코드로 분기 가능해야 함)
- 예외 처리는 `GlobalExceptionHandler` 가 일괄 — 각 컨트롤러에서 try/catch 금지

---

## 7. Repository

- 표준 메서드명 컨벤션 (`findBy...`, `existsBy...`) 우선
- 복잡한 쿼리는 `@Query` (JPQL). native query 는 정말 필요할 때만
- 의도가 비자명한 쿼리에는 한 줄 주석 (예: partial unique 의미 등가성 설명)

---

## 8. 패키지 구조

도메인 단위로 묶는다. 레이어 단위로 묶지 않는다.

```
com.cdlab.cdlabchat
├── common/        # 공통 인프라 (auth, response, exception, config)
├── user/
├── session/
│   ├── dto/
│   ├── Session.java
│   ├── SessionStatus.java
│   ├── SessionRepository.java
│   ├── SessionService.java
│   └── SessionController.java
├── event/
└── message/
```

- DTO 는 도메인 패키지 하위 `dto/` 에 둔다
- 도메인 간 직접 의존은 가능 (예: SessionService 가 UserRepository 사용). 단방향 흐름 권장

---

## 9. 트랜잭션 / 영속성

- 트랜잭션 경계는 **Service 메서드** 레벨
- 동기 projection (events + messages 동시 INSERT) 은 같은 메서드 / 같은 트랜잭션
- DB 제약 (CHECK / partial unique) 은 애플리케이션 검증과 **이중 방어** — race condition 의 최종 보루

---

## 10. 시간 / ID

- 시간 타입: `java.time.Instant` (`@CreationTimestamp` 사용)
- 시간 컬럼: DB DEFAULT `CURRENT_TIMESTAMP` 도 함께 (마이그레이션 / JPA 양쪽에서 보장)
- ID: `BIGSERIAL` + `GenerationType.IDENTITY`. 응답에는 그대로 노출 (별도 외부 ID 전략 미도입 — 001 결정)

---

