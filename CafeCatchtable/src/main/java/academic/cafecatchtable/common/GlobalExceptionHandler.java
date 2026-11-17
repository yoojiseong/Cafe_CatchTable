package academic.cafecatchtable.common;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.sql.SQLException;

/**
 * 모든 /api 오류를 { code, dbCode, message } 로 바꾼다 (API 명세서 1장).
 * - ApiException            : 서비스가 판단한 오류 (프로시저 p_result_code 포함)
 * - SQLException(ORA-200xx) : 트리거 RAISE_APPLICATION_ERROR (예: TRG_SEAT_DELETE_GUARD → -20010)
 *                             JPA·JdbcTemplate 이 감싼 예외도 원인을 따라가 getErrorCode() 로 찾는다.
 */
@RestControllerAdvice(annotations = RestController.class)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** ORA-00001 unique constraint violated */
    private static final int ORA_UNIQUE = 1;
    /** ORA-02292 integrity constraint violated - child record found */
    private static final int ORA_CHILD_FOUND = 2292;
    /** ORA-02290 check constraint violated (CK_MEMBER_PHONE, CK_CAFE_TIME_ORDER 등) */
    private static final int ORA_CHECK = 2290;
    /** ORA-01400 cannot insert NULL */
    private static final int ORA_NOT_NULL = 1400;
    /** ORA-12899 value too large for column */
    private static final int ORA_TOO_LARGE = 12899;

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException e) {
        return build(e.getErrorCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValid(MethodArgumentNotValidException e) {
        FieldError fe = e.getBindingResult().getFieldError();
        String msg = fe != null && fe.getDefaultMessage() != null
                ? fe.getDefaultMessage()
                : ErrorCode.VALIDATION_FAILED.message();
        return build(ErrorCode.VALIDATION_FAILED, msg);
    }

    @ExceptionHandler({
            HandlerMethodValidationException.class,
            ConstraintViolationException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception e) {
        return build(ErrorCode.VALIDATION_FAILED, null);
    }

    /** 아직 작성하지 않은 프로시저 호출 클래스(jdbc 패키지)가 던진다 */
    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<ErrorResponse> handleNotImplemented(UnsupportedOperationException e) {
        log.warn("미구현 기능 호출: {}", e.getMessage());
        return build(ErrorCode.NOT_IMPLEMENTED, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleOther(Exception e) {
        SQLException sql = findSqlException(e);
        if (sql != null) {
            int ora = sql.getErrorCode();                       // ORA-20010 → 20010
            ErrorCode fromDb = ErrorCode.fromDbCode(-ora);      // → -20010
            if (fromDb != null) return build(fromDb, null);
            if (ora == ORA_CHILD_FOUND) return build(ErrorCode.DATA_IN_USE, null);
            if (ora == ORA_UNIQUE) return build(ErrorCode.DATA_IN_USE, "이미 존재하는 데이터입니다");
            if (ora == ORA_CHECK || ora == ORA_NOT_NULL || ora == ORA_TOO_LARGE) {
                return build(ErrorCode.VALIDATION_FAILED, null);   // 웹 검증을 통과했지만 DB 제약에 걸린 경우
            }
        }
        log.error("처리되지 않은 오류", e);
        return build(ErrorCode.INTERNAL_ERROR, null);
    }

    /** 예외 원인 사슬에서 SQLException 을 찾는다. 없으면 null. */
    public static SQLException findSqlException(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof SQLException s) return s;
            if (c.getCause() == c) break;
        }
        return null;
    }

    /** ORA-00001(UNIQUE 위반)인지 */
    public static boolean isUniqueViolation(Throwable t) {
        SQLException s = findSqlException(t);
        return s != null && s.getErrorCode() == ORA_UNIQUE;
    }

    private ResponseEntity<ErrorResponse> build(ErrorCode code, String message) {
        return ResponseEntity.status(code.status()).body(ErrorResponse.of(code, message));
    }
}
