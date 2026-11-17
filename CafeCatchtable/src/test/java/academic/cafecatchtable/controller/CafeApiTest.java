package academic.cafecatchtable.controller;

import academic.cafecatchtable.jdbc.SlotRow;
import academic.cafecatchtable.support.Fixtures;
import academic.cafecatchtable.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 카페·좌석 조회 API (C-01 ~ C-04) */
class CafeApiTest extends IntegrationTest {

    private long cafeId;
    private long hallId;
    private long terraceId;
    private long seat2;      // 홀 2인석, 콘센트
    private long seat4;      // 홀 4인석
    private long seatT;      // 테라스 2인석
    private long seatOff;    // 테라스 사용중지

    @BeforeEach
    void setUp() {
        cafeId = fx.cafe(Fixtures.OWNER, "ZZ테스트카페 아르떼");
        hallId = fx.zone(cafeId, "홀");
        terraceId = fx.zone(cafeId, "테라스");
        seat2 = fx.seat(hallId, "H-1", "2인 테이블석", 2, "콘센트");
        seat4 = fx.seat(hallId, "H-2", "4인 테이블석", 4, null);
        seatT = fx.seat(terraceId, "T-1", "2인 테이블석", 2, "쿠션, 콘센트");
        seatOff = fx.seat(terraceId, "T-2", "바 좌석", 1, null, "사용중지");
    }

    @Nested
    @DisplayName("C-01 카페 검색")
    class Search {

