/*
===============================================================================
카페 캐치테이블 - DB 구축 스크립트

생성 대상:
  - 저장 프로시저 7개
  - 트리거 5개
  - 예약 조회 인덱스 1개
  - 노쇼 자동 처리 DBMS_SCHEDULER 작업 1개

실행 계정:
  CAFE_CATCHTABLE
===============================================================================
*/

--------------------------------------------------------------------------------
-- 1. 문서에 명시된 예약 조회 인덱스
--------------------------------------------------------------------------------

CREATE INDEX IDX_RESV_SEAT_START
    ON RESERVATION (SEAT_ID, START_DT);

--------------------------------------------------------------------------------
-- 2. 예약 테이블의 직접 DML 차단을 위한 내부 보조 패키지
--    저장 프로시저에서만 G_ALLOWED를 TRUE로 설정합니다.
--------------------------------------------------------------------------------

CREATE OR REPLACE PACKAGE PKG_RESV_DML AS
    G_ALLOWED BOOLEAN := FALSE;
END PKG_RESV_DML;
/

--------------------------------------------------------------------------------
-- 3. 저장 프로시저 7개
--------------------------------------------------------------------------------

-- 3-1. 예약 생성
-- IN : p_member_id, p_seat_id, p_start_dt, p_end_dt, p_visitor_count
-- OUT: p_reservation_id, p_result_code
CREATE OR REPLACE PROCEDURE SP_CREATE_RESERVATION (
    p_member_id      IN  MEMBER.MEMBER_ID%TYPE,
    p_seat_id        IN  SEAT.SEAT_ID%TYPE,
    p_start_dt       IN  DATE,
    p_end_dt         IN  DATE,
    p_visitor_count  IN  NUMBER,
    p_reservation_id OUT RESERVATION.RESERVATION_ID%TYPE,
    p_result_code    OUT NUMBER
) AS
    v_locked_seat    SEAT.SEAT_ID%TYPE;
    v_member_check   MEMBER.MEMBER_ID%TYPE;
    v_capacity       SEAT.CAPACITY%TYPE;
    v_seat_status    SEAT.STATUS%TYPE;
    v_cafe_id        CAFE.CAFE_ID%TYPE;
    v_start_time     CAFE.START_TIME%TYPE;
    v_end_time       CAFE.END_TIME%TYPE;
    v_operating_days CAFE.OPERATING_DAYS%TYPE;
    v_day_code       VARCHAR2(3);
    v_open_dt        DATE;
    v_close_dt       DATE;
    v_count          NUMBER;
