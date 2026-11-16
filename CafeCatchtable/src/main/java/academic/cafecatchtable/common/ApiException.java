package academic.cafecatchtable.common;

/** 서비스·인터셉터에서 던지면 GlobalExceptionHandler 가 API 명세서 오류 형식으로 바꾼다. */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    public ApiException(ErrorCode errorCode) {
        this(errorCode, errorCode.message());
    }

    public ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() { return errorCode; }

    /**
     * 프로시저 p_result_code 검사. 0이면 통과, 아니면 해당 오류(-20001~-20010)로 예외를 던진다.
     */
    public static void throwIfFailed(int resultCode) {
        if (resultCode == 0) return;
        ErrorCode code = ErrorCode.fromDbCode(resultCode);
        if (code == null) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "알 수 없는 처리 결과 코드: " + resultCode);
        }
        throw new ApiException(code);
    }
}
