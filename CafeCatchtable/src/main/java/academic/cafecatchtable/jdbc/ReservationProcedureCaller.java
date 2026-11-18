package academic.cafecatchtable.jdbc;

import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;

/**
 * 예약 상태를 바꾸는 저장프로시저 호출 (순수 JDBC, CallableStatement) — 구현은 직접 작성.
 * 프로시저 정의: Document/02_cafe_catchtable_db_build.sql 3-1 ~ 3-4
 *
 * 공통 규칙
 *  - 회원 ID 는 세션에서 꺼내 p_member_id 로 넘긴다 (요청 본문에서 받지 않음).
 *  - 프로시저가 COMMIT/ROLLBACK 을 직접 하므로 호출하는 서비스 메서드는 @Transactional 을 걸지 않았다.
 *    dataSource.getConnection() 으로 받은 Connection 은 autoCommit 기본값 그대로 쓰고 try-with-resources 로 닫는다.
 *  - OUT p_result_code : 성공 0, 실패 -20001 ~ -20010. 그대로 return 하면
 *    ReservationService 가 ApiException.throwIfFailed() 로 HTTP 오류로 바꾼다.
 *  - 네 프로시저 모두 EXCEPTION WHEN OTHERS 에서 오류를 p_result_code 로 바꿔 주므로
 *    (트리거의 RAISE_APPLICATION_ERROR 포함) 정상적인 규칙 위반은 SQLException 으로 오지 않는다.
 *    SQLException 은 접속 오류 등이므로 RuntimeException 으로 감싸 던지면 된다(500 처리).
 *  - 예약행이 없으면(NO_DATA_FOUND) -20007 이 오지만, 서비스가 미리 존재 여부를 확인해 404 로 응답한다.
 *
 * 직접 DML 차단: TRG_RESV_VALIDATE 는 PKG_RESV_DML.G_ALLOWED 가 TRUE 일 때만 INSERT/UPDATE 를 허용한다.
 * 프로시저 안에서만 TRUE 로 켜므로, 앱(JPA)에서 RESERVATION 을 직접 INSERT/UPDATE 하면 -20007 로 막힌다.
 * (그래서 Reservation 엔티티는 @Immutable)
 *
 * DB-P5 SP_EXPIRE_NOSHOW 는 DBMS_SCHEDULER Job JOB_EXPIRE_NOSHOW(5분 간격)가 실행하므로 앱에서 호출하지 않는다 (FR-18).
 *
 * 구현: createReservation. 나머지(취소·체크인·퇴실)는 아직 UnsupportedOperationException 을 던진다
 * → API 는 501 NOT_IMPLEMENTED 응답. createReservation 과 같은 방식으로 작성하면 된다.
 */
@Repository
public class ReservationProcedureCaller {

    /** Spring 이 application.properties 의 Oracle 설정으로 만든 커넥션 풀. dataSource.getConnection() 으로 사용 */
    private final DataSource dataSource;

