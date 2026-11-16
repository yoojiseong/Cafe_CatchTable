# 카페 캐치테이블 — SQL Developer DB 구축 가이드

`Document/01_create_oracle_user.sql`, `Document/02_cafe_catchtable_db_build.sql` 두 스크립트로
Oracle XE 위에 빈 DB를 처음부터 만드는 순서입니다. 처음부터 끝까지 순서대로 진행하면 됩니다.

```
[0] Oracle 서비스 켜기
[1] SQL Developer 인코딩 UTF-8 설정
[2] SYSTEM 접속 만들기        ──▶ [3] 01 스크립트 앞부분: 사용자 생성
[4] CAFE_CATCHTABLE 접속 만들기 ──▶ [5] 01 스크립트 뒷부분: 테이블 7개
                               ──▶ [6] 02 스크립트: 인덱스·패키지·프로시저 7·트리거 5·스케줄러
                               ──▶ [7] 확인  ──▶ [8] 샘플 데이터
[9] 스프링 애플리케이션 연결
```

> ⚠️ **가장 많이 하는 실수**
> `01_create_oracle_user.sql` 은 **사용자 생성**과 **테이블 생성**이 한 파일에 들어 있습니다.
> SYSTEM 으로 접속한 상태에서 파일 전체를 실행하면 테이블이 `CAFE_CATCHTABLE` 이 아니라 **SYSTEM 스키마에 생성됩니다.**
> 반드시 **사용자 생성 부분은 SYSTEM**, **테이블 생성 부분은 CAFE_CATCHTABLE** 접속에서 따로 실행하세요.

---

## 0. Oracle 서비스 켜기

리스너만 떠 있고 DB 서비스가 꺼져 있으면 **기존 접속까지 포함해 모든 접속이** 아래 오류로 실패합니다.

> `ORA-12505: 리스너가 현재 접속 기술자에 지정된 SID를 알지 못합니다`
> (SQL Developer 표시: "SID xe이(가) host localhost port 1521의 리스너에 등록되지 않았습니다")

1. `Win + R` → `services.msc`
2. 아래 두 서비스가 **실행 중**인지 확인하고, 꺼져 있으면 오른쪽 클릭 → **시작** (1~2분 걸릴 수 있음)
   - `OracleServiceXE` — DB 본체 (**이게 꺼져 있으면 위 오류**)
   - `OracleOraDB18Home1TNSListener` — 리스너 (Oracle XE 18c 기준 이름)

또는 **관리자 권한** 명령 프롬프트에서:

```bat
net start OracleServiceXE
```

서비스를 켰는데도 같은 오류면 리스너 등록을 기다리거나(최대 1분) 강제로 등록합니다:

```bat
sqlplus / as sysdba
ALTER SYSTEM REGISTER;
exit
```

> 시작 유형이 '자동'인데 부팅 후 꺼져 있다면 시작에 실패한 것입니다.
> 이벤트 뷰어 → Windows 로그 → 응용 프로그램에서 `OracleServiceXE` 오류를 확인하세요.

## 1. SQL Developer 인코딩 설정 (한글 상태값 때문에 필수)

스크립트에 `'빈자리'`, `'예약완료'` 같은 한글 상수가 들어 있습니다. 인코딩이 틀리면
CHECK 제약·프로시저 비교가 깨져서 **모든 예약이 실패**합니다.

1. **도구(Tools) → 환경설정(Preferences) → 환경(Environment) → 인코딩(Encoding)** 을 `UTF-8` 로 변경
2. SQL Developer 재시작
3. **그 다음에** 스크립트 파일을 엽니다 (이미 열려 있던 탭은 닫고 다시 열기)

DB 문자셋도 확인해 둡니다 (XE 기본값은 `AL32UTF8`):

```sql
SELECT VALUE FROM NLS_DATABASE_PARAMETERS WHERE PARAMETER = 'NLS_CHARACTERSET';
```

## 2. SYSTEM 접속 만들기

접속(Connections) 패널의 **+** → 새 접속:

| 항목 | 값 |
|---|---|
| Name | `XE_SYSTEM` |
| 사용자 이름 | `SYSTEM` |
| 비밀번호 | Oracle 설치 시 정한 비밀번호 |
| 접속 유형 | 기본(Basic) |
| 호스트 이름 | `localhost` |
| 포트 | `1521` |
| **SID** | `xe` |