BEGIN
    p_reservation_id := NULL;
    p_result_code := 0;

    -- 시작/종료 시각의 기본 유효성
    IF p_start_dt IS NULL OR p_end_dt IS NULL OR p_end_dt <= p_start_dt THEN
        p_result_code := -20001;
        ROLLBACK;
        RETURN;
    END IF;

    -- 같은 좌석을 대상으로 한 동시 예약 요청을 직렬화
    SELECT SEAT_ID
      INTO v_locked_seat
      FROM SEAT
     WHERE SEAT_ID = p_seat_id
       FOR UPDATE;

    SELECT S.CAPACITY, S.STATUS, Z.CAFE_ID,
           C.START_TIME, C.END_TIME, C.OPERATING_DAYS
      INTO v_capacity, v_seat_status, v_cafe_id,
           v_start_time, v_end_time, v_operating_days
      FROM SEAT S
      JOIN ZONE Z ON Z.ZONE_ID = S.ZONE_ID
      JOIN CAFE C ON C.CAFE_ID = Z.CAFE_ID
     WHERE S.SEAT_ID = p_seat_id;

    -- 한 회원이 다른 좌석에 동시에 예약하는 경우도 검사할 수 있도록 회원 행 잠금
    SELECT MEMBER_ID
      INTO v_member_check
      FROM MEMBER
     WHERE MEMBER_ID = p_member_id
       FOR UPDATE;

    IF v_seat_status = '사용중지' THEN
        p_result_code := -20009;
        ROLLBACK;
        RETURN;
    END IF;

    -- 현재 이후부터 7일 이내
    IF p_start_dt <= SYSDATE OR p_start_dt > SYSDATE + 7 THEN
        p_result_code := -20002;
        ROLLBACK;
        RETURN;
    END IF;

    -- 좌석 적정인원 확인
    IF p_visitor_count IS NULL OR p_visitor_count < 1
       OR p_visitor_count > v_capacity THEN
        p_result_code := -20003;
        ROLLBACK;
        RETURN;
    END IF;

    -- 영업 요일 확인
    -- OPERATING_DAYS는 'MON,TUE,WED,THU,FRI,SAT,SUN' 형식으로 저장합니다.
    v_day_code := UPPER(TO_CHAR(p_start_dt, 'DY', 'NLS_DATE_LANGUAGE=ENGLISH'));
    IF INSTR(',' || REPLACE(UPPER(v_operating_days), ' ', '') || ',',
             ',' || v_day_code || ',') = 0 THEN
        p_result_code := -20001;
        ROLLBACK;
        RETURN;
    END IF;

    -- 영업 시작/종료 시각 확인. 자정을 넘기는 영업은 지원하지 않습니다.
    v_open_dt := TRUNC(p_start_dt)
                 + (TO_DATE(v_start_time, 'HH24:MI')
                    - TRUNC(TO_DATE(v_start_time, 'HH24:MI')));
    v_close_dt := TRUNC(p_start_dt)
                  + (TO_DATE(v_end_time, 'HH24:MI')
                     - TRUNC(TO_DATE(v_end_time, 'HH24:MI')));

    IF p_start_dt < v_open_dt OR p_end_dt > v_close_dt THEN
        p_result_code := -20001;
        ROLLBACK;
        RETURN;
    END IF;

    -- 같은 좌석의 유효 예약 시간 중복 확인
    SELECT COUNT(*)
      INTO v_count
      FROM RESERVATION R
     WHERE R.SEAT_ID = p_seat_id
       AND R.STATUS IN ('예약완료', '이용중')
       AND p_start_dt < R.END_DT
       AND p_end_dt > R.START_DT;

    IF v_count > 0 THEN
        p_result_code := -20004;
        ROLLBACK;
        RETURN;
    END IF;

    -- 같은 회원의 다른 예약과 시간 중복 확인
    SELECT COUNT(*)
      INTO v_count
      FROM RESERVATION R
     WHERE R.MEMBER_ID = p_member_id
       AND R.STATUS IN ('예약완료', '이용중')
       AND p_start_dt < R.END_DT
       AND p_end_dt > R.START_DT;

    IF v_count > 0 THEN
        p_result_code := -20005;
        ROLLBACK;
        RETURN;
    END IF;

    PKG_RESV_DML.G_ALLOWED := TRUE;

    INSERT INTO RESERVATION (
        MEMBER_ID, SEAT_ID, START_DT, END_DT,
        VISITOR_COUNT, STATUS, CREATED_AT
    ) VALUES (
        p_member_id, p_seat_id, p_start_dt, p_end_dt,
        p_visitor_count, '예약완료', SYSDATE
    ) RETURNING RESERVATION_ID INTO p_reservation_id;

    PKG_RESV_DML.G_ALLOWED := FALSE;
    COMMIT;

EXCEPTION
    WHEN NO_DATA_FOUND THEN
        PKG_RESV_DML.G_ALLOWED := FALSE;
        ROLLBACK;
        p_result_code := -20007;
    WHEN OTHERS THEN
        PKG_RESV_DML.G_ALLOWED := FALSE;
        ROLLBACK;
        IF SQLCODE BETWEEN -20010 AND -20001 THEN
            p_result_code := SQLCODE;
        ELSE
            p_result_code := -20007;
        END IF;
END;
/

-- 3-2. 예약 취소: 본인 예약이고 '예약완료'이며 입실 전인 경우만 취소
CREATE OR REPLACE PROCEDURE SP_CANCEL_RESERVATION (
    p_reservation_id IN RESERVATION.RESERVATION_ID%TYPE,
    p_member_id      IN MEMBER.MEMBER_ID%TYPE,
    p_result_code    OUT NUMBER
) AS
    v_member_id RESERVATION.MEMBER_ID%TYPE;
    v_status    RESERVATION.STATUS%TYPE;
    v_start_dt  RESERVATION.START_DT%TYPE;
BEGIN
    p_result_code := 0;

    SELECT MEMBER_ID, STATUS, START_DT
      INTO v_member_id, v_status, v_start_dt
      FROM RESERVATION
     WHERE RESERVATION_ID = p_reservation_id
       FOR UPDATE;

    IF v_member_id <> p_member_id THEN
        p_result_code := -20008;
        ROLLBACK;
        RETURN;
    END IF;

    IF v_status <> '예약완료' OR SYSDATE >= v_start_dt THEN
        p_result_code := -20007;
        ROLLBACK;
        RETURN;
    END IF;

    PKG_RESV_DML.G_ALLOWED := TRUE;
    UPDATE RESERVATION
       SET STATUS = '예약취소'
     WHERE RESERVATION_ID = p_reservation_id;
    PKG_RESV_DML.G_ALLOWED := FALSE;

    COMMIT;
