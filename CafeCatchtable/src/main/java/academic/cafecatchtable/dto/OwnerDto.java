package academic.cafecatchtable.dto;

import academic.cafecatchtable.jdbc.ZoneStatRow;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 점주 API (O-01 ~ O-12) 요청·응답 */
public final class OwnerDto {

    private OwnerDto() {}

    private static final String DT = "yyyy-MM-dd'T'HH:mm:ss";
    /**
     * HH:mm (00:00 ~ 23:59) 또는 24:00.
     * DB 제약(CK_CAFE_*_TIME)은 23:59 까지만 허용하므로 24:00 은 서비스가 23:59 로 바꿔 저장한다 (요구사항 6장).
     */
    private static final String HHMM = "^(([01]\\d|2[0-3]):[0-5]\\d|24:00)$";

    /** O-02 / O-03 카페 등록·수정 (FR-04). 길이·NOT NULL 은 CAFE 테이블 DDL 기준 */
    public record CafeRequest(
            @NotBlank(message = "카페 이름을 입력해 주세요")
            @Size(max = 100, message = "카페 이름은 100자 이하입니다")
            String cafeName,

            @NotBlank(message = "주소를 입력해 주세요")
            @Size(max = 255, message = "주소는 255자 이하입니다")
            String address,

            @NotBlank(message = "전화번호를 입력해 주세요")
            @Pattern(regexp = "^\\d{2,4}-\\d{3,4}-\\d{4}$", message = "전화번호 형식을 확인해 주세요")
            String phone,

            @NotBlank(message = "영업 시작 시간을 입력해 주세요")
            @Pattern(regexp = HHMM, message = "영업 시작 시간 형식은 HH:mm 입니다")
            String startTime,

            @NotBlank(message = "영업 종료 시간을 입력해 주세요")
            @Pattern(regexp = HHMM, message = "영업 종료 시간 형식은 HH:mm 입니다")
            String endTime,

            @NotBlank(message = "영업일을 하나 이상 선택해 주세요")
            String operatingDays
    ) {}

    /** O-04 / O-05 구역 추가·이름 변경 (FR-05) */
    public record ZoneRequest(
            @NotBlank(message = "구역 이름을 입력해 주세요")
            @Size(max = 50, message = "구역 이름은 50자 이하입니다")
            String zoneName
    ) {}

    /** O-07 / O-08 좌석 등록·수정 (FR-06). status 는 수정 시 '사용중지' 또는 '빈자리' */
    public record SeatRequest(
            @NotBlank(message = "좌석번호를 입력해 주세요")
            @Size(max = 20, message = "좌석번호는 20자 이하입니다")
            String seatNum,

            @NotBlank(message = "좌석 타입을 입력해 주세요")
            @Size(max = 50, message = "좌석 타입은 50자 이하입니다")
            String seatType,

            @NotNull(message = "적정인원을 입력해 주세요")
            @Min(value = 1, message = "적정인원은 1명 이상이어야 합니다")
            Integer capacity,

            @Size(max = 255, message = "별도품목은 255자 이하입니다")
            String extraItem,

            String status
    ) {}

    /** O-10 일일 예약 타임라인 / O-11 예약자 상세 (FR-20) */
    public record OwnerReservationResponse(
            Long reservationId,
            Long seatId,
            String seatNum,
            String zoneName,
            String memberId,
            String memberName,
            String phone,
            @JsonFormat(pattern = DT) LocalDateTime startDt,
            @JsonFormat(pattern = DT) LocalDateTime endDt,
            Integer visitorCount,
            String status,
            @JsonFormat(pattern = DT) LocalDateTime actualIn,
            @JsonFormat(pattern = DT) LocalDateTime actualOut,
            @JsonFormat(pattern = DT) LocalDateTime createdAt
    ) {}

    /** O-12 구역 통계 + 인기 좌석 TOP 3 (FR-21) */
    public record StatsResponse(Long cafeId, LocalDate from, LocalDate to,
                                List<ZoneStatRow> zones, List<PopularSeat> topSeats) {}

    public record PopularSeat(Long seatId, String seatNum, String zoneName, Long resvCount) {}
}