**테스트** → "상태: 성공" 확인 후 **접속**.

> 💡 SID `xe` 는 CDB 루트에 접속합니다. 01 스크립트의 `ALTER SESSION SET "_ORACLE_SCRIPT" = TRUE;`
> 는 CDB 루트에서 `C##` 접두어 없이 사용자를 만들기 위한 설정이라 이 접속 방식과 짝이 맞습니다.
> 팀원 모두 **같은 방식(SID xe)** 으로 맞춰야 스프링 접속 URL도 같아집니다.
>
> (선택) PDB에 만들고 싶다면 서비스 이름 `XEPDB1` 로 접속하고 `_ORACLE_SCRIPT` 줄은 빼고 실행하세요.
> 이 경우 스프링 URL은 `jdbc:oracle:thin:@//localhost:1521/XEPDB1` 입니다.

## 3. 사용자 생성 — `01_create_oracle_user.sql` 앞부분 (SYSTEM 접속)

`XE_SYSTEM` 워크시트에서 **아래 부분만** 실행합니다 (파일의 5 ~ 17행).

```sql
ALTER SESSION SET "_ORACLE_SCRIPT" = TRUE;

CREATE USER CAFE_CATCHTABLE
IDENTIFIED BY 1234
DEFAULT TABLESPACE USERS
TEMPORARY TABLESPACE TEMP;

GRANT CONNECT, RESOURCE, DBA TO CAFE_CATCHTABLE;

ALTER USER CAFE_CATCHTABLE QUOTA UNLIMITED ON USERS;
```

실행은 **스크립트 실행(F5)** 을 쓰세요. (`Ctrl + Enter` 는 커서가 있는 문장 하나만 실행합니다)

> `DBA` 권한에 `CREATE JOB` 이 포함되어 있어서 02 스크립트의 노쇼 스케줄러도 별도 권한 없이 만들어집니다.

## 4. CAFE_CATCHTABLE 접속 만들기

| 항목 | 값 |
|---|---|
| Name | `CAFE_CATCHTABLE` |
| 사용자 이름 | `CAFE_CATCHTABLE` |
| 비밀번호 | `1234` |
| 호스트 / 포트 / SID | `localhost` / `1521` / `xe` |

**테스트 → 접속**. 이후 단계는 전부 이 접속에서 실행합니다.

## 5. 테이블 생성 — `01_create_oracle_user.sql` 뒷부분 (CAFE_CATCHTABLE 접속)

파일의 `SET SERVEROUTPUT ON;` 부터 끝까지(19행 ~) 를 `CAFE_CATCHTABLE` 워크시트에 붙여 넣고 **F5**.

생성되는 테이블 (FK 때문에 이 순서대로 만들어짐):

| 테이블 | PK | 비고 |
|---|---|---|
| `MEMBER` | `MEMBER_ID` | 아이디 4~20자 영문·숫자, 전화 `010-0000-0000` CHECK |
| `OWNER` | `OWNER_ID` (→ MEMBER) | 사업자번호 `000-00-00000` UNIQUE |
| `CAFE` | `CAFE_ID` (IDENTITY) | `START_TIME`·`END_TIME` = `'HH24:MI'`, 종료 > 시작 |
| `ZONE` | `ZONE_ID` (IDENTITY) | → CAFE |
| `SEAT` | `SEAT_ID` (IDENTITY) | (ZONE_ID, SEAT_NUM) UNIQUE, 적정인원 ≥ 1 |
| `RESERVATION` | `RESERVATION_ID` (IDENTITY) | → SEAT **ON DELETE CASCADE** |
| `RESERVATION_LOG` | `LOG_ID` (IDENTITY) | 트리거가 이력 기록 |

## 6. 프로시저·트리거 생성 — `02_cafe_catchtable_db_build.sql` (CAFE_CATCHTABLE 접속)

파일 전체를 열어 **F5** 로 실행합니다. 프로시저·트리거는 `/` 로 끝나는 PL/SQL 블록이라
반드시 **스크립트 실행(F5)** 이어야 합니다.

만들어지는 객체:

