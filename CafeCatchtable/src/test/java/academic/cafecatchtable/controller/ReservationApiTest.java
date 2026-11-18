package academic.cafecatchtable.controller;

import academic.cafecatchtable.jdbc.CreateReservationResult;
import academic.cafecatchtable.support.Fixtures;
import academic.cafecatchtable.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.allOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 예약·이용 API (R-01 ~ R-06)
 * 생성·취소·체크인·퇴실은 프로시저 호출 클래스(ReservationProcedureCaller)를 Mock 으로 두고
 * 서비스의 입력 검증, 프로시저에 넘기는 값, 결과 코드 → HTTP 응답 변환을 검증한다.
 */
class ReservationApiTest extends IntegrationTest {

    private long cafeId;
    private long seatId;

    @BeforeEach
    void setUp() {
        cafeId = fx.cafe(Fixtures.OWNER, "ZZ예약테스트카페");
        long zoneId = fx.zone(cafeId, "창가");
        seatId = fx.seat(zoneId, "W-1", "2인 테이블석", 2, "콘센트");
    }

    private static String createBody(long seatId, String startDt, int stay, int visitors) {
        return """
                {"seatId":%d,"startDt":"%s","stayMinutes":%d,"visitorCount":%d}
                """.formatted(seatId, startDt, stay, visitors);
    }

    @Nested
    @DisplayName("R-01 예약 생성 (SP_CREATE_RESERVATION 은 Mock)")
    class Create {

        @Test
        void 세션_회원ID와_서버가_계산한_종료시각을_프로시저에_넘기고_201_Location() throws Exception {
            LocalDateTime start = LocalDateTime.of(2030, 1, 2, 10, 0);
            // 프로시저가 INSERT 했다고 가정하고 같은 내용의 행을 미리 만들어 둔다
            long created = fx.reservationAt(Fixtures.USER, seatId, "2030-01-02 10:00", "2030-01-02 11:30", 2, "예약완료");
            when(reservationProcedureCaller.createReservation(Fixtures.USER, seatId, start, start.plusMinutes(90), 2))
                    .thenReturn(new CreateReservationResult(created, 0));

            mvc.perform(post("/api/reservations").session(userSession()).contentType(MediaType.APPLICATION_JSON)
                            .content(createBody(seatId, "2030-01-02T10:00:45", 90, 2)))   // 초는 버림
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", "/api/reservations/" + created))
                    .andExpect(jsonPath("$.reservationId").value(created))
                    .andExpect(jsonPath("$.cafeName").value("ZZ예약테스트카페"))
                    .andExpect(jsonPath("$.zoneName").value("창가"))
                    .andExpect(jsonPath("$.seatNum").value("W-1"))
                    .andExpect(jsonPath("$.startDt").value("2030-01-02T10:00:00"))
                    .andExpect(jsonPath("$.endDt").value("2030-01-02T11:30:00"))
                    .andExpect(jsonPath("$.status").value("예약완료"));

            verify(reservationProcedureCaller).createReservation(Fixtures.USER, seatId, start, start.plusMinutes(90), 2);
        }

        @Test
        void 프로시저_결과코드를_오류_응답으로_바꾼다() throws Exception {
            assertCreateFails(-20004, 409, "RESV_TIME_CONFLICT");
            assertCreateFails(-20005, 409, "MEMBER_TIME_CONFLICT");
            assertCreateFails(-20001, 422, "OUT_OF_BUSINESS_HOURS");
            assertCreateFails(-20002, 422, "OUT_OF_BOOKING_RANGE");
            assertCreateFails(-20003, 422, "OVER_CAPACITY");
            assertCreateFails(-20009, 422, "SEAT_DISABLED");
        }

        private void assertCreateFails(int resultCode, int httpStatus, String code) throws Exception {
            when(reservationProcedureCaller.createReservation(anyString(), anyLong(), any(), any(), anyInt()))
                    .thenReturn(new CreateReservationResult(null, resultCode));
            mvc.perform(post("/api/reservations").session(userSession()).contentType(MediaType.APPLICATION_JSON)
                            .content(createBody(seatId, "2030-01-02T10:00:00", 60, 1)))
                    .andExpect(status().is(httpStatus))
                    .andExpect(jsonPath("$.code").value(code))
                    .andExpect(jsonPath("$.dbCode").value(resultCode));
        }