EXCEPTION
    WHEN NO_DATA_FOUND THEN
        PKG_RESV_DML.G_ALLOWED := FALSE;
        ROLLBACK;
        p_result_code := -20007;
    WHEN OTHERS THEN
        PKG_RESV_DML.G_ALLOWED := FALSE;
        ROLLBACK;
        IF SQLCODE BETWEEN -20010 AND -20001 THEN
            p_result_code := SQLCODE;
        ELSE
            p_result_code := -20007;
        END IF;
END;
/

-- 3-3. 체크인: 입실예정 10분 전부터 15분 후까지
CREATE OR REPLACE PROCEDURE SP_CHECK_IN (
    p_reservation_id IN RESERVATION.RESERVATION_ID%TYPE,
    p_member_id      IN MEMBER.MEMBER_ID%TYPE,
    p_result_code    OUT NUMBER
) AS
    v_member_id RESERVATION.MEMBER_ID%TYPE;
    v_start_dt  RESERVATION.START_DT%TYPE;
    v_status    RESERVATION.STATUS%TYPE;
BEGIN
    p_result_code := 0;

    SELECT MEMBER_ID, START_DT, STATUS
      INTO v_member_id, v_start_dt, v_status
      FROM RESERVATION
     WHERE RESERVATION_ID = p_reservation_id
       FOR UPDATE;

    IF v_member_id <> p_member_id THEN
        p_result_code := -20008;
        ROLLBACK;
        RETURN;
    END IF;

    IF v_status <> '예약완료' THEN
        p_result_code := -20007;
        ROLLBACK;
        RETURN;
    END IF;

    IF SYSDATE < v_start_dt - (10 / 1440)
       OR SYSDATE > v_start_dt + (15 / 1440) THEN
        p_result_code := -20006;
        ROLLBACK;
        RETURN;
    END IF;

    PKG_RESV_DML.G_ALLOWED := TRUE;
    UPDATE RESERVATION
       SET ACTUAL_IN = SYSDATE,
           STATUS = '이용중'
     WHERE RESERVATION_ID = p_reservation_id;
    PKG_RESV_DML.G_ALLOWED := FALSE;

    COMMIT;
EXCEPTION
    WHEN NO_DATA_FOUND THEN
        PKG_RESV_DML.G_ALLOWED := FALSE;
        ROLLBACK;
        p_result_code := -20007;
    WHEN OTHERS THEN
        PKG_RESV_DML.G_ALLOWED := FALSE;
        ROLLBACK;
        IF SQLCODE BETWEEN -20010 AND -20001 THEN
            p_result_code := SQLCODE;
        ELSE
            p_result_code := -20007;
        END IF;
END;
/

-- 3-4. 퇴실: ACTUAL_OUT 기록 및 '이용완료' 변경
CREATE OR REPLACE PROCEDURE SP_CHECK_OUT (
    p_reservation_id IN RESERVATION.RESERVATION_ID%TYPE,
    p_member_id      IN MEMBER.MEMBER_ID%TYPE,
    p_result_code    OUT NUMBER
) AS
    v_member_id RESERVATION.MEMBER_ID%TYPE;
    v_status    RESERVATION.STATUS%TYPE;
BEGIN
    p_result_code := 0;

    SELECT MEMBER_ID, STATUS
      INTO v_member_id, v_status
      FROM RESERVATION
     WHERE RESERVATION_ID = p_reservation_id
       FOR UPDATE;

    IF v_member_id <> p_member_id THEN
        p_result_code := -20008;
        ROLLBACK;
        RETURN;
    END IF;

    IF v_status <> '이용중' THEN
        p_result_code := -20007;
        ROLLBACK;
        RETURN;
    END IF;

    PKG_RESV_DML.G_ALLOWED := TRUE;
    UPDATE RESERVATION
       SET ACTUAL_OUT = SYSDATE,
           STATUS = '이용완료'
     WHERE RESERVATION_ID = p_reservation_id;
    PKG_RESV_DML.G_ALLOWED := FALSE;

    COMMIT;
EXCEPTION
    WHEN NO_DATA_FOUND THEN
        PKG_RESV_DML.G_ALLOWED := FALSE;
        ROLLBACK;
        p_result_code := -20007;
    WHEN OTHERS THEN
        PKG_RESV_DML.G_ALLOWED := FALSE;
        ROLLBACK;
        IF SQLCODE BETWEEN -20010 AND -20001 THEN
            p_result_code := SQLCODE;
        ELSE
            p_result_code := -20007;
        END IF;
