package academic.cafecatchtable.jdbc;

import academic.cafecatchtable.dto.OwnerDto.OwnerReservationResponse;
import academic.cafecatchtable.dto.OwnerDto.PopularSeat;
import org.springframework.jdbc.core.CallableStatementCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 점주 조회 저장프로시저 호출 — 프로시저 정의: Document/04_owner_query_procedures.md
 *  - O-10 일일 예약 타임라인 → SP_OWNER_DAILY_RESERVATIONS(p_cafe_id, p_date, OUT p_cursor)
 *  - O-11 예약자 상세       → SP_OWNER_RESERVATION_DETAIL(p_reservation_id, OUT p_cursor)
 *  - O-12 인기 좌석 TOP 3   → SP_POPULAR_SEATS(p_cafe_id, p_from, p_to(미포함), OUT p_cursor)
 *
 * 세 프로시저 모두 결과를 SYS_REFCURSOR 로 돌려준다 → Types.REF_CURSOR 로 등록하고 ResultSet 으로 읽는다.
 * 조회 전용(COMMIT 없음)이라 JdbcTemplate.execute 로 호출한다.
 * JdbcTemplate 은 진행 중인 트랜잭션의 커넥션을 그대로 쓰고, 커넥션·CallableStatement 를 알아서 닫는다.
 */
@Repository
public class OwnerQueryDao {

    private final JdbcTemplate jdbcTemplate;

    public OwnerQueryDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** O-10: 선택한 날짜(00:00~24:00)에 걸친 카페의 모든 예약. 구역 → 좌석 → 시작 시각 순 */
    public List<OwnerReservationResponse> findDailyReservations(Long cafeId, LocalDate date) {
        return jdbcTemplate.execute("{call SP_OWNER_DAILY_RESERVATIONS(?, ?, ?)}",
                (CallableStatementCallback<List<OwnerReservationResponse>>) cs -> {
                    cs.setLong(1, cafeId);
                    cs.setTimestamp(2, Timestamp.valueOf(date.atStartOfDay()));
                    cs.registerOutParameter(3, Types.REF_CURSOR);
                    cs.execute();

                    List<OwnerReservationResponse> list = new ArrayList<>();
                    try (ResultSet rs = cursor(cs, 3)) {
                        while (rs.next()) list.add(toReservation(rs));
                    }
                    return list;
                });
    }

    /** O-11: 예약 1건 + 그 카페의 점주 ID (권한 검사용). 없으면 빈 Optional */
    public Optional<OwnedReservation> findReservationWithOwner(Long reservationId) {
        return jdbcTemplate.execute("{call SP_OWNER_RESERVATION_DETAIL(?, ?)}",
                (CallableStatementCallback<Optional<OwnedReservation>>) cs -> {
                    cs.setLong(1, reservationId);
                    cs.registerOutParameter(2, Types.REF_CURSOR);
                    cs.execute();

                    try (ResultSet rs = cursor(cs, 2)) {
                        if (!rs.next()) return Optional.empty();
                        return Optional.of(new OwnedReservation(rs.getString("OWNER_ID"), toReservation(rs)));
                    }
                });
    }

    /** O-12: 기간 [from, to] 동안 예약이 많은 좌석 TOP 3 (예약취소 제외) */
    public List<PopularSeat> findTopSeats(Long cafeId, LocalDate from, LocalDate to) {
        return jdbcTemplate.execute("{call SP_POPULAR_SEATS(?, ?, ?, ?)}",
                (CallableStatementCallback<List<PopularSeat>>) cs -> {
                    cs.setLong(1, cafeId);
                    cs.setTimestamp(2, Timestamp.valueOf(from.atStartOfDay()));
                    cs.setTimestamp(3, Timestamp.valueOf(to.plusDays(1).atStartOfDay()));   // p_to 는 미포함 경계
                    cs.registerOutParameter(4, Types.REF_CURSOR);
                    cs.execute();

                    List<PopularSeat> list = new ArrayList<>();
                    try (ResultSet rs = cursor(cs, 4)) {
                        while (rs.next()) {
                            list.add(new PopularSeat(rs.getLong("SEAT_ID"), rs.getString("SEAT_NUM"),
                                    rs.getString("ZONE_NAME"), rs.getLong("RESV_COUNT")));
                        }
                    }
                    return list;
                });
    }

    public record OwnedReservation(String ownerId, OwnerReservationResponse reservation) {}

    /* ------------------------------ 도우미 ------------------------------ */

    /** OUT SYS_REFCURSOR → ResultSet */
    private static ResultSet cursor(CallableStatement cs, int index) throws SQLException {
        return cs.getObject(index, ResultSet.class);
    }

    /** SP_OWNER_DAILY_RESERVATIONS / SP_OWNER_RESERVATION_DETAIL 커서 한 행 */
    private static OwnerReservationResponse toReservation(ResultSet rs) throws SQLException {
        return new OwnerReservationResponse(
                rs.getLong("RESERVATION_ID"),
                rs.getLong("SEAT_ID"),
                rs.getString("SEAT_NUM"),
                rs.getString("ZONE_NAME"),
                rs.getString("MEMBER_ID"),
                rs.getString("MEMBER_NAME"),
                rs.getString("PHONE"),
                toLdt(rs, "START_DT"),
                toLdt(rs, "END_DT"),
                rs.getInt("VISITOR_COUNT"),
                rs.getString("STATUS"),
                toLdt(rs, "ACTUAL_IN"),
                toLdt(rs, "ACTUAL_OUT"),
                toLdt(rs, "CREATED_AT"));
    }

    private static LocalDateTime toLdt(ResultSet rs, String col) throws SQLException {
        Timestamp ts = rs.getTimestamp(col);
        return ts == null ? null : ts.toLocalDateTime();
    }
}
