# jdbc 패키지 안내

위치: `src/main/java/academic/cafecatchtable/jdbc/`

Oracle 저장 프로시저를 Java에서 **호출**하는 코드를 모아 둔 곳입니다.
프로시저 본체(검사·INSERT·UPDATE·SELECT)는 SQL Developer로 DB에 만들어 두었고, 이 패키지는 그것을 실행시키고 결과를 받아 오는 일만 합니다.
트리거는 DB가 알아서 실행하므로 여기에 트리거를 부르는 코드는 없습니다.

---

## 1. 이름이 두 종류인 이유

이 폴더에는 **하는 일이 다른 두 종류의 파일**이 있습니다. 이름 끝이 그 역할을 나타냅니다.

| 이름 끝 | 종류 | 하는 일 | 비유 |
|---|---|---|---|
| `~Caller` | class (일하는 클래스) | 프로시저를 **호출**한다. `{call SP_...}` 실행 → OUT 값 꺼내기 | 주문하러 가는 사람 |
| `~Result`, `~Row` | record (데이터 그릇) | 프로시저가 **돌려준 값**을 담아 서비스로 넘긴다. 로직 없음 | 받아 온 영수증·물건 |

그릇 쪽은 프로시저가 값을 **어떻게 돌려주느냐**에 따라 다시 둘로 나뉩니다.

| 이름 끝 | 프로시저가 돌려주는 방식 | 개수 | 예 |
|---|---|---|---|
| `~Result` | **OUT 파라미터** 여러 개 (숫자·문자 값) | 호출 1번에 1개 | `CreateReservationResult` = p_reservation_id + p_result_code |
| `~Row` | **커서(SYS_REFCURSOR)** 의 한 행 | 호출 1번에 여러 개 (`List<~Row>`) | `SlotRow` = 시간대 커서 한 줄 |

```
서비스 ──호출──▶ ~Caller ──{call SP_...}──▶ Oracle 프로시저
서비스 ◀─────── ~Result / List<~Row> ◀──── OUT 값 / 커서
```

> 취소·체크인·퇴실처럼 OUT 이 결과 코드 숫자 하나뿐이면 그릇을 따로 만들지 않고 `int` 로 돌려줍니다.
> 점주 조회(`OwnerProcedureCaller`)는 화면 응답과 모양이 같아서 그릇을 새로 만들지 않고 `dto/OwnerDto` 의 응답 record 에 바로 담습니다.

---

## 2. 파일별 설명

### Caller (프로시저 호출 클래스)

| 파일 | 호출하는 프로시저 | 쓰는 API | 돌려주는 것 | 상태 |
|---|---|---|---|---|
| `ReservationProcedureCaller` | `SP_CREATE_RESERVATION` | R-01 예약 생성 | `CreateReservationResult` | ✅ 구현 |
| | `SP_CANCEL_RESERVATION` | R-04 예약 취소 | `int` 결과 코드 | ⬜ 미구현 (501) |
| | `SP_CHECK_IN` | R-05 체크인 | `int` 결과 코드 | ⬜ 미구현 (501) |
| | `SP_CHECK_OUT` | R-06 퇴실 | `int` 결과 코드 | ⬜ 미구현 (501) |
| `SlotProcedureCaller` | `SP_GET_AVAILABLE_SLOTS` | C-04 예약 가능 시간대 | `List<SlotRow>` | ⬜ 미구현 (501) |
| `StatsProcedureCaller` | `SP_ZONE_STATS` | O-12 구역 통계 | `List<ZoneStatRow>` | ⬜ 미구현 (501) |
| `OwnerProcedureCaller` | `SP_OWNER_DAILY_RESERVATIONS` | O-10 일일 예약 | `List<OwnerReservationResponse>` | ✅ 구현 |
| | `SP_OWNER_RESERVATION_DETAIL` | O-11 예약자 상세 | `Optional<OwnedReservation>` | ✅ 구현 |
| | `SP_POPULAR_SEATS` | O-12 인기 좌석 TOP 3 | `List<PopularSeat>` | ✅ 구현 |

- 미구현 메서드는 `UnsupportedOperationException` 을 던지고, API 는 `501 NOT_IMPLEMENTED` 로 응답합니다.
- 각 메서드 위 주석에 파라미터 순서·타입, 프로시저 검사 순서, 함께 실행되는 트리거가 적혀 있습니다.
- `OwnerProcedureCaller.OwnedReservation` 은 예약 정보 + 그 카페의 점주 ID 를 묶은 record 입니다. 서비스가 "내 카페 예약인지" 검사(아니면 403)하는 데 씁니다.

### Result / Row (결과 그릇 record)

| 파일 | 담는 값 | 출처 |
|---|---|---|
| `CreateReservationResult` | `reservationId` (실패 시 null), `resultCode` (성공 0, 실패 -20001 ~ -20010) | SP_CREATE_RESERVATION 의 OUT 6, 7 |
| `SlotRow` | `seatId`, `seatNum`, `zoneName`, `slotStart`, `available` | SP_GET_AVAILABLE_SLOTS 커서 한 행 |
| `ZoneStatRow` | `zoneName`, `resvCount`, `avgStayMin`, `occupancyRate`(%), `turnover`, `noshowRate`(%) | SP_ZONE_STATS 커서 한 행 |

---

## 3. 새 프로시저 호출을 작성하는 방법

`ReservationProcedureCaller.createReservation()` 이 기준 예시입니다. 규칙은 하나입니다.

- **JDBC 코드는 Caller 메서드 안에**, 결과 record(`~Result`, `~Row`)는 **값만** 담는다.

```java
try (Connection con = dataSource.getConnection();          // application.properties 의 접속 설정·커넥션 풀 사용
     CallableStatement cstmt = con.prepareCall("{call SP_...(?, ?, ?)}")) {
    cstmt.setLong(1, ...);                                 // IN
    cstmt.registerOutParameter(3, Types.INTEGER);          // OUT (커서면 Types.REF_CURSOR)
    cstmt.execute();
    return cstmt.getInt(3);                                // 커서면 cstmt.getObject(3, ResultSet.class)
} catch (SQLException e) {
    throw new RuntimeException("SP_... 호출 실패", e);     // 접속 오류 등 → 500
}
```

- 상태를 바꾸는 프로시저(예약 생성·취소·체크인·퇴실)는 안에서 직접 COMMIT 하므로 autoCommit 기본값 그대로 씁니다.
- 결과 코드 규칙 위반(-20001 ~ -20010)은 예외가 아니라 OUT 값으로 오므로 그대로 return 하면 서비스가 HTTP 오류로 바꿉니다.

---

## 4. 다른 폴더와의 차이

| 폴더 | 내용 | DB 접근 방식 |
|---|---|---|
| `repository/` | 회원·카페·구역·좌석 등 단순 저장·조회 | JPA (SQL 을 Spring 이 생성) |
| `jdbc/` | 저장 프로시저 호출 | JDBC `CallableStatement` (`{call SP_...}`) |
| `dto/` | 화면(API) 요청·응답 모양 | DB 접근 없음 |

## 5. 테스트

- `src/test/java/.../jdbc/ProcedureCallerContractTest` : Caller 가 실제 프로시저를 불러 결과 코드·커서를 제대로 받는지 검증합니다.
  미구현 메서드의 테스트는 "건너뜀"으로 표시되고, 구현하면 자동으로 실행됩니다.
- 프로시저가 직접 COMMIT 하므로 이 테스트는 `zzc` 로 시작하는 전용 데이터를 만들었다가 끝나면 지웁니다.