END;
/

-- 3-5. 노쇼 처리: 입실예정 + 15분이 지났고 아직 '예약완료'인 예약 변경
CREATE OR REPLACE PROCEDURE SP_EXPIRE_NOSHOW AS
BEGIN
    PKG_RESV_DML.G_ALLOWED := TRUE;

    UPDATE RESERVATION
       SET STATUS = '노쇼'
     WHERE STATUS = '예약완료'
       AND START_DT + (15 / 1440) < SYSDATE;

    PKG_RESV_DML.G_ALLOWED := FALSE;
    COMMIT;
EXCEPTION
    WHEN OTHERS THEN
        PKG_RESV_DML.G_ALLOWED := FALSE;
        ROLLBACK;
        RAISE;
END;
/

-- 3-6. 카페의 좌석별 1시간 예약 가능 슬롯 조회
-- 반환: SEAT_ID, SEAT_NUM, ZONE_NAME, SLOT_START, AVAILABLE(Y/N)
CREATE OR REPLACE PROCEDURE SP_GET_AVAILABLE_SLOTS (
    p_cafe_id       IN CAFE.CAFE_ID%TYPE,
    p_date          IN DATE,
    p_visitor_count IN NUMBER,
    p_cursor        OUT SYS_REFCURSOR
) AS
    v_start_time CAFE.START_TIME%TYPE;
    v_end_time   CAFE.END_TIME%TYPE;
    v_days       CAFE.OPERATING_DAYS%TYPE;
    v_day_code   VARCHAR2(3);
    v_open_dt    DATE;
    v_close_dt   DATE;
BEGIN
    SELECT START_TIME, END_TIME, OPERATING_DAYS
      INTO v_start_time, v_end_time, v_days
      FROM CAFE
     WHERE CAFE_ID = p_cafe_id;

    v_day_code := UPPER(TO_CHAR(p_date, 'DY', 'NLS_DATE_LANGUAGE=ENGLISH'));

    IF INSTR(',' || REPLACE(UPPER(v_days), ' ', '') || ',',
             ',' || v_day_code || ',') = 0 THEN
        OPEN p_cursor FOR
            SELECT CAST(NULL AS NUMBER)       AS SEAT_ID,
                   CAST(NULL AS VARCHAR2(20)) AS SEAT_NUM,
                   CAST(NULL AS VARCHAR2(50)) AS ZONE_NAME,
                   CAST(NULL AS DATE)         AS SLOT_START,
                   CAST(NULL AS VARCHAR2(1))  AS AVAILABLE
              FROM DUAL
             WHERE 1 = 0;
        RETURN;
    END IF;

    v_open_dt := TRUNC(p_date)
                 + (TO_DATE(v_start_time, 'HH24:MI')
                    - TRUNC(TO_DATE(v_start_time, 'HH24:MI')));
    v_close_dt := TRUNC(p_date)
                  + (TO_DATE(v_end_time, 'HH24:MI')
                     - TRUNC(TO_DATE(v_end_time, 'HH24:MI')));

    OPEN p_cursor FOR
        SELECT S.SEAT_ID,
               S.SEAT_NUM,
               Z.ZONE_NAME,
               SLOT_DATA.SLOT_START,
               CASE
                   WHEN S.CAPACITY < p_visitor_count THEN 'N'
                   WHEN EXISTS (
                       SELECT 1
                         FROM RESERVATION R
                        WHERE R.SEAT_ID = S.SEAT_ID
                          AND R.STATUS IN ('예약완료', '이용중')
                          AND SLOT_DATA.SLOT_START < R.END_DT
                          AND SLOT_DATA.SLOT_START + (1 / 24) > R.START_DT
                   ) THEN 'N'
                   ELSE 'Y'
               END AS AVAILABLE
          FROM SEAT S
          JOIN ZONE Z ON Z.ZONE_ID = S.ZONE_ID
          CROSS JOIN (
              SELECT v_open_dt + (LEVEL - 1) / 24 AS SLOT_START
                FROM DUAL
              CONNECT BY LEVEL <= FLOOR((v_close_dt - v_open_dt) * 24)
          ) SLOT_DATA
         WHERE Z.CAFE_ID = p_cafe_id
           AND S.STATUS <> '사용중지'
           AND SLOT_DATA.SLOT_START > SYSDATE
         ORDER BY Z.ZONE_NAME, S.SEAT_NUM, SLOT_DATA.SLOT_START;
END;
/

