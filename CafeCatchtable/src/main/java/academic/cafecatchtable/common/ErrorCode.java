package academic.cafecatchtable.common;

import org.springframework.http.HttpStatus;

/**
 * API 명세서 1장 "HTTP 상태와 오류 코드" + 요구사항 명세서 4.3 "오류 코드".
 * dbCode 는 프로시저 p_result_code 또는 트리거 RAISE_APPLICATION_ERROR 로 올라오는 번호다.
 */
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, null, "입력값을 다시 확인해 주세요"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, null, "로그인이 필요합니다"),
    FORBIDDEN(HttpStatus.FORBIDDEN, null, "접근 권한이 없습니다"),
    NOT_OWNER_OF_RESV(HttpStatus.FORBIDDEN, -20008, "본인 예약만 처리할 수 있습니다"),
    NOT_FOUND(HttpStatus.NOT_FOUND, null, "요청한 정보를 찾을 수 없습니다"),

    DUPLICATE_ID(HttpStatus.CONFLICT, null, "이미 사용 중인 아이디입니다"),
    DUPLICATE_BUSINESS_NUM(HttpStatus.CONFLICT, null, "이미 등록된 사업자등록번호입니다"),
    DUPLICATE_SEAT_NUM(HttpStatus.CONFLICT, null, "이 구역에 같은 좌석번호가 이미 있습니다"),
    ALREADY_OWNER(HttpStatus.CONFLICT, null, "이미 점주로 등록된 회원입니다"),
    DATA_IN_USE(HttpStatus.CONFLICT, null, "연결된 데이터가 있어 처리할 수 없습니다"),
    RESV_TIME_CONFLICT(HttpStatus.CONFLICT, -20004, "이미 예약된 시간입니다"),
    MEMBER_TIME_CONFLICT(HttpStatus.CONFLICT, -20005, "같은 시간에 다른 예약이 있습니다"),
    SEAT_HAS_RESERVATION(HttpStatus.CONFLICT, -20010, "예약이 남아 있는 좌석입니다"),

    OUT_OF_BUSINESS_HOURS(HttpStatus.UNPROCESSABLE_CONTENT, -20001, "영업시간 안에서만 예약할 수 있습니다"),
    OUT_OF_BOOKING_RANGE(HttpStatus.UNPROCESSABLE_CONTENT, -20002, "오늘부터 7일 이내만 예약할 수 있습니다"),
    OVER_CAPACITY(HttpStatus.UNPROCESSABLE_CONTENT, -20003, "이 좌석의 적정인원을 넘었습니다"),
    CHECKIN_WINDOW_CLOSED(HttpStatus.UNPROCESSABLE_CONTENT, -20006, "입실 10분 전부터 15분 후까지 체크인할 수 있습니다"),
    INVALID_STATE(HttpStatus.UNPROCESSABLE_CONTENT, -20007, "지금은 처리할 수 없는 예약입니다"),
    SEAT_DISABLED(HttpStatus.UNPROCESSABLE_CONTENT, -20009, "현재 운영하지 않는 좌석입니다"),

    NOT_IMPLEMENTED(HttpStatus.NOT_IMPLEMENTED, null, "아직 구현되지 않은 기능입니다"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, null, "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요");

    private final HttpStatus status;
    private final Integer dbCode;
    private final String message;

    ErrorCode(HttpStatus status, Integer dbCode, String message) {
        this.status = status;
        this.dbCode = dbCode;
        this.message = message;
    }

    public HttpStatus status() { return status; }
    public Integer dbCode() { return dbCode; }
    public String message() { return message; }

    /** -20001 ~ -20010 → ErrorCode. 정의되지 않은 번호면 null. */
    public static ErrorCode fromDbCode(int dbCode) {
        for (ErrorCode c : values()) {
            if (c.dbCode != null && c.dbCode == dbCode) return c;
        }
        return null;
    }
}
