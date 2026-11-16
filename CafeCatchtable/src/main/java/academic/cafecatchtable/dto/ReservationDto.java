package academic.cafecatchtable.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/** 예약·이용 API (R-01 ~ R-06) 요청·응답 */
public final class ReservationDto {

    private ReservationDto() {}

    private static final String DT = "yyyy-MM-dd'T'HH:mm:ss";

    /** R-01 예약 생성. 회원 ID 는 본문이 아니라 세션에서 꺼낸다. */
    public record CreateRequest(
            @NotNull(message = "좌석을 선택해 주세요")
            Long seatId,

            @NotNull(message = "입실 일시를 선택해 주세요")
            LocalDateTime startDt,

            @NotNull(message = "이용 시간을 선택해 주세요")
            @Min(value = 60, message = "이용 시간은 1~4시간입니다")
            @Max(value = 240, message = "이용 시간은 1~4시간입니다")
            Integer stayMinutes,

            @NotNull(message = "인원을 입력해 주세요")
            @Min(value = 1, message = "인원은 1명 이상입니다")
            Integer visitorCount
    ) {}

    /**
     * R-01 / R-02 / R-03 응답.
     * remainingMinutes 는 '이용중'일 때만, actualOut 은 값이 있을 때만 들어간다.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ReservationResponse(
            Long reservationId,
            String cafeName,
            String zoneName,
            Long seatId,
            String seatNum,
            @JsonFormat(pattern = DT) LocalDateTime startDt,
            @JsonFormat(pattern = DT) LocalDateTime endDt,
            Integer visitorCount,
            String status,
            @JsonFormat(pattern = DT) LocalDateTime actualIn,
            @JsonFormat(pattern = DT) LocalDateTime actualOut,
            Long remainingMinutes,
            @JsonFormat(pattern = DT) LocalDateTime createdAt
    ) {}

    /** R-04 / R-05 / R-06 응답 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record StatusResponse(
            Long reservationId,
            String status,
            @JsonFormat(pattern = DT) LocalDateTime actualIn,
            @JsonFormat(pattern = DT) LocalDateTime actualOut
    ) {}

    /** JPQL 조회 결과 (RESERVATION + SEAT + ZONE + CAFE) */
    public record ReservationRow(
            Long reservationId, String memberId, Long cafeId, String cafeName, String zoneName,
            Long seatId, String seatNum, LocalDateTime startDt, LocalDateTime endDt, Integer visitorCount,
            String status, LocalDateTime actualIn, LocalDateTime actualOut, LocalDateTime createdAt
    ) {}
}