-- 3-7. 구역별 통계 조회
-- 반환: ZONE_NAME, RESV_COUNT, AVG_STAY_MIN, OCCUPANCY_RATE,
--       TURNOVER, NOSHOW_RATE
CREATE OR REPLACE PROCEDURE SP_ZONE_STATS (
    p_cafe_id IN CAFE.CAFE_ID%TYPE,
    p_from    IN DATE,
    p_to      IN DATE,
    p_cursor  OUT SYS_REFCURSOR
) AS
BEGIN
    IF p_from IS NULL OR p_to IS NULL OR p_to <= p_from THEN
        OPEN p_cursor FOR
            SELECT CAST(NULL AS VARCHAR2(50)) AS ZONE_NAME,
                   CAST(NULL AS NUMBER) AS RESV_COUNT,
                   CAST(NULL AS NUMBER) AS AVG_STAY_MIN,
                   CAST(NULL AS NUMBER) AS OCCUPANCY_RATE,
                   CAST(NULL AS NUMBER) AS TURNOVER,
                   CAST(NULL AS NUMBER) AS NOSHOW_RATE
              FROM DUAL
             WHERE 1 = 0;
        RETURN;
    END IF;

    OPEN p_cursor FOR
        WITH CAFE_INFO AS (
            SELECT START_TIME, END_TIME, OPERATING_DAYS
              FROM CAFE
             WHERE CAFE_ID = p_cafe_id
        ),
        CALENDAR_DAYS AS (
            SELECT TRUNC(p_from) + LEVEL - 1 AS WORK_DATE
              FROM DUAL
            CONNECT BY LEVEL <= TRUNC(p_to) - TRUNC(p_from) + 1
        ),
        OPEN_HOURS AS (
            SELECT NVL(SUM(
                       GREATEST(
                           0,
                           (LEAST(
                                D.WORK_DATE + (TO_DATE(CI.END_TIME, 'HH24:MI')
                                - TRUNC(TO_DATE(CI.END_TIME, 'HH24:MI'))),
                                p_to
                            ) - GREATEST(
                                D.WORK_DATE + (TO_DATE(CI.START_TIME, 'HH24:MI')
                                - TRUNC(TO_DATE(CI.START_TIME, 'HH24:MI'))),
                                p_from
                            )) * 24
                       )
                   ), 0) AS TOTAL_OPEN_HOURS
              FROM CALENDAR_DAYS D
              CROSS JOIN CAFE_INFO CI
             WHERE INSTR(',' || REPLACE(UPPER(CI.OPERATING_DAYS), ' ', '') || ',',
                         ',' || UPPER(TO_CHAR(D.WORK_DATE, 'DY',
                                               'NLS_DATE_LANGUAGE=ENGLISH')) || ',') > 0
        ),
        ZONE_SEATS AS (
            SELECT Z.ZONE_ID,
                   Z.ZONE_NAME,
                   COUNT(S.SEAT_ID) AS SEAT_COUNT
              FROM ZONE Z
              LEFT JOIN SEAT S ON S.ZONE_ID = Z.ZONE_ID
             WHERE Z.CAFE_ID = p_cafe_id
             GROUP BY Z.ZONE_ID, Z.ZONE_NAME
        ),
        RESV AS (
            SELECT S.ZONE_ID,
                   COUNT(*) AS RESV_COUNT,
                   AVG(CASE WHEN R.STATUS = '이용완료'
                            THEN (R.ACTUAL_OUT - R.ACTUAL_IN) * 1440
                       END) AS AVG_STAY_MIN,
                   SUM(CASE WHEN R.STATUS IN ('예약완료', '이용중', '이용완료')
                            THEN GREATEST(0,
                                 (LEAST(R.END_DT, p_to)
                                  - GREATEST(R.START_DT, p_from)) * 24)
                            ELSE 0
                       END) AS RESERVED_HOURS,
                   SUM(CASE WHEN R.STATUS = '이용완료' THEN 1 ELSE 0 END)
                       AS COMPLETED_COUNT,
                   SUM(CASE WHEN R.STATUS = '노쇼' THEN 1 ELSE 0 END)
                       AS NOSHOW_COUNT
              FROM RESERVATION R
              JOIN SEAT S ON S.SEAT_ID = R.SEAT_ID
              JOIN ZONE Z ON Z.ZONE_ID = S.ZONE_ID
             WHERE Z.CAFE_ID = p_cafe_id
               AND R.START_DT < p_to
               AND R.END_DT > p_from
             GROUP BY S.ZONE_ID
        )
        SELECT ZS.ZONE_NAME,
               NVL(R.RESV_COUNT, 0) AS RESV_COUNT,
               ROUND(R.AVG_STAY_MIN, 1) AS AVG_STAY_MIN,
               ROUND(
                   CASE
                       WHEN ZS.SEAT_COUNT = 0 OR OH.TOTAL_OPEN_HOURS = 0 THEN 0
                       ELSE NVL(R.RESERVED_HOURS, 0)
                            / (ZS.SEAT_COUNT * OH.TOTAL_OPEN_HOURS) * 100
                   END, 2
               ) AS OCCUPANCY_RATE,
               ROUND(NVL(R.COMPLETED_COUNT, 0)
                     / NULLIF(ZS.SEAT_COUNT, 0), 2) AS TURNOVER,
               ROUND(NVL(R.NOSHOW_COUNT, 0)
                     / NULLIF(NVL(R.RESV_COUNT, 0), 0) * 100, 2) AS NOSHOW_RATE
          FROM ZONE_SEATS ZS
          CROSS JOIN OPEN_HOURS OH
          LEFT JOIN RESV R ON R.ZONE_ID = ZS.ZONE_ID
         ORDER BY ZS.ZONE_NAME;
