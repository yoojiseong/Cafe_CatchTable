package academic.cafecatchtable.dto;

import academic.cafecatchtable.domain.Cafe;
import academic.cafecatchtable.domain.Zone;
import academic.cafecatchtable.common.OperatingDays;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 카페·좌석 조회 API (C-01 ~ C-04) 응답 — 점주 API 도 같은 모양을 쓴다 */
public final class CafeDto {

    private CafeDto() {}

    /** C-01 / C-02 / O-01 ~ O-03. zones 는 C-02 에서만 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CafeResponse(Long cafeId, String cafeName, String address, String phone,
                               String startTime, String endTime, String operatingDays,
                               List<ZoneResponse> zones) {

        public static CafeResponse of(Cafe c) {
            return of(c, null);
        }

        /**
         * OPERATING_DAYS 는 DB 에 'MON,TUE' 처럼 대문자로 들어 있을 수도 있다(프로시저는 UPPER 비교).
         * 화면(app.js)은 'Mon,Tue' 형식만 인식하므로 응답에서는 표준 형식으로 맞춰 내려준다.
         */
        public static CafeResponse of(Cafe c, List<ZoneResponse> zones) {
            String days = OperatingDays.normalize(c.getOperatingDays());
            return new CafeResponse(c.getCafeId(), c.getCafeName(), c.getAddress(), c.getPhone(),
                    c.getStartTime(), c.getEndTime(), days != null ? days : c.getOperatingDays(), zones);
        }
    }

    /** C-02 zones / O-04 / O-05 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ZoneResponse(Long zoneId, Long cafeId, String zoneName) {

        public static ZoneResponse of(Zone z) {
            return new ZoneResponse(z.getZoneId(), z.getCafeId(), z.getZoneName());
        }

        /** C-02 에서는 cafeId 를 빼고 내려준다 */
        public static ZoneResponse brief(Zone z) {
            return new ZoneResponse(z.getZoneId(), null, z.getZoneName());
        }
    }

    /** C-03 / O-07 / O-08. currentStatus = 빈자리 | 예약됨 | 이용중 | 사용중지 */
    public record SeatResponse(Long seatId, String seatNum, Long zoneId, String zoneName,
                               String seatType, String extraItem, Integer capacity, String currentStatus) {}

    /** C-03 JPQL 조회 결과 (SEAT + ZONE) */
    public record SeatRow(Long seatId, String seatNum, Long zoneId, String zoneName,
                          String seatType, String extraItem, Integer capacity, String status) {}

    /** C-04 예약 가능 시간대 */
    public record SlotsResponse(Long cafeId, LocalDate date, List<SeatSlots> seats) {}

    public record SeatSlots(Long seatId, String seatNum, String zoneName, List<Slot> slots) {}

    public record Slot(@JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime start,
                       boolean available) {}
}
