package academic.cafecatchtable.jdbc;

import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.List;

/**
 * 예약 가능 시간대 조회 프로시저 호출 (순수 JDBC) — 구현은 직접 작성.
 * 사용처: C-04 GET /api/cafes/{cafeId}/slots (FR-11, 질의 4)
 */
@Repository
public class SlotProcedureCaller {

    private final DataSource dataSource;

    public SlotProcedureCaller(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * DB-P6 SP_GET_AVAILABLE_SLOTS (02_cafe_catchtable_db_build.sql 3-6)
     *
     * 호출: {call SP_GET_AVAILABLE_SLOTS(?, ?, ?, ?)}
     *   IN  1 p_cafe_id       NUMBER
     *   IN  2 p_date          DATE   ← java.sql.Date.valueOf(date)  (프로시저가 TRUNC 해서 씀)
     *   IN  3 p_visitor_count NUMBER
     *   OUT 4 p_cursor        SYS_REFCURSOR → registerOutParameter(4, Types.REF_CURSOR), (ResultSet) cs.getObject(4)
     *
     * 커서 열 → SlotRow (ZONE_NAME, SEAT_NUM, SLOT_START 순으로 정렬되어 옴)
     *   SEAT_ID → seatId, SEAT_NUM → seatNum, ZONE_NAME → zoneName,
     *   SLOT_START(DATE) → slotStart (rs.getTimestamp(...).toLocalDateTime()),
     *   AVAILABLE('Y'/'N') → available ("Y".equals(...))
     *
     * 프로시저 동작 참고
     *   - 휴무일이면 오류 없이 빈 커서를 돌려준다 → 서비스가 호출 전에 422(-20001 "영업일이 아닙니다")로 먼저 거른다.
     *   - '사용중지' 좌석과 이미 지난 슬롯(SLOT_START <= SYSDATE)은 결과에서 빠진다.
     *   - 적정인원 < 인원 인 좌석은 행은 오지만 AVAILABLE='N'.
     *   - 카페가 없으면 예외 처리 없이 ORA-01403(NO_DATA_FOUND)이 올라온다 → 서비스가 미리 404 로 거른다.
     * 조회 전용이라 트리거가 실행되지 않는다.
     */
    public List<SlotRow> getAvailableSlots(Long cafeId, LocalDate date, int visitorCount) {
        // TODO: SP_GET_AVAILABLE_SLOTS 호출 구현
        throw new UnsupportedOperationException("SP_GET_AVAILABLE_SLOTS 호출 미구현");
    }
}