END;
/

--------------------------------------------------------------------------------
-- 4. 트리거 5개
--------------------------------------------------------------------------------

-- 4-1. 예약 INSERT/UPDATE 검증 및 프로시저를 통하지 않은 직접 DML 차단
CREATE OR REPLACE TRIGGER TRG_RESV_VALIDATE
BEFORE INSERT OR UPDATE ON RESERVATION
FOR EACH ROW
DECLARE
    v_capacity SEAT.CAPACITY%TYPE;
    v_seat_status SEAT.STATUS%TYPE;
BEGIN
    IF NOT PKG_RESV_DML.G_ALLOWED THEN
        RAISE_APPLICATION_ERROR(
            -20007,
            '예약은 지정된 저장프로시저로 처리해야 합니다'
        );
    END IF;

    IF :NEW.END_DT <= :NEW.START_DT THEN
        RAISE_APPLICATION_ERROR(-20001, '영업시간 안에서만 예약할 수 있습니다');
    END IF;

    SELECT CAPACITY, STATUS
      INTO v_capacity, v_seat_status
      FROM SEAT
     WHERE SEAT_ID = :NEW.SEAT_ID;

    IF :NEW.VISITOR_COUNT < 1 OR :NEW.VISITOR_COUNT IS NULL
       OR :NEW.VISITOR_COUNT > v_capacity THEN
        RAISE_APPLICATION_ERROR(-20003, '이 좌석의 적정인원을 넘었습니다');
    END IF;

    IF v_seat_status = '사용중지' THEN
        RAISE_APPLICATION_ERROR(-20009, '현재 운영하지 않는 좌석입니다');
    END IF;
END;
/

-- 4-2. 예약 상태 변경에 따른 좌석 상태 동기화
CREATE OR REPLACE TRIGGER TRG_RESV_SEAT_SYNC
AFTER UPDATE OF STATUS ON RESERVATION
FOR EACH ROW
BEGIN
    IF :NEW.STATUS = '이용중' THEN
        UPDATE SEAT
           SET STATUS = '이용중'
         WHERE SEAT_ID = :NEW.SEAT_ID;
    ELSIF :NEW.STATUS IN ('이용완료', '예약취소', '노쇼') THEN
        UPDATE SEAT
           SET STATUS = '빈자리'
         WHERE SEAT_ID = :NEW.SEAT_ID;
    END IF;
END;
/

-- 4-3. 예약 생성/수정/삭제 이력 기록
CREATE OR REPLACE TRIGGER TRG_RESV_HISTORY
AFTER INSERT OR UPDATE OR DELETE ON RESERVATION
FOR EACH ROW
BEGIN
    IF INSERTING THEN
        INSERT INTO RESERVATION_LOG (
            RESERVATION_ID, ACTION, OLD_STATUS, NEW_STATUS, CHANGED_AT
        ) VALUES (
            :NEW.RESERVATION_ID, 'INSERT', NULL, :NEW.STATUS, SYSDATE
        );
    ELSIF UPDATING THEN
        INSERT INTO RESERVATION_LOG (
            RESERVATION_ID, ACTION, OLD_STATUS, NEW_STATUS, CHANGED_AT
        ) VALUES (
            :NEW.RESERVATION_ID, 'UPDATE', :OLD.STATUS, :NEW.STATUS, SYSDATE
        );
    ELSIF DELETING THEN
        INSERT INTO RESERVATION_LOG (
            RESERVATION_ID, ACTION, OLD_STATUS, NEW_STATUS, CHANGED_AT
        ) VALUES (
            :OLD.RESERVATION_ID, 'DELETE', :OLD.STATUS, NULL, SYSDATE
        );
    END IF;
