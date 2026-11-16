package academic.cafecatchtable.common;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 오류 응답 형식 (API 명세서 1장).
 * { "code": "RESV_TIME_CONFLICT", "dbCode": -20004, "message": "이미 예약된 시간입니다" }
 * dbCode 는 프로시저·트리거에서 온 오류일 때만 들어간다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String code, Integer dbCode, String message) {

    public static ErrorResponse of(ErrorCode code, String message) {
        return new ErrorResponse(code.name(), code.dbCode(), message != null ? message : code.message());
    }
}
