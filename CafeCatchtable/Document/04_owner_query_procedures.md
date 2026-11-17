# 점주 조회 저장 프로시저 (O-10 · O-11 · O-12)

`jdbc/OwnerQueryDao` 가 Java 에서 직접 실행하던 SELECT 3개를 Oracle 저장 프로시저로 옮긴 것입니다.
Java 는 이제 SQL 을 갖고 있지 않고, 아래 프로시저를 `{call ...}` 로 호출해 커서(SYS_REFCURSOR)만 읽습니다.

## 요약

| 프로시저 | 사용 API | 입력 (IN) | 출력 (OUT) | 기능 |
|---|---|---|---|---|
| `SP_OWNER_DAILY_RESERVATIONS` | O-10 일일 예약 타임라인 | `p_cafe_id` NUMBER<br>`p_date` DATE | `p_cursor` SYS_REFCURSOR<br>(예약 행 0~N개) | 선택한 날짜(00:00~24:00)에 **걸친** 카페의 모든 예약을 예약자 이름·연락처와 함께 조회. 구역 → 좌석번호 → 입실 시각 순 |
| `SP_OWNER_RESERVATION_DETAIL` | O-11 예약자 상세 | `p_reservation_id` NUMBER | `p_cursor` SYS_REFCURSOR<br>(예약 행 0~1개) | 예약 1건의 상세 + 그 카페의 `OWNER_ID`. 앱이 OWNER_ID 로 "내 카페 예약인지" 검사(아니면 403) |
| `SP_POPULAR_SEATS` | O-12 인기 좌석 TOP 3 | `p_cafe_id` NUMBER<br>`p_from` DATE (포함)<br>`p_to` DATE (**미포함**) | `p_cursor` SYS_REFCURSOR<br>(좌석 행 0~3개) | 기간 안에 입실 예정이던 예약 수가 많은 좌석 3개. '예약취소'는 제외. 동률이면 좌석번호 순 |

### 커서 열 (Java 가 읽는 이름)

**SP_OWNER_DAILY_RESERVATIONS / SP_OWNER_RESERVATION_DETAIL**

| 열 | 내용 |
|---|---|
| RESERVATION_ID, SEAT_ID, SEAT_NUM, ZONE_NAME | 예약·좌석·구역 |
| MEMBER_ID, MEMBER_NAME, PHONE | 예약자 (MEMBER.NAME 을 MEMBER_NAME 으로) |
| START_DT, END_DT, VISITOR_COUNT, STATUS | 예약 내용 |
| ACTUAL_IN, ACTUAL_OUT, CREATED_AT | 실제 입실·퇴실, 예약 시각 (없으면 NULL) |
| OWNER_ID | **DETAIL 에만** 있음 — 권한 검사용 |

**SP_POPULAR_SEATS** : `SEAT_ID, SEAT_NUM, ZONE_NAME, RESV_COUNT`

### 설계 메모
- `p_to` 미포함 경계는 기존 `SP_ZONE_STATS` 와 같은 규칙입니다. 앱은 종료일 다음날 00:00 을 넘깁니다
  (예: 4/1 ~ 4/7 조회 → `p_from = 2030-04-01 00:00`, `p_to = 2030-04-08 00:00`).
- 날짜 비교는 `START_DT`, `END_DT` 컬럼에 함수를 씌우지 않으므로 `IDX_RESV_SEAT_START` 인덱스를 그대로 쓸 수 있습니다.
- 조회 전용이라 COMMIT 이 없고, 권한(본인 카페인지) 판단은 기존처럼 앱(서비스)이 합니다.
- 조건에 맞는 행이 없으면 오류 대신 **빈 커서**를 돌려줍니다 (없는 예약 → 앱이 404).

---

## SQL (SQL Developer 에서 CAFE_CATCHTABLE 로 접속 → 전체 선택 → F5 스크립트 실행)

> 한글 상태값('예약취소')이 들어 있으므로 `DB_SETUP_GUIDE.md` 의 UTF-8 인코딩 설정을 먼저 확인하세요.

