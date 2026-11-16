package academic.cafecatchtable.jdbc;

import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 구역 통계 프로시저 호출 (순수 JDBC) — 구현은 직접 작성.
 * 사용처: O-12 GET /api/owner/cafes/{cafeId}/stats (FR-21, 질의 6·13)
 * 인기 좌석 TOP 3(질의 14)는 프로시저가 아니라 OwnerQueryDao 의 SELECT 로 구현돼 있다.
 */
@Repository
public class StatsProcedureDao {

    private final DataSource dataSource;

    public StatsProcedureDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * DB-P7 SP_ZONE_STATS (02_cafe_catchtable_db_build.sql 3-7)
     *
     * 호출: {call SP_ZONE_STATS(?, ?, ?, ?)}
     *   IN  1 p_cafe_id NUMBER
     *   IN  2 p_from    DATE  ← Timestamp.valueOf(from)        (기간 시작 시각, 포함)
     *   IN  3 p_to      DATE  ← Timestamp.valueOf(toExclusive) (기간 끝 시각, 미포함)
     *   OUT 4 p_cursor  SYS_REFCURSOR → registerOutParameter(4, Types.REF_CURSOR), (ResultSet) cs.getObject(4)
     *
     * 프로시저는 p_to 를 "그 시각 미만"으로 계산한다(R.START_DT < p_to, LEAST(…, p_to)).
     * 그래서 서비스가 화면의 [from일, to일] 을 from 00:00 ~ (to+1일) 00:00 으로 바꿔서 넘긴다.
     * p_to <= p_from 이면 빈 커서가 온다.
     *
     * 커서 열 → ZoneStatRow (구역 1행, ZONE_NAME 순)
     *   ZONE_NAME → zoneName, RESV_COUNT → resvCount, AVG_STAY_MIN → avgStayMin,
     *   OCCUPANCY_RATE → occupancyRate, TURNOVER → turnover, NOSHOW_RATE → noshowRate
     *   AVG_STAY_MIN·TURNOVER·NOSHOW_RATE 는 NULL 일 수 있으니
     *   rs.getObject(col, Double.class) 처럼 받아 null 을 유지할 것 (getDouble 은 NULL 을 0 으로 바꿈).
     *
     * 조회 전용이라 트리거가 실행되지 않는다. 점주 본인 카페인지는 서비스가 호출 전에 검사한다.
     */
    public List<ZoneStatRow> getZoneStats(Long cafeId, LocalDateTime from, LocalDateTime toExclusive) {
        // TODO: SP_ZONE_STATS 호출 구현
        throw new UnsupportedOperationException("SP_ZONE_STATS 호출 미구현");
    }
}