| 구분 | 이름 | 역할 |
|---|---|---|
| 인덱스 | `IDX_RESV_SEAT_START` | RESERVATION(SEAT_ID, START_DT) — NFR-04 |
| 패키지 | `PKG_RESV_DML` | 프로시저 안에서만 RESERVATION 변경 허용 (직접 DML 차단) |
| 프로시저 | `SP_CREATE_RESERVATION` | 예약 생성 (FR-12~14) |
| | `SP_CANCEL_RESERVATION` | 예약 취소 (FR-15) |
| | `SP_CHECK_IN` / `SP_CHECK_OUT` | 체크인 / 퇴실 (FR-16, 17) |
| | `SP_EXPIRE_NOSHOW` | 노쇼 일괄 처리 (FR-18) |
| | `SP_GET_AVAILABLE_SLOTS` | 1시간 슬롯 조회 (FR-11) |
| | `SP_ZONE_STATS` | 구역 통계 (FR-21) |
| 트리거 | `TRG_RESV_VALIDATE` | 예약 INSERT/UPDATE 검증 + 직접 DML 차단 |
| | `TRG_RESV_SEAT_SYNC` | 예약 상태 → 좌석 상태 동기화 |
| | `TRG_RESV_HISTORY` | RESERVATION_LOG 기록 |
| | `TRG_SEAT_DELETE_GUARD` | 유효 예약 남은 좌석 삭제 차단 (-20010) |
| | `TRG_OWNER_ROLE` | OWNER INSERT 시 MEMBER.ROLE = 'OWNER' |
| 스케줄러 | `JOB_EXPIRE_NOSHOW` | 5분마다 `SP_EXPIRE_NOSHOW` 실행 |

스크립트 마지막의 두 SELECT 가 결과를 보여줍니다.
**`USER_ERRORS` 결과가 0건**이어야 정상입니다.

## 7. 확인

```sql
-- 1) 모든 객체가 VALID 인지 (INVALID 가 있으면 컴파일 오류)
SELECT OBJECT_TYPE, OBJECT_NAME, STATUS
  FROM USER_OBJECTS
 WHERE OBJECT_TYPE IN ('TABLE','INDEX','PACKAGE','PROCEDURE','TRIGGER')
 ORDER BY OBJECT_TYPE, OBJECT_NAME;

-- 2) 컴파일 오류 (0건이어야 함)
SELECT NAME, TYPE, LINE, TEXT FROM USER_ERRORS ORDER BY NAME, SEQUENCE;

-- 3) 노쇼 스케줄러가 켜져 있는지 (ENABLED = TRUE, STATE = SCHEDULED)
SELECT JOB_NAME, ENABLED, STATE, REPEAT_INTERVAL, LAST_START_DATE
  FROM USER_SCHEDULER_JOBS;

-- 4) 한글이 깨지지 않았는지 — '빈자리', '예약완료' 등이 정상으로 보여야 함
SELECT CONSTRAINT_NAME, SEARCH_CONDITION_VC
  FROM USER_CONSTRAINTS
 WHERE CONSTRAINT_NAME IN ('CK_SEAT_STATUS', 'CK_RESV_STATUS');
```

NFR-01 확인 (직접 INSERT 가 막혀야 정상):

```sql
INSERT INTO RESERVATION (MEMBER_ID, SEAT_ID, START_DT, END_DT, VISITOR_COUNT)
VALUES ('user01', 1, SYSDATE + 1, SYSDATE + 1 + 1/24, 1);
-- 기대 결과: ORA-20007: 예약은 지정된 저장프로시저로 처리해야 합니다
```

## 8. 샘플 데이터 (선택)

앱 로그인은 **BCrypt 해시**만 인정합니다. 평문 비밀번호로 넣은 회원은 로그인이 안 됩니다.
아래 해시는 비밀번호 **`test1234`** 입니다.

ID 컬럼은 IDENTITY 이므로 **값을 직접 넣지 말고** 서브쿼리로 찾아 씁니다.
(직접 넣으면 IDENTITY 시퀀스가 따라오지 않아 나중에 앱에서 등록할 때 PK 충돌이 납니다)

```sql
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
```

예약은 직접 INSERT 가 막혀 있으므로 **프로시저로** 만듭니다 (내일 14:00~16:00, 2명):

```sql
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
```

## 9. 스프링 애플리케이션 연결

`src/main/resources/application.properties` 의 접속 정보가 SQL Developer 접속과 같아야 합니다.