        @Test
        void 이름이나_주소_일부로_대소문자_구분없이_찾는다() throws Exception {
            mvc.perform(get("/api/cafes").session(userSession()).param("keyword", "zz테스트카페"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].cafeId").value(cafeId))
                    .andExpect(jsonPath("$[0].cafeName").value("ZZ테스트카페 아르떼"))
                    .andExpect(jsonPath("$[0].zones").doesNotExist());

            mvc.perform(get("/api/cafes").session(userSession()).param("keyword", "테스트구"))
                    .andExpect(jsonPath("$[*].cafeId", hasItem((int) cafeId)));
        }

        @Test
        void 키워드가_없으면_전체_결과가_나온다() throws Exception {
            mvc.perform(get("/api/cafes").session(userSession()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].cafeId", hasItem((int) cafeId)));
        }

        @Test
        void LIKE_와일드카드는_글자로_취급하지_않는다() throws Exception {
            mvc.perform(get("/api/cafes").session(userSession()).param("keyword", "%%%없는카페%%"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        void 대문자로_저장된_영업일도_Mon_형식으로_내려준다() throws Exception {
            long upper = fx.cafe(Fixtures.OWNER, "ZZ대문자영업일", "SUN,MON,WED");
            mvc.perform(get("/api/cafes/{id}", upper).session(userSession()))
                    .andExpect(jsonPath("$.operatingDays").value("Mon,Wed,Sun"));
        }
    }

    @Nested
    @DisplayName("C-02 카페 상세")
    class Detail {

        @Test
        void 카페_정보와_구역_목록을_돌려준다() throws Exception {
            mvc.perform(get("/api/cafes/{id}", cafeId).session(userSession()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cafeName").value("ZZ테스트카페 아르떼"))
                    .andExpect(jsonPath("$.startTime").value("09:00"))
                    .andExpect(jsonPath("$.endTime").value("22:00"))
                    .andExpect(jsonPath("$.operatingDays").value("Mon,Tue,Wed,Thu,Fri,Sat,Sun"))
                    .andExpect(jsonPath("$.zones", hasSize(2)))
                    .andExpect(jsonPath("$.zones[0].zoneId").value(hallId))
                    .andExpect(jsonPath("$.zones[0].zoneName").value("홀"))
                    .andExpect(jsonPath("$.zones[0].cafeId").doesNotExist());
        }

        @Test
        void 없는_카페면_404() throws Exception {
            mvc.perform(get("/api/cafes/{id}", 987654321L).session(userSession()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("C-03 좌석 조회")
    class Seats {

        @Test
        void 모든_좌석과_현재_상태를_돌려준다() throws Exception {
            mvc.perform(get("/api/cafes/{id}/seats", cafeId).session(userSession()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(4)))
                    .andExpect(jsonPath("$[?(@.seatId == %d)].currentStatus".formatted(seat2)).value("빈자리"))
                    .andExpect(jsonPath("$[?(@.seatId == %d)].currentStatus".formatted(seatOff)).value("사용중지"))
                    .andExpect(jsonPath("$[?(@.seatId == %d)].zoneName".formatted(seatT)).value("테라스"));
        }

        @Test
        void 지금_시각에_걸친_예약완료_예약이_있으면_예약됨() throws Exception {
            fx.reservation(Fixtures.USER, seat2, -0.5, 2, 2, "예약완료");
            mvc.perform(get("/api/cafes/{id}/seats", cafeId).session(userSession()))
                    .andExpect(jsonPath("$[?(@.seatId == %d)].currentStatus".formatted(seat2)).value("예약됨"))
                    .andExpect(jsonPath("$[?(@.seatId == %d)].currentStatus".formatted(seat4)).value("빈자리"));
        }

        @Test
        void 이미_끝났거나_취소된_예약은_예약됨으로_보지_않는다() throws Exception {
            fx.reservation(Fixtures.USER, seat2, -3, 1, 2, "예약완료");
            fx.reservation(Fixtures.USER, seat4, -0.5, 2, 2, "예약취소");
            mvc.perform(get("/api/cafes/{id}/seats", cafeId).session(userSession()))
                    .andExpect(jsonPath("$[?(@.seatId == %d)].currentStatus".formatted(seat2)).value("빈자리"))
                    .andExpect(jsonPath("$[?(@.seatId == %d)].currentStatus".formatted(seat4)).value("빈자리"));
        }

        @Test
        void 구역_타입_별도품목_인원으로_거른다() throws Exception {
            mvc.perform(get("/api/cafes/{id}/seats", cafeId).session(userSession()).param("zoneId", String.valueOf(terraceId)))
                    .andExpect(jsonPath("$", hasSize(2)));
            mvc.perform(get("/api/cafes/{id}/seats", cafeId).session(userSession()).param("seatType", "2인 테이블석"))
                    .andExpect(jsonPath("$", hasSize(2)));
            mvc.perform(get("/api/cafes/{id}/seats", cafeId).session(userSession()).param("extraItem", "콘센트"))
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[*].seatId", not(hasItem((int) seat4))));
            mvc.perform(get("/api/cafes/{id}/seats", cafeId).session(userSession()).param("visitors", "3"))
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].seatId").value(seat4));
        }

        @Test
        void 없는_카페면_404() throws Exception {
            mvc.perform(get("/api/cafes/{id}/seats", 987654321L).session(userSession()))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("C-04 예약 가능 시간대 (SP_GET_AVAILABLE_SLOTS 는 Mock)")
    class Slots {

        private LocalDate today() {
            return jdbc.queryForObject("SELECT TRUNC(SYSDATE) FROM DUAL", java.sql.Date.class).toLocalDate();
        }

        @Test
        void 프로시저_결과를_좌석별로_묶어_돌려준다() throws Exception {
            LocalDate date = today().plusDays(1);
            when(slotProcedureDao.getAvailableSlots(cafeId, date, 2)).thenReturn(List.of(
                    new SlotRow(seat2, "H-1", "홀", date.atTime(9, 0), true),
                    new SlotRow(seat2, "H-1", "홀", date.atTime(9, 30), false),
                    new SlotRow(seat4, "H-2", "홀", date.atTime(9, 0), true)));

            mvc.perform(get("/api/cafes/{id}/slots", cafeId).session(userSession())
                            .param("date", date.toString()).param("visitors", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cafeId").value(cafeId))
                    .andExpect(jsonPath("$.date").value(date.toString()))
                    .andExpect(jsonPath("$.seats", hasSize(2)))
                    .andExpect(jsonPath("$.seats[0].seatId").value(seat2))
                    .andExpect(jsonPath("$.seats[0].slots", hasSize(2)))
                    .andExpect(jsonPath("$.seats[0].slots[0].start").value(date + "T09:00:00"))
                    .andExpect(jsonPath("$.seats[0].slots[1].available").value(false))
                    .andExpect(jsonPath("$.seats[1].seatNum").value("H-2"));
        }

        @Test
        void visitors를_생략하면_1명으로_조회한다() throws Exception {
            LocalDate date = today();
            when(slotProcedureDao.getAvailableSlots(anyLong(), any(), anyInt())).thenReturn(List.of());
            mvc.perform(get("/api/cafes/{id}/slots", cafeId).session(userSession()).param("date", date.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.seats", hasSize(0)));
            verify(slotProcedureDao).getAvailableSlots(cafeId, date, 1);
        }

        @Test
        void 오늘부터_7일_밖이면_422_dbCode_20002() throws Exception {
            mvc.perform(get("/api/cafes/{id}/slots", cafeId).session(userSession())
                            .param("date", today().plusDays(8).toString()))
                    .andExpect(status().is(422))
                    .andExpect(jsonPath("$.code").value("OUT_OF_BOOKING_RANGE"))
                    .andExpect(jsonPath("$.dbCode").value(-20002));
            mvc.perform(get("/api/cafes/{id}/slots", cafeId).session(userSession())
                            .param("date", today().minusDays(1).toString()))
                    .andExpect(status().is(422));
            verify(slotProcedureDao, never()).getAvailableSlots(anyLong(), any(), anyInt());
        }

        @Test
        void 휴무일이면_422_dbCode_20001() throws Exception {
            LocalDate date = today().plusDays(1);
            DayOfWeek open = date.getDayOfWeek().plus(1);   // 조회일 다음 요일만 영업
            String code = open.name().charAt(0) + open.name().substring(1, 3).toLowerCase();
            long closedCafe = fx.cafe(Fixtures.OWNER, "ZZ휴무카페", code);

            mvc.perform(get("/api/cafes/{id}/slots", closedCafe).session(userSession()).param("date", date.toString()))
                    .andExpect(status().is(422))
                    .andExpect(jsonPath("$.dbCode").value(-20001))
                    .andExpect(jsonPath("$.message").value("영업일이 아닙니다"));
        }

        @Test
        void 날짜가_없거나_형식이_틀리면_400() throws Exception {
            mvc.perform(get("/api/cafes/{id}/slots", cafeId).session(userSession()))
                    .andExpect(status().isBadRequest());
            mvc.perform(get("/api/cafes/{id}/slots", cafeId).session(userSession()).param("date", "2026/11/20"))
                    .andExpect(status().isBadRequest());
            mvc.perform(get("/api/cafes/{id}/slots", cafeId).session(userSession())
                            .param("date", today().toString()).param("visitors", "0"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void 프로시저_호출_클래스가_미구현이면_501() throws Exception {
            when(slotProcedureDao.getAvailableSlots(anyLong(), any(), anyInt()))
                    .thenThrow(new UnsupportedOperationException("SP_GET_AVAILABLE_SLOTS 호출 미구현"));
            mvc.perform(get("/api/cafes/{id}/slots", cafeId).session(userSession()).param("date", today().toString()))
                    .andExpect(status().isNotImplemented())
                    .andExpect(jsonPath("$.code").value("NOT_IMPLEMENTED"));
        }

        @Test
        void 없는_카페면_404() throws Exception {
            mvc.perform(get("/api/cafes/{id}/slots", 987654321L).session(userSession()).param("date", today().toString()))
                    .andExpect(status().isNotFound());
            verify(slotProcedureDao, never()).getAvailableSlots(eq(987654321L), any(), anyInt());
        }
    }
}
