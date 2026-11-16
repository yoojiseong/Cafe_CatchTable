package academic.cafecatchtable.jdbc;

import academic.cafecatchtable.common.Status;
import academic.cafecatchtable.dto.OwnerDto.OwnerReservationResponse;
import academic.cafecatchtable.dto.OwnerDto.PopularSeat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 점주 조회용 SELECT (프로시저 아님) — API 명세서상 "JDBC JdbcTemplate".
 *  - O-10 일일 예약 타임라인 (FR-20, 질의 5·11)
 *  - O-11 예약자 상세       (FR-20, 질의 12)
 *  - O-12 인기 좌석 TOP 3   (FR-21, 질의 14)
 * NFR-03: 모든 값은 바인드 변수로만 넘긴다.
 * DATE 컬럼 인덱스(RESERVATION(SEAT_ID, START_DT))를 타도록 바인드 값은 CAST(? AS DATE) 로 비교한다.
 */
@Repository
public class OwnerQueryDao {

    private static final String SELECT_RESV = """
            SELECT r.RESERVATION_ID, r.SEAT_ID, s.SEAT_NUM, z.ZONE_NAME,
                   r.MEMBER_ID, m.NAME AS MEMBER_NAME, m.PHONE,
                   r.START_DT, r.END_DT, r.VISITOR_COUNT, r.STATUS,
                   r.ACTUAL_IN, r.ACTUAL_OUT, r.CREATED_AT, c.OWNER_ID
              FROM RESERVATION r
              JOIN SEAT   s ON s.SEAT_ID   = r.SEAT_ID
              JOIN ZONE   z ON z.ZONE_ID   = s.ZONE_ID
              JOIN CAFE   c ON c.CAFE_ID   = z.CAFE_ID
              JOIN MEMBER m ON m.MEMBER_ID = r.MEMBER_ID
            """;

    private final JdbcTemplate jdbcTemplate;

    public OwnerQueryDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** O-10: 선택한 날짜(00:00~24:00)에 걸친 카페의 모든 예약. 좌석 → 시작 시각 순 */
    public List<OwnerReservationResponse> findDailyReservations(Long cafeId, LocalDate date) {
        String sql = SELECT_RESV + """
                 WHERE z.CAFE_ID = ?
                   AND r.START_DT < CAST(? AS DATE)
                   AND r.END_DT   > CAST(? AS DATE)
                 ORDER BY z.ZONE_NAME, s.SEAT_NUM, r.START_DT
                """;
        return jdbcTemplate.query(sql, RESV_MAPPER,
                cafeId,
                Timestamp.valueOf(date.plusDays(1).atStartOfDay()),
                Timestamp.valueOf(date.atStartOfDay()));
    }

    /** O-11: 예약 1건 + 그 카페의 점주 ID (권한 검사용) */
    public Optional<OwnedReservation> findReservationWithOwner(Long reservationId) {
        String sql = SELECT_RESV + " WHERE r.RESERVATION_ID = ?";
        List<OwnedReservation> list = jdbcTemplate.query(sql,
                (rs, n) -> new OwnedReservation(rs.getString("OWNER_ID"), RESV_MAPPER.mapRow(rs, n)),
                reservationId);
        return list.stream().findFirst();
    }

    /** O-12: 기간 [from, to] 동안 예약이 많은 좌석 TOP 3 (예약취소 제외) */
    public List<PopularSeat> findTopSeats(Long cafeId, LocalDate from, LocalDate to) {
        String sql = """
                SELECT * FROM (
                    SELECT s.SEAT_ID, s.SEAT_NUM, z.ZONE_NAME, COUNT(*) AS RESV_COUNT
                      FROM RESERVATION r
                      JOIN SEAT s ON s.SEAT_ID = r.SEAT_ID
                      JOIN ZONE z ON z.ZONE_ID = s.ZONE_ID
                     WHERE z.CAFE_ID = ?
                       AND r.START_DT >= CAST(? AS DATE)
                       AND r.START_DT <  CAST(? AS DATE)
                       AND r.STATUS <> ?
                     GROUP BY s.SEAT_ID, s.SEAT_NUM, z.ZONE_NAME
                     ORDER BY RESV_COUNT DESC, s.SEAT_NUM
                ) WHERE ROWNUM <= 3
                """;
        return jdbcTemplate.query(sql,
                (rs, n) -> new PopularSeat(rs.getLong("SEAT_ID"), rs.getString("SEAT_NUM"),
                        rs.getString("ZONE_NAME"), rs.getLong("RESV_COUNT")),
                cafeId,
                Timestamp.valueOf(from.atStartOfDay()),
                Timestamp.valueOf(to.plusDays(1).atStartOfDay()),
                Status.RESV_CANCELED);
    }

    public record OwnedReservation(String ownerId, OwnerReservationResponse reservation) {}

    private static final RowMapper<OwnerReservationResponse> RESV_MAPPER = (rs, n) -> new OwnerReservationResponse(
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

    private static LocalDateTime toLdt(ResultSet rs, String col) throws SQLException {
        Timestamp ts = rs.getTimestamp(col);
        return ts == null ? null : ts.toLocalDateTime();
    }
}