| SQL Developer 접속 방식 | `spring.datasource.url` |
|---|---|
| SID `xe` (이 가이드 기본) | `jdbc:oracle:thin:@localhost:1521:xe` |
| 서비스 이름 `XEPDB1` | `jdbc:oracle:thin:@//localhost:1521/XEPDB1` |

```properties
spring.datasource.url=${DB_URL:jdbc:oracle:thin:@localhost:1521:xe}
spring.datasource.username=${DB_USERNAME:CAFE_CATCHTABLE}
spring.datasource.password=${DB_PASSWORD:1234}
```

엔티티와 실제 테이블이 맞는지 한 번 검증해 보려면 (테이블을 바꾸지 않는 읽기 전용 검사):

```bash
./gradlew bootJar
java -jar build/libs/CafeCatchtable-0.0.1-SNAPSHOT.jar --spring.jpa.hibernate.ddl-auto=validate
```

`Started CafeCatchtableApplication` 이 뜨면 성공. 브라우저에서 `http://localhost:8080/login` →
`user01` / `test1234` 로 로그인해 봅니다.

---

## 다시 처음부터 만들기 (초기화)

`SYSTEM` 접속에서:

```sql
ALTER SESSION SET "_ORACLE_SCRIPT" = TRUE;
DROP USER CAFE_CATCHTABLE CASCADE;   -- 테이블·프로시저·트리거·스케줄러 Job 모두 삭제
```

그 다음 3단계부터 다시 진행합니다.
`02` 스크립트만 다시 돌리고 싶을 때는 인덱스·Job 이 이미 있어서 오류가 나므로 먼저 지웁니다:

```sql
DROP INDEX IDX_RESV_SEAT_START;
BEGIN DBMS_SCHEDULER.DROP_JOB('JOB_EXPIRE_NOSHOW', FORCE => TRUE); END;
/
```
(프로시저·트리거·패키지는 `CREATE OR REPLACE` 라 그대로 다시 실행돼도 됩니다)

## 문제 해결

| 증상 | 원인 / 해결 |
|---|---|
| `ORA-12505` / `ORA-12514` (접속 실패) | `OracleServiceXE` 가 꺼져 있음 → 0단계. 또는 SID/서비스 이름 오타 |
| `ORA-01017` (아이디/비밀번호 오류) | 3단계 사용자 생성이 안 됐거나 비밀번호 다름 |
| `ORA-65096: 공통 사용자 또는 롤 이름이 부적합` | CDB 루트에서 `_ORACLE_SCRIPT` 설정 없이 CREATE USER → 3단계 첫 줄 같이 실행 |
| 테이블이 CAFE_CATCHTABLE 에 없음 | 01 파일 전체를 SYSTEM 에서 실행함 → SYSTEM 의 테이블 삭제 후 5단계를 CAFE_CATCHTABLE 접속에서 실행 |
| `ORA-02290: CK_SEAT_STATUS` 등 한글 CHECK 위반 | 파일 인코딩이 UTF-8 이 아님 → 1단계 후 초기화하고 재구축 |
| 프로시저 `INVALID` / `USER_ERRORS` 에 결과 | 테이블이 없는 상태에서 02 실행 → 5단계 확인 후 02 재실행 |
| 앱에서 카페 등록 시 PK 중복(`ORA-00001`) | 샘플 데이터에 ID를 직접 넣음 → 아래 실행 |
| 로그인 실패 (`401`) | 비밀번호가 평문으로 들어감 → BCrypt 해시로 UPDATE |

IDENTITY 시퀀스를 현재 최대값 다음으로 맞추기 (ID를 직접 넣어 시드했을 때):

```sql
ALTER TABLE CAFE        MODIFY CAFE_ID        GENERATED BY DEFAULT AS IDENTITY (START WITH LIMIT VALUE);
ALTER TABLE ZONE        MODIFY ZONE_ID        GENERATED BY DEFAULT AS IDENTITY (START WITH LIMIT VALUE);
ALTER TABLE SEAT        MODIFY SEAT_ID        GENERATED BY DEFAULT AS IDENTITY (START WITH LIMIT VALUE);
ALTER TABLE RESERVATION MODIFY RESERVATION_ID GENERATED BY DEFAULT AS IDENTITY (START WITH LIMIT VALUE);
```