    public ReservationProcedureCaller(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * R-01 예약 생성 (FR-12 ~ FR-14) → DB-P1 SP_CREATE_RESERVATION
     *
     * 호출: {call SP_CREATE_RESERVATION(?, ?, ?, ?, ?, ?, ?)}
     *   IN  1 p_member_id      VARCHAR2 ← memberId
     *   IN  2 p_seat_id        NUMBER   ← seatId
     *   IN  3 p_start_dt       DATE     ← Timestamp.valueOf(startDt)
     *   IN  4 p_end_dt         DATE     ← Timestamp.valueOf(endDt)  (서비스가 startDt + stayMinutes 로 계산)
     *   IN  5 p_visitor_count  NUMBER   ← visitorCount
     *   OUT 6 p_reservation_id NUMBER   → reservationId (Types.NUMERIC, 실패 시 NULL → wasNull() 확인)
     *   OUT 7 p_result_code    NUMBER   → resultCode    (Types.INTEGER)
     *
     * 프로시저 검사 순서: 좌석·회원 행 FOR UPDATE 잠금 → 사용중지(-20009) → 현재 이후~7일(-20002)
     *   → 적정인원(-20003) → 영업요일·영업시간(-20001) → 좌석 시간 중복(-20004) → 회원 동시간 예약(-20005)
     *   → INSERT → COMMIT
     * INSERT 시 자동으로 실행되는 트리거:
     *   - TRG_RESV_VALIDATE (DB-T1, BEFORE INSERT) : 직접 DML 차단, END_DT>START_DT, 인원·사용중지 재확인
     *   - TRG_RESV_HISTORY  (DB-T3, AFTER INSERT)  : RESERVATION_LOG 에 'INSERT' 이력
     */
    public CreateReservationResult createReservation(String memberId, Long seatId,
                                                     LocalDateTime startDt, LocalDateTime endDt,
                                                     int visitorCount) {
        String sql = "{call SP_CREATE_RESERVATION(?, ?, ?, ?, ?, ?, ?)}";
        try (Connection con = dataSource.getConnection();
             CallableStatement cstmt = con.prepareCall(sql)) {

            cstmt.setString(1, memberId);
            cstmt.setLong(2, seatId);
            cstmt.setTimestamp(3, Timestamp.valueOf(startDt));
            cstmt.setTimestamp(4, Timestamp.valueOf(endDt));
            cstmt.setInt(5, visitorCount);
            cstmt.registerOutParameter(6, Types.NUMERIC);
            cstmt.registerOutParameter(7, Types.INTEGER);

            cstmt.execute();

            long id = cstmt.getLong(6);
            Long reservationId = cstmt.wasNull() ? null : id;   // 실패하면 NULL
            int resultCode = cstmt.getInt(7);
            return new CreateReservationResult(reservationId, resultCode);

        } catch (SQLException e) {
            // 규칙 위반은 프로시저가 p_result_code 로 돌려주므로, 여기로 오는 건 접속 오류 등이다 → 500
            throw new RuntimeException("SP_CREATE_RESERVATION 호출 실패", e);
        }
    }

    /**
     * R-04 예약 취소 (FR-15) → DB-P2 SP_CANCEL_RESERVATION
     *
     * 호출: {call SP_CANCEL_RESERVATION(?, ?, ?)}
     *   IN  1 p_reservation_id NUMBER
     *   IN  2 p_member_id      VARCHAR2
     *   OUT 3 p_result_code    NUMBER   → return
     *
     * 프로시저 검사: 본인 아님 -20008, '예약완료'가 아니거나 입실 시각이 지남 -20007 → STATUS='예약취소'
     * UPDATE 시 자동으로 실행되는 트리거:
     *   - TRG_RESV_VALIDATE  (DB-T1, BEFORE UPDATE)
     *   - TRG_RESV_SEAT_SYNC (DB-T2, AFTER UPDATE OF STATUS) : SEAT.STATUS → '빈자리'
     *   - TRG_RESV_HISTORY   (DB-T3)                         : 'UPDATE' 이력 (예약완료 → 예약취소)
     */
    public int cancelReservation(Long reservationId, String memberId) {
        // TODO: SP_CANCEL_RESERVATION 호출 구현
        throw new UnsupportedOperationException("SP_CANCEL_RESERVATION 호출 미구현");
    }

    /**
     * R-05 체크인 (FR-16) → DB-P3 SP_CHECK_IN
     *
     * 호출: {call SP_CHECK_IN(?, ?, ?)}
     *   IN  1 p_reservation_id NUMBER
     *   IN  2 p_member_id      VARCHAR2
     *   OUT 3 p_result_code    NUMBER   → return
     *
     * 프로시저 검사: 본인 아님 -20008, '예약완료' 아님 -20007, 입실예정 10분 전~15분 후 밖 -20006
     *              → ACTUAL_IN = SYSDATE, STATUS='이용중'
     * UPDATE 시 자동으로 실행되는 트리거:
     *   - TRG_RESV_VALIDATE  (DB-T1, BEFORE UPDATE)
     *   - TRG_RESV_SEAT_SYNC (DB-T2) : SEAT.STATUS → '이용중'
     *   - TRG_RESV_HISTORY   (DB-T3) : 'UPDATE' 이력 (예약완료 → 이용중)
     */
    public int checkIn(Long reservationId, String memberId) {
        // TODO: SP_CHECK_IN 호출 구현
        throw new UnsupportedOperationException("SP_CHECK_IN 호출 미구현");
    }

    /**
     * R-06 퇴실 (FR-17) → DB-P4 SP_CHECK_OUT
     *
     * 호출: {call SP_CHECK_OUT(?, ?, ?)}
     *   IN  1 p_reservation_id NUMBER
     *   IN  2 p_member_id      VARCHAR2
     *   OUT 3 p_result_code    NUMBER   → return
     *
     * 프로시저 검사: 본인 아님 -20008, '이용중' 아님 -20007 → ACTUAL_OUT = SYSDATE, STATUS='이용완료'
     * UPDATE 시 자동으로 실행되는 트리거:
     *   - TRG_RESV_VALIDATE  (DB-T1, BEFORE UPDATE)
     *   - TRG_RESV_SEAT_SYNC (DB-T2) : SEAT.STATUS → '빈자리' (좌석 즉시 반납)
     *   - TRG_RESV_HISTORY   (DB-T3) : 'UPDATE' 이력 (이용중 → 이용완료)
     */
    public int checkOut(Long reservationId, String memberId) {
        // TODO: SP_CHECK_OUT 호출 구현
        throw new UnsupportedOperationException("SP_CHECK_OUT 호출 미구현");
    }
}