        @Test
        void 알_수_없는_결과코드나_예약번호가_없으면_500() throws Exception {
            when(reservationProcedureCaller.createReservation(anyString(), anyLong(), any(), any(), anyInt()))
                    .thenReturn(new CreateReservationResult(null, -1));
            mvc.perform(post("/api/reservations").session(userSession()).contentType(MediaType.APPLICATION_JSON)
                            .content(createBody(seatId, "2030-01-02T10:00:00", 60, 1)))
                    .andExpect(status().isInternalServerError());

            when(reservationProcedureCaller.createReservation(anyString(), anyLong(), any(), any(), anyInt()))
                    .thenReturn(new CreateReservationResult(null, 0));
            mvc.perform(post("/api/reservations").session(userSession()).contentType(MediaType.APPLICATION_JSON)
                            .content(createBody(seatId, "2030-01-02T10:00:00", 60, 1)))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        void 이용시간은_1에서_4시간_30분_단위() throws Exception {
            for (int stay : new int[]{30, 75, 270}) {
                mvc.perform(post("/api/reservations").session(userSession()).contentType(MediaType.APPLICATION_JSON)
                                .content(createBody(seatId, "2030-01-02T10:00:00", stay, 1)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
            }
            verify(reservationProcedureCaller, never()).createReservation(anyString(), anyLong(), any(), any(), anyInt());
        }

        @Test
        void 필수값이_없거나_인원이_0이면_400() throws Exception {
            mvc.perform(post("/api/reservations").session(userSession()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"seatId\":" + seatId + ",\"stayMinutes\":60,\"visitorCount\":1}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("입실 일시를 선택해 주세요"));
            mvc.perform(post("/api/reservations").session(userSession()).contentType(MediaType.APPLICATION_JSON)
                            .content(createBody(seatId, "2030-01-02T10:00:00", 60, 0)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void 없는_좌석이면_404() throws Exception {
            mvc.perform(post("/api/reservations").session(userSession()).contentType(MediaType.APPLICATION_JSON)
                            .content(createBody(987654321L, "2030-01-02T10:00:00", 60, 1)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void 로그인하지_않으면_401() throws Exception {
            mvc.perform(post("/api/reservations").contentType(MediaType.APPLICATION_JSON)
                            .content(createBody(seatId, "2030-01-02T10:00:00", 60, 1)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("R-02 내 예약 목록 / R-03 예약 상세")
    class Query {

        private long future;
        private long inUse;
        private long done;
        private long noShow;

        @BeforeEach
        void reservations() {
            future = fx.reservation(Fixtures.USER, seatId, 24, 2, 2, "예약완료");
            inUse = fx.reservation(Fixtures.USER, seatId, -1, 2, 1, "이용중");
            done = fx.reservation(Fixtures.USER, seatId, -48, 2, 1, "이용완료");
            noShow = fx.reservation(Fixtures.USER, seatId, -72, 1, 1, "노쇼");
            fx.reservation(Fixtures.OTHER_USER, seatId, 48, 1, 1, "예약완료");
        }

        @Test
        void 전체는_본인_예약만_입실시각_최신순() throws Exception {
            mvc.perform(get("/api/reservations/me").session(userSession()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].reservationId",
                            contains((int) future, (int) inUse, (int) done, (int) noShow)));
        }

        @Test
        void active는_예약완료와_이용중() throws Exception {
            mvc.perform(get("/api/reservations/me").session(userSession()).param("type", "active"))
                    .andExpect(jsonPath("$[*].reservationId", contains((int) future, (int) inUse)))
                    // 이용중이면 남은 시간(분)이 들어간다. 종료까지 약 60분
                    .andExpect(jsonPath("$[1].remainingMinutes", allOf(greaterThan(55), lessThanOrEqualTo(61))))
                    .andExpect(jsonPath("$[1].actualIn").exists())
                    .andExpect(jsonPath("$[0].remainingMinutes").doesNotExist());
        }

        @Test
        void past는_이용완료_취소_노쇼() throws Exception {
            mvc.perform(get("/api/reservations/me").session(userSession()).param("type", "past"))
                    .andExpect(jsonPath("$[*].reservationId", contains((int) done, (int) noShow)))
                    .andExpect(jsonPath("$[0].actualOut").exists());
        }

        @Test
        void type이_잘못되면_400() throws Exception {
            mvc.perform(get("/api/reservations/me").session(userSession()).param("type", "soon"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void 상세는_본인_예약만_볼_수_있다() throws Exception {
            mvc.perform(get("/api/reservations/{id}", future).session(userSession()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reservationId").value(future))
                    .andExpect(jsonPath("$.cafeName").value("ZZ예약테스트카페"))
                    .andExpect(jsonPath("$.visitorCount").value(2))
                    .andExpect(jsonPath("$.createdAt").exists());

            mvc.perform(get("/api/reservations/{id}", future).session(sessionOf(Fixtures.OTHER_USER, "다른회원", "USER")))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/reservations/{id}", 987654321L).session(userSession()))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("R-04 취소 / R-05 체크인 / R-06 퇴실 (프로시저는 Mock)")
    class StatusChange {

        private long resvId;

        @BeforeEach
        void reservation() {
            resvId = fx.reservation(Fixtures.USER, seatId, 24, 2, 2, "예약완료");
        }

        @Test
        void 성공하면_프로시저_실행_후_상태를_다시_읽어_돌려준다() throws Exception {
            when(reservationProcedureCaller.cancelReservation(resvId, Fixtures.USER)).thenReturn(0);
            mvc.perform(post("/api/reservations/{id}/cancel", resvId).session(userSession()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reservationId").value(resvId))
                    .andExpect(jsonPath("$.status").value("예약완료"));   // Mock 이라 실제 변경은 없음
            verify(reservationProcedureCaller).cancelReservation(resvId, Fixtures.USER);

            when(reservationProcedureCaller.checkIn(resvId, Fixtures.USER)).thenReturn(0);
            mvc.perform(post("/api/reservations/{id}/check-in", resvId).session(userSession()))
                    .andExpect(status().isOk());
            verify(reservationProcedureCaller).checkIn(resvId, Fixtures.USER);

            when(reservationProcedureCaller.checkOut(resvId, Fixtures.USER)).thenReturn(0);
            mvc.perform(post("/api/reservations/{id}/check-out", resvId).session(userSession()))
                    .andExpect(status().isOk());
            verify(reservationProcedureCaller).checkOut(resvId, Fixtures.USER);
        }

        @Test
        void 프로시저_결과코드를_오류_응답으로_바꾼다() throws Exception {
            when(reservationProcedureCaller.cancelReservation(resvId, Fixtures.USER)).thenReturn(-20007);
            mvc.perform(post("/api/reservations/{id}/cancel", resvId).session(userSession()))
                    .andExpect(status().is(422))
                    .andExpect(jsonPath("$.code").value("INVALID_STATE"))
                    .andExpect(jsonPath("$.dbCode").value(-20007));

            when(reservationProcedureCaller.checkIn(resvId, Fixtures.USER)).thenReturn(-20006);
            mvc.perform(post("/api/reservations/{id}/check-in", resvId).session(userSession()))
                    .andExpect(status().is(422))
                    .andExpect(jsonPath("$.code").value("CHECKIN_WINDOW_CLOSED"));

            when(reservationProcedureCaller.checkOut(resvId, Fixtures.USER)).thenReturn(-20008);
            mvc.perform(post("/api/reservations/{id}/check-out", resvId).session(userSession()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("NOT_OWNER_OF_RESV"))
                    .andExpect(jsonPath("$.dbCode").value(-20008));
        }

        @Test
        void 없는_예약이면_프로시저를_부르지_않고_404() throws Exception {
            mvc.perform(post("/api/reservations/{id}/cancel", 987654321L).session(userSession()))
                    .andExpect(status().isNotFound());
            mvc.perform(post("/api/reservations/{id}/check-in", 987654321L).session(userSession()))
                    .andExpect(status().isNotFound());
            mvc.perform(post("/api/reservations/{id}/check-out", 987654321L).session(userSession()))
                    .andExpect(status().isNotFound());
            verify(reservationProcedureCaller, never()).cancelReservation(anyLong(), anyString());
            verify(reservationProcedureCaller, never()).checkIn(anyLong(), anyString());
            verify(reservationProcedureCaller, never()).checkOut(anyLong(), anyString());
        }

        @Test
        void 프로시저_호출_클래스가_미구현이면_501() throws Exception {
            when(reservationProcedureCaller.cancelReservation(anyLong(), anyString()))
                    .thenThrow(new UnsupportedOperationException("미구현"));
            mvc.perform(post("/api/reservations/{id}/cancel", resvId).session(userSession()))
                    .andExpect(status().isNotImplemented());
        }
    }

    @Test
    @DisplayName("RESERVATION 은 프로시저 밖에서 바꿀 수 없다 (TRG_RESV_VALIDATE → ORA-20007)")
    void 직접_DML은_트리거가_막는다() {
        long id = fx.reservation(Fixtures.USER, seatId, 24, 1, 1, "예약완료");
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        jdbc.update("UPDATE RESERVATION SET STATUS = '예약취소' WHERE RESERVATION_ID = ?", id))
                .hasMessageContaining("ORA-20007");
    }
}
