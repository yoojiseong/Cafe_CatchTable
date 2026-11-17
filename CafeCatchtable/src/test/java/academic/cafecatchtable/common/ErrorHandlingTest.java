package academic.cafecatchtable.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 오류 코드 변환 (API 명세서 1장, 요구사항 4.3) — DB 불필요 */
class ErrorHandlingTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @ParameterizedTest
    @CsvSource({
            "-20001, OUT_OF_BUSINESS_HOURS, 422",
            "-20002, OUT_OF_BOOKING_RANGE,  422",
            "-20003, OVER_CAPACITY,         422",
            "-20004, RESV_TIME_CONFLICT,    409",
            "-20005, MEMBER_TIME_CONFLICT,  409",
            "-20006, CHECKIN_WINDOW_CLOSED, 422",
            "-20007, INVALID_STATE,         422",
            "-20008, NOT_OWNER_OF_RESV,     403",
            "-20009, SEAT_DISABLED,         422",
            "-20010, SEAT_HAS_RESERVATION,  409"
    })
    void 프로시저_결과코드_10개가_모두_매핑된다(int dbCode, ErrorCode expected, int httpStatus) {
        assertThat(ErrorCode.fromDbCode(dbCode)).isEqualTo(expected);
        assertThat(expected.status().value()).isEqualTo(httpStatus);
        assertThatThrownBy(() -> ApiException.throwIfFailed(dbCode))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    @Test
    void 결과코드_0이면_통과_알_수_없는_코드는_500() {
        assertThatCode(() -> ApiException.throwIfFailed(0)).doesNotThrowAnyException();
        assertThatThrownBy(() -> ApiException.throwIfFailed(-20099))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR));
    }

    @Test
    void 트리거_ORA_20010은_원인_사슬에서_찾아_409_dbCode로_바꾼다() {
        SQLException ora = new SQLException("ORA-20010: 예약이 남아 있는 좌석입니다", "72000", 20010);
        Exception wrapped = new RuntimeException("JPA 래핑", new IllegalStateException("중간", ora));

        ResponseEntity<ErrorResponse> res = handler.handleOther(wrapped);

        assertThat(res.getStatusCode().value()).isEqualTo(409);
        assertThat(res.getBody()).isEqualTo(new ErrorResponse("SEAT_HAS_RESERVATION", -20010, "예약이 남아 있는 좌석입니다"));
    }

    @Test
    void ORA_제약조건_오류는_의미있는_응답으로() {
        assertThat(handler.handleOther(dataError(2292)).getBody().code()).isEqualTo("DATA_IN_USE");   // 자식 행 존재
        assertThat(handler.handleOther(dataError(1)).getStatusCode().value()).isEqualTo(409);         // UNIQUE
        assertThat(handler.handleOther(dataError(2290)).getStatusCode().value()).isEqualTo(400);      // CHECK
        assertThat(handler.handleOther(dataError(1400)).getStatusCode().value()).isEqualTo(400);      // NOT NULL
        assertThat(handler.handleOther(dataError(12899)).getStatusCode().value()).isEqualTo(400);     // 길이 초과
    }

    @Test
    void 그_밖의_오류는_500이고_내부_메시지를_노출하지_않는다() {
        ResponseEntity<ErrorResponse> res = handler.handleOther(new IllegalStateException("secret detail"));
        assertThat(res.getStatusCode().value()).isEqualTo(500);
        assertThat(res.getBody().message()).doesNotContain("secret");
        assertThat(res.getBody().dbCode()).isNull();
    }

    @Test
    void 미구현_프로시저_호출은_501() {
        assertThat(handler.handleNotImplemented(new UnsupportedOperationException("x")).getStatusCode().value())
                .isEqualTo(501);
    }

    private static Exception dataError(int oraCode) {
        return new DataIntegrityViolationException("db", new SQLException("ORA-" + oraCode, "23000", oraCode));
    }
}