END;
/

-- 4-4. 미래 유효 예약이 남아 있는 좌석 삭제 방지
-- 복합 트리거로 변경:
-- 1) BEFORE STATEMENT에서 예약이 유효한 좌석 ID를 미리 수집합니다.
-- 2) BEFORE EACH ROW에서는 RESERVATION을 다시 조회하지 않고 수집값만 확인합니다.
-- 따라서 FK_RESV_SEAT의 ON DELETE CASCADE와 함께 실행되어도
-- 행 단위 트리거의 mutating table(ORA-04091) 오류를 피할 수 있습니다.
CREATE OR REPLACE TRIGGER TRG_SEAT_DELETE_GUARD
FOR DELETE ON SEAT
COMPOUND TRIGGER
    TYPE t_active_seat_ids IS TABLE OF BOOLEAN INDEX BY VARCHAR2(100);
    g_active_seat_ids t_active_seat_ids;

    BEFORE STATEMENT IS
    BEGIN
        -- 현재 이후 종료 시각이 남아 있고 예약완료/이용중인 좌석을 미리 수집합니다.
        FOR r IN (
            SELECT DISTINCT SEAT_ID
              FROM RESERVATION
             WHERE STATUS IN ('예약완료', '이용중')
               AND END_DT > SYSDATE
        ) LOOP
            g_active_seat_ids(TO_CHAR(r.SEAT_ID)) := TRUE;
        END LOOP;
    END BEFORE STATEMENT;

    BEFORE EACH ROW IS
    BEGIN
        IF g_active_seat_ids.EXISTS(TO_CHAR(:OLD.SEAT_ID)) THEN
            RAISE_APPLICATION_ERROR(-20010, '예약이 남아 있는 좌석입니다');
        END IF;
    END BEFORE EACH ROW;
END TRG_SEAT_DELETE_GUARD;
/

-- 4-5. OWNER 등록 시 해당 회원의 ROLE을 OWNER로 변경
CREATE OR REPLACE TRIGGER TRG_OWNER_ROLE
AFTER INSERT ON OWNER
FOR EACH ROW
BEGIN
    UPDATE MEMBER
       SET ROLE = 'OWNER'
     WHERE MEMBER_ID = :NEW.OWNER_ID;
END;
/

--------------------------------------------------------------------------------
-- 5. 노쇼 자동 처리 스케줄러: 5분 간격 실행
--------------------------------------------------------------------------------

BEGIN
    DBMS_SCHEDULER.CREATE_JOB(
        JOB_NAME        => 'JOB_EXPIRE_NOSHOW',
        JOB_TYPE        => 'STORED_PROCEDURE',
        JOB_ACTION      => 'SP_EXPIRE_NOSHOW',
        START_DATE      => SYSTIMESTAMP,
        REPEAT_INTERVAL => 'FREQ=MINUTELY;INTERVAL=5',
        ENABLED         => TRUE,
        COMMENTS        => '5분마다 체크인하지 않은 예약을 노쇼 처리'
    );
END;
/

--------------------------------------------------------------------------------
-- 6. 생성 결과 및 컴파일 오류 확인
--------------------------------------------------------------------------------

SELECT OBJECT_TYPE, OBJECT_NAME, STATUS
  FROM USER_OBJECTS
 WHERE OBJECT_NAME IN (
    'MEMBER', 'OWNER', 'CAFE', 'ZONE', 'SEAT', 'RESERVATION', 'RESERVATION_LOG',
    'IDX_RESV_SEAT_START',
    'PKG_RESV_DML',
    'SP_CREATE_RESERVATION', 'SP_CANCEL_RESERVATION', 'SP_CHECK_IN',
    'SP_CHECK_OUT', 'SP_EXPIRE_NOSHOW', 'SP_GET_AVAILABLE_SLOTS', 'SP_ZONE_STATS',
    'TRG_RESV_VALIDATE', 'TRG_RESV_SEAT_SYNC', 'TRG_RESV_HISTORY',
    'TRG_SEAT_DELETE_GUARD', 'TRG_OWNER_ROLE', 'JOB_EXPIRE_NOSHOW'
 )
 ORDER BY OBJECT_TYPE, OBJECT_NAME;

SELECT NAME, TYPE, LINE, POSITION, TEXT
  FROM USER_ERRORS
 ORDER BY NAME, SEQUENCE;