```sql
--------------------------------------------------------------------------------
-- O-10 일일 예약 타임라인
--------------------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE SP_OWNER_DAILY_RESERVATIONS (
    p_cafe_id IN  CAFE.CAFE_ID%TYPE,
    p_date    IN  DATE,
    p_cursor  OUT SYS_REFCURSOR
) AS
    v_day_start DATE := TRUNC(p_date);
BEGIN
    OPEN p_cursor FOR
        SELECT r.RESERVATION_ID, r.SEAT_ID, s.SEAT_NUM, z.ZONE_NAME,
               r.MEMBER_ID, m.NAME AS MEMBER_NAME, m.PHONE,
               r.START_DT, r.END_DT, r.VISITOR_COUNT, r.STATUS,
               r.ACTUAL_IN, r.ACTUAL_OUT, r.CREATED_AT
          FROM RESERVATION r
          JOIN SEAT   s ON s.SEAT_ID   = r.SEAT_ID
          JOIN ZONE   z ON z.ZONE_ID   = s.ZONE_ID
          JOIN MEMBER m ON m.MEMBER_ID = r.MEMBER_ID
         WHERE z.CAFE_ID  = p_cafe_id
           AND r.START_DT < v_day_start + 1     -- 그날 24:00 전에 시작하고
           AND r.END_DT   > v_day_start         -- 그날 00:00 후에 끝나는 예약
         ORDER BY z.ZONE_NAME, s.SEAT_NUM, r.START_DT;
END;
/

--------------------------------------------------------------------------------
-- O-11 예약자 상세 (+ 점주 ID)
--------------------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE SP_OWNER_RESERVATION_DETAIL (
    p_reservation_id IN  RESERVATION.RESERVATION_ID%TYPE,
    p_cursor         OUT SYS_REFCURSOR
) AS
BEGIN
    OPEN p_cursor FOR
        SELECT r.RESERVATION_ID, r.SEAT_ID, s.SEAT_NUM, z.ZONE_NAME,
               r.MEMBER_ID, m.NAME AS MEMBER_NAME, m.PHONE,
               r.START_DT, r.END_DT, r.VISITOR_COUNT, r.STATUS,
               r.ACTUAL_IN, r.ACTUAL_OUT, r.CREATED_AT,
               c.OWNER_ID
          FROM RESERVATION r
          JOIN SEAT   s ON s.SEAT_ID   = r.SEAT_ID
          JOIN ZONE   z ON z.ZONE_ID   = s.ZONE_ID
          JOIN CAFE   c ON c.CAFE_ID   = z.CAFE_ID
          JOIN MEMBER m ON m.MEMBER_ID = r.MEMBER_ID
         WHERE r.RESERVATION_ID = p_reservation_id;
END;
/

--------------------------------------------------------------------------------
-- O-12 인기 좌석 TOP 3 (p_to 미포함)
--------------------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE SP_POPULAR_SEATS (
    p_cafe_id IN  CAFE.CAFE_ID%TYPE,
    p_from    IN  DATE,
    p_to      IN  DATE,
    p_cursor  OUT SYS_REFCURSOR
) AS
BEGIN
    OPEN p_cursor FOR
        SELECT SEAT_ID, SEAT_NUM, ZONE_NAME, RESV_COUNT
          FROM (
                SELECT s.SEAT_ID, s.SEAT_NUM, z.ZONE_NAME, COUNT(*) AS RESV_COUNT
                  FROM RESERVATION r
                  JOIN SEAT s ON s.SEAT_ID = r.SEAT_ID
                  JOIN ZONE z ON z.ZONE_ID = s.ZONE_ID
                 WHERE z.CAFE_ID   = p_cafe_id
                   AND r.START_DT >= p_from
                   AND r.START_DT <  p_to
                   AND r.STATUS   <> '예약취소'
                 GROUP BY s.SEAT_ID, s.SEAT_NUM, z.ZONE_NAME
                 ORDER BY RESV_COUNT DESC, s.SEAT_NUM
               )
         WHERE ROWNUM <= 3;
END;
/

-- 컴파일 확인: 세 줄 모두 VALID 여야 합니다
SELECT OBJECT_NAME, STATUS
  FROM USER_OBJECTS
 WHERE OBJECT_NAME IN ('SP_OWNER_DAILY_RESERVATIONS', 'SP_OWNER_RESERVATION_DETAIL', 'SP_POPULAR_SEATS');
```

## SQL Developer 에서 직접 실행해 보기

```sql
SET SERVEROUTPUT ON
VARIABLE rc REFCURSOR

-- O-10: 카페 1번의 2026-11-04 예약
EXEC SP_OWNER_DAILY_RESERVATIONS(1, DATE '2026-11-04', :rc);
PRINT rc

-- O-11: 예약 1번 상세
EXEC SP_OWNER_RESERVATION_DETAIL(1, :rc);
PRINT rc

-- O-12: 카페 1번, 11/1 ~ 11/30 인기 좌석 (p_to 는 12/1 00:00)
EXEC SP_POPULAR_SEATS(1, DATE '2026-11-01', DATE '2026-12-01', :rc);
PRINT rc
```

## Java 호출 (`jdbc/OwnerQueryDao`)

```java
{call SP_OWNER_DAILY_RESERVATIONS(?, ?, ?)}   // 1 cafeId, 2 Timestamp(날짜 00:00), 3 OUT Types.REF_CURSOR
{call SP_OWNER_RESERVATION_DETAIL(?, ?)}      // 1 reservationId, 2 OUT Types.REF_CURSOR
{call SP_POPULAR_SEATS(?, ?, ?, ?)}           // 1 cafeId, 2 from 00:00, 3 (to+1일) 00:00, 4 OUT Types.REF_CURSOR
```

OUT 커서는 `cs.registerOutParameter(n, Types.REF_CURSOR)` 로 등록하고, 실행 후
`cs.getObject(n, ResultSet.class)` 로 받아 행을 읽습니다.