/*
문서의 오류 코드:
  -20001 영업일·영업시간 밖
  -20002 과거 또는 7일 초과
  -20003 인원 > 적정인원
  -20004 좌석 시간 중복
  -20005 회원의 동시간 예약 존재
  -20006 체크인 허용 시간 밖
  -20007 현재 상태에서 불가한 동작
  -20008 본인 예약 아님
  -20009 사용중지 좌석
  -20010 예약 남은 좌석 삭제

프로시저 성공 결과 코드는 0입니다.
*/



-- 회원 2명 (비밀번호: test1234)
INSERT INTO MEMBER (MEMBER_ID, PASSWORD, NAME, PHONE)
VALUES ('user01', '$2a$10$OjewBF9Fq5BCqjUsIVwYvO4qTp2ZN8PBDaiaen3EzVTBW/Xmau59K', '김회원', '010-1111-1111');
INSERT INTO MEMBER (MEMBER_ID, PASSWORD, NAME, PHONE)
VALUES ('owner01', '$2a$10$OjewBF9Fq5BCqjUsIVwYvO4qTp2ZN8PBDaiaen3EzVTBW/Xmau59K', '박점주', '010-2222-2222');

-- 점주 등록 → TRG_OWNER_ROLE 이 owner01 의 ROLE 을 'OWNER' 로 바꿈
INSERT INTO OWNER (OWNER_ID, BUSINESS_NUM) VALUES ('owner01', '123-45-00001');

-- 카페 1개 (매일 09:00~23:00)
INSERT INTO CAFE (OWNER_ID, CAFE_NAME, ADDRESS, PHONE, START_TIME, END_TIME, OPERATING_DAYS)
VALUES ('owner01', '서면 전포 스터디랩', '부산 부산진구 전포대로 1', '051-000-0000',
        '09:00', '23:00', 'Mon,Tue,Wed,Thu,Fri,Sat,Sun');

-- 구역 2개
INSERT INTO ZONE (CAFE_ID, ZONE_NAME)
SELECT CAFE_ID, '오픈형 스터디존' FROM CAFE WHERE CAFE_NAME = '서면 전포 스터디랩';
INSERT INTO ZONE (CAFE_ID, ZONE_NAME)
SELECT CAFE_ID, '집중형 독서실존' FROM CAFE WHERE CAFE_NAME = '서면 전포 스터디랩';

-- 좌석
INSERT INTO SEAT (ZONE_ID, SEAT_NUM, SEAT_TYPE, CAPACITY, EXTRA_ITEM)
SELECT ZONE_ID, 'O-01', '4인 테이블', 4, '콘센트 2구' FROM ZONE WHERE ZONE_NAME = '오픈형 스터디존';
INSERT INTO SEAT (ZONE_ID, SEAT_NUM, SEAT_TYPE, CAPACITY, EXTRA_ITEM)
SELECT ZONE_ID, 'O-02', '2인 테이블', 2, NULL FROM ZONE WHERE ZONE_NAME = '오픈형 스터디존';
INSERT INTO SEAT (ZONE_ID, SEAT_NUM, SEAT_TYPE, CAPACITY, EXTRA_ITEM)
SELECT ZONE_ID, 'F-01', '1인석', 1, '콘센트, 스탠드' FROM ZONE WHERE ZONE_NAME = '집중형 독서실존';

COMMIT;

-- 확인: owner01 의 ROLE 이 OWNER 로 바뀌었는지
SELECT MEMBER_ID, ROLE FROM MEMBER;

SET SERVEROUTPUT ON;
DECLARE
    v_seat_id SEAT.SEAT_ID%TYPE;
    v_resv_id NUMBER;
    v_code    NUMBER;
BEGIN
    SELECT SEAT_ID INTO v_seat_id FROM SEAT WHERE SEAT_NUM = 'O-01' AND ROWNUM = 1;

    SP_CREATE_RESERVATION(
        p_member_id      => 'user01',
        p_seat_id        => v_seat_id,
        p_start_dt       => TRUNC(SYSDATE) + 1 + 14/24,
        p_end_dt         => TRUNC(SYSDATE) + 1 + 16/24,
        p_visitor_count  => 2,
        p_reservation_id => v_resv_id,
        p_result_code    => v_code
    );
    DBMS_OUTPUT.PUT_LINE('reservation_id = ' || v_resv_id || ', result_code = ' || v_code);
END;
/
-- 기대 결과: result_code = 0. 같은 블록을 한 번 더 실행하면 -20004(시간 중복)

SELECT * FROM RESERVATION;
SELECT * FROM RESERVATION_LOG;   -- TRG_RESV_HISTORY 가 'INSERT' 이력을 남김


