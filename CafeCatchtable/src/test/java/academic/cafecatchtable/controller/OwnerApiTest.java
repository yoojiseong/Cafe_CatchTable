package academic.cafecatchtable.controller;

import academic.cafecatchtable.jdbc.ZoneStatRow;
import academic.cafecatchtable.support.Fixtures;
import academic.cafecatchtable.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 점주 API (O-01 ~ O-12) */
class OwnerApiTest extends IntegrationTest {

    @PersistenceContext
    private EntityManager em;

    private long cafeId;
    private long zoneId;
    private long seatId;
    private long otherCafeId;
    private long otherZoneId;
    private long otherSeatId;

    @BeforeEach
    void setUp() {
        cafeId = fx.cafe(Fixtures.OWNER, "ZZ점주카페");
        zoneId = fx.zone(cafeId, "1층");
        seatId = fx.seat(zoneId, "A-1", "2인 테이블석", 2, "콘센트");

        otherCafeId = fx.cafe(Fixtures.OTHER_OWNER, "ZZ남의카페");
        otherZoneId = fx.zone(otherCafeId, "남의구역");
        otherSeatId = fx.seat(otherZoneId, "X-1", "1인석", 1, null);
    }

    private static String cafeBody(String name, String start, String end, String days) {
        return """
                {"cafeName":"%s","address":"서울 마포구 와우산로 1","phone":"02-123-4567",
                 "startTime":"%s","endTime":"%s","operatingDays":"%s"}
                """.formatted(name, start, end, days);
    }

    private static String seatBody(String seatNum, int capacity, String status) {
        return """
                {"seatNum":"%s","seatType":"창가석","capacity":%d,"extraItem":"콘센트, 쿠션"%s}
                """.formatted(seatNum, capacity, status == null ? "" : ",\"status\":\"" + status + "\"");
    }

    @Nested
    @DisplayName("권한")
    class Authorization {

        @Test
        void 일반회원은_점주_API에_403() throws Exception {
            mvc.perform(get("/api/owner/cafes").session(userSession()))
                    .andExpect(status().isForbidden());
        }

        @Test
        void 미로그인은_401() throws Exception {
            mvc.perform(get("/api/owner/cafes")).andExpect(status().isUnauthorized());
        }

        @Test
        void 다른_점주의_카페_구역_좌석은_403() throws Exception {
            mvc.perform(put("/api/owner/cafes/{id}", otherCafeId).session(ownerSession())
                            .contentType(MediaType.APPLICATION_JSON).content(cafeBody("x", "10:00", "20:00", "Mon")))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/owner/cafes/{id}/zones", otherCafeId).session(ownerSession())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"zoneName\":\"x\"}"))
                    .andExpect(status().isForbidden());
            mvc.perform(put("/api/owner/zones/{id}", otherZoneId).session(ownerSession())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"zoneName\":\"x\"}"))
                    .andExpect(status().isForbidden());
            mvc.perform(delete("/api/owner/zones/{id}", otherZoneId).session(ownerSession()))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/owner/zones/{id}/seats", otherZoneId).session(ownerSession())
                            .contentType(MediaType.APPLICATION_JSON).content(seatBody("Z-9", 1, null)))
                    .andExpect(status().isForbidden());
            mvc.perform(put("/api/owner/seats/{id}", otherSeatId).session(ownerSession())
                            .contentType(MediaType.APPLICATION_JSON).content(seatBody("X-1", 1, null)))
                    .andExpect(status().isForbidden());
            mvc.perform(delete("/api/owner/seats/{id}", otherSeatId).session(ownerSession()))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/owner/cafes/{id}/reservations", otherCafeId).session(ownerSession()))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/owner/cafes/{id}/stats", otherCafeId).session(ownerSession()))
                    .andExpect(status().isForbidden());

            assertThat(fx.count("SELECT COUNT(*) FROM SEAT WHERE SEAT_ID = ?", otherSeatId)).isEqualTo(1);
        }

        @Test
        void 없는_대상은_404() throws Exception {
            mvc.perform(put("/api/owner/zones/{id}", 987654321L).session(ownerSession())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"zoneName\":\"x\"}"))
                    .andExpect(status().isNotFound());
            mvc.perform(delete("/api/owner/seats/{id}", 987654321L).session(ownerSession()))
                    .andExpect(status().isNotFound());
            mvc.perform(get("/api/owner/reservations/{id}", 987654321L).session(ownerSession()))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("O-01 ~ O-03 카페")
    class Cafes {

        @Test
        void 내_카페만_이름순으로_보인다() throws Exception {
            long second = fx.cafe(Fixtures.OWNER, "ZZ가나다카페");
            mvc.perform(get("/api/owner/cafes").session(ownerSession()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].cafeId", contains((int) second, (int) cafeId)))
                    .andExpect(jsonPath("$[*].cafeId", not(hasItem((int) otherCafeId))));
        }

        @Test
        void 등록하면_영업일은_표준순서로_24시는_23시59분으로_저장된다() throws Exception {
            mvc.perform(post("/api/owner/cafes").session(ownerSession()).contentType(MediaType.APPLICATION_JSON)
                            .content(cafeBody("ZZ새카페", "10:00", "24:00", "sun, MON ,wed")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.cafeId").exists())
                    .andExpect(jsonPath("$.endTime").value("23:59"))
                    .andExpect(jsonPath("$.operatingDays").value("Mon,Wed,Sun"));

            assertThat(jdbc.queryForObject("SELECT OWNER_ID FROM CAFE WHERE CAFE_NAME = 'ZZ새카페'", String.class))
                    .isEqualTo(Fixtures.OWNER);
        }

        @Test
        void 수정() throws Exception {
            mvc.perform(put("/api/owner/cafes/{id}", cafeId).session(ownerSession()).contentType(MediaType.APPLICATION_JSON)
                            .content(cafeBody("ZZ이름변경", "08:30", "21:00", "Sat,Sun")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cafeName").value("ZZ이름변경"))
                    .andExpect(jsonPath("$.startTime").value("08:30"));
            em.flush();   // 테스트 트랜잭션은 커밋되지 않으므로 변경 감지 결과를 직접 DB 로 내보낸 뒤 확인
            assertThat(jdbc.queryForObject("SELECT OPERATING_DAYS FROM CAFE WHERE CAFE_ID = ?", String.class, cafeId))
                    .isEqualTo("Sat,Sun");
        }

        @Test
        void 입력값_검증() throws Exception {
            // 종료 ≤ 시작
            mvc.perform(post("/api/owner/cafes").session(ownerSession()).contentType(MediaType.APPLICATION_JSON)
                            .content(cafeBody("ZZ", "20:00", "10:00", "Mon")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("영업 종료 시간은 시작 시간보다 늦어야 합니다"));
            // 알 수 없는 요일
            mvc.perform(post("/api/owner/cafes").session(ownerSession()).contentType(MediaType.APPLICATION_JSON)
                            .content(cafeBody("ZZ", "10:00", "20:00", "Mon,Funday")))
                    .andExpect(status().isBadRequest());
            // 시간 형식
            mvc.perform(post("/api/owner/cafes").session(ownerSession()).contentType(MediaType.APPLICATION_JSON)
                            .content(cafeBody("ZZ", "9:00", "20:00", "Mon")))
                    .andExpect(status().isBadRequest());
            // 이름 누락
            mvc.perform(post("/api/owner/cafes").session(ownerSession()).contentType(MediaType.APPLICATION_JSON)
                            .content(cafeBody("", "10:00", "20:00", "Mon")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("카페 이름을 입력해 주세요"));
        }
    }

    @Nested
    @DisplayName("O-04 ~ O-06 구역")
    class Zones {

        @Test
        void 추가_이름변경() throws Exception {
            mvc.perform(post("/api/owner/cafes/{id}/zones", cafeId).session(ownerSession())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"zoneName\":\" 테라스 \"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.cafeId").value(cafeId))
                    .andExpect(jsonPath("$.zoneName").value("테라스"));

            mvc.perform(put("/api/owner/zones/{id}", zoneId).session(ownerSession())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"zoneName\":\"2층\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.zoneName").value("2층"));
        }

        @Test
        void 삭제하면_구역의_좌석과_지난_예약도_함께_지워진다() throws Exception {
            long pastResv = fx.reservation(Fixtures.USER, seatId, -48, 2, 2, "이용완료");

            mvc.perform(delete("/api/owner/zones/{id}", zoneId).session(ownerSession()))
                    .andExpect(status().isNoContent());

            assertThat(fx.count("SELECT COUNT(*) FROM ZONE WHERE ZONE_ID = ?", zoneId)).isZero();
            assertThat(fx.count("SELECT COUNT(*) FROM SEAT WHERE SEAT_ID = ?", seatId)).isZero();
            // FK_RESV_SEAT ON DELETE CASCADE + TRG_RESV_HISTORY 'DELETE' 이력
            assertThat(fx.count("SELECT COUNT(*) FROM RESERVATION WHERE RESERVATION_ID = ?", pastResv)).isZero();
            assertThat(fx.count("SELECT COUNT(*) FROM RESERVATION_LOG WHERE RESERVATION_ID = ? AND ACTION = 'DELETE'",
                    pastResv)).isEqualTo(1);
        }

        @Test
        void 유효_예약이_남은_좌석이_있으면_409_dbCode_20010_전체_롤백() throws Exception {
            long freeSeat = fx.seat(zoneId, "A-2", "2인 테이블석", 2, null);
            fx.reservation(Fixtures.USER, seatId, 24, 2, 2, "예약완료");

            mvc.perform(delete("/api/owner/zones/{id}", zoneId).session(ownerSession()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SEAT_HAS_RESERVATION"))
                    .andExpect(jsonPath("$.dbCode").value(-20010));

            assertThat(fx.count("SELECT COUNT(*) FROM SEAT WHERE SEAT_ID IN (?, ?)", seatId, freeSeat)).isEqualTo(2);
            assertThat(fx.count("SELECT COUNT(*) FROM ZONE WHERE ZONE_ID = ?", zoneId)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("O-07 ~ O-09 좌석")
    class Seats {

        @Test
        void 등록하면_빈자리로_저장된다() throws Exception {
            mvc.perform(post("/api/owner/zones/{id}/seats", zoneId).session(ownerSession())
                            .contentType(MediaType.APPLICATION_JSON).content(seatBody("A-2", 4, null)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.seatNum").value("A-2"))
                    .andExpect(jsonPath("$.zoneName").value("1층"))
                    .andExpect(jsonPath("$.capacity").value(4))
                    .andExpect(jsonPath("$.extraItem").value("콘센트, 쿠션"))
                    .andExpect(jsonPath("$.currentStatus").value("빈자리"));
        }

        @Test
        void 같은_구역에_같은_좌석번호면_409() throws Exception {
            mvc.perform(post("/api/owner/zones/{id}/seats", zoneId).session(ownerSession())
                            .contentType(MediaType.APPLICATION_JSON).content(seatBody("A-1", 2, null)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("DUPLICATE_SEAT_NUM"));

            long other = fx.seat(zoneId, "A-2", "2인 테이블석", 2, null);
            mvc.perform(put("/api/owner/seats/{id}", other).session(ownerSession())
                            .contentType(MediaType.APPLICATION_JSON).content(seatBody("A-1", 2, null)))
                    .andExpect(status().isConflict());
        }

        @Test
        void 적정인원이_0이면_400() throws Exception {
            mvc.perform(post("/api/owner/zones/{id}/seats", zoneId).session(ownerSession())
                            .contentType(MediaType.APPLICATION_JSON).content(seatBody("A-3", 0, null)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("적정인원은 1명 이상이어야 합니다"));
        }

        @Test
        void 수정과_사용중지_사용재개() throws Exception {
            mvc.perform(put("/api/owner/seats/{id}", seatId).session(ownerSession())
                            .contentType(MediaType.APPLICATION_JSON).content(seatBody("A-1", 3, "사용중지")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.capacity").value(3))
                    .andExpect(jsonPath("$.currentStatus").value("사용중지"));
            assertThat(fx.seatStatus(seatId)).isEqualTo("사용중지");

            mvc.perform(put("/api/owner/seats/{id}", seatId).session(ownerSession())
                            .contentType(MediaType.APPLICATION_JSON).content(seatBody("A-1", 3, "빈자리")))
                    .andExpect(jsonPath("$.currentStatus").value("빈자리"));
            assertThat(fx.seatStatus(seatId)).isEqualTo("빈자리");
        }

        @Test
        void 이용중인_좌석은_사용재개_요청으로_빈자리가_되지_않는다() throws Exception {
            jdbc.update("UPDATE SEAT SET STATUS = '이용중' WHERE SEAT_ID = ?", seatId);
            mvc.perform(put("/api/owner/seats/{id}", seatId).session(ownerSession())
                            .contentType(MediaType.APPLICATION_JSON).content(seatBody("A-1", 2, "빈자리")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.currentStatus").value("이용중"));
        }

        @Test
        void 상태는_빈자리나_사용중지만_지정할_수_있다() throws Exception {
            mvc.perform(put("/api/owner/seats/{id}", seatId).session(ownerSession())
                            .contentType(MediaType.APPLICATION_JSON).content(seatBody("A-1", 2, "이용중")))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void 사용중지_좌석에는_예약을_넣을_수_없다_TRG_RESV_VALIDATE() {
            jdbc.update("UPDATE SEAT SET STATUS = '사용중지' WHERE SEAT_ID = ?", seatId);
            org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                            fx.reservation(Fixtures.USER, seatId, 24, 1, 1, "예약완료"))
                    .hasMessageContaining("ORA-20009");
        }

        @Test
        void 지난_예약만_있으면_삭제된다() throws Exception {
            fx.reservation(Fixtures.USER, seatId, -48, 2, 2, "노쇼");
            fx.reservation(Fixtures.USER, seatId, 24, 2, 2, "예약취소");   // 취소된 예약은 유효 예약이 아님
            mvc.perform(delete("/api/owner/seats/{id}", seatId).session(ownerSession()))
                    .andExpect(status().isNoContent());
            assertThat(fx.count("SELECT COUNT(*) FROM SEAT WHERE SEAT_ID = ?", seatId)).isZero();
        }

        @Test
        void 앞으로_유효_예약이_있으면_409_dbCode_20010() throws Exception {
            fx.reservation(Fixtures.USER, seatId, 24, 2, 2, "예약완료");
            mvc.perform(delete("/api/owner/seats/{id}", seatId).session(ownerSession()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.dbCode").value(-20010));
            assertThat(fx.count("SELECT COUNT(*) FROM SEAT WHERE SEAT_ID = ?", seatId)).isEqualTo(1);
        }

        @Test
        void 이용중_예약이_있어도_409() throws Exception {
            fx.reservation(Fixtures.USER, seatId, -0.5, 2, 2, "이용중");
            mvc.perform(delete("/api/owner/seats/{id}", seatId).session(ownerSession()))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("O-10 일일 예약 / O-11 예약자 상세")
    class DailyReservations {

        @Test
        void 선택한_날짜에_걸친_예약을_예약자_정보와_함께_돌려준다() throws Exception {
            long seat2 = fx.seat(zoneId, "A-2", "2인 테이블석", 2, null);
            long r1 = fx.reservationAt(Fixtures.USER, seatId, "2030-03-05 10:00", "2030-03-05 12:00", 2, "예약완료");
            long r2 = fx.reservationAt(Fixtures.OTHER_USER, seatId, "2030-03-05 14:00", "2030-03-05 15:00", 1, "예약취소");
            long r3 = fx.reservationAt(Fixtures.USER, seat2, "2030-03-05 23:00", "2030-03-06 01:00", 1, "예약완료");
            fx.reservationAt(Fixtures.USER, seatId, "2030-03-06 10:00", "2030-03-06 11:00", 1, "예약완료");   // 다른 날
            fx.reservationAt(Fixtures.USER, otherSeatId, "2030-03-05 10:00", "2030-03-05 11:00", 1, "예약완료"); // 남의 카페

            mvc.perform(get("/api/owner/cafes/{id}/reservations", cafeId).session(ownerSession()).param("date", "2030-03-05"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].reservationId", contains((int) r1, (int) r2, (int) r3)))
                    .andExpect(jsonPath("$[0].memberName").value("테스트회원"))
                    .andExpect(jsonPath("$[0].phone").value("010-1000-0001"))
                    .andExpect(jsonPath("$[0].visitorCount").value(2))
                    .andExpect(jsonPath("$[0].seatNum").value("A-1"))
                    .andExpect(jsonPath("$[0].zoneName").value("1층"))
                    .andExpect(jsonPath("$[0].startDt").value("2030-03-05T10:00:00"))
                    .andExpect(jsonPath("$[1].status").value("예약취소"));
        }

        @Test
        void 날짜를_생략하면_오늘() throws Exception {
            long today = fx.reservation(Fixtures.USER, seatId, 0, 1, 1, "예약완료");
            mvc.perform(get("/api/owner/cafes/{id}/reservations", cafeId).session(ownerSession()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].reservationId", hasItem((int) today)));
        }

        @Test
        void 예약자_상세는_내_카페_예약만() throws Exception {
            long mine = fx.reservationAt(Fixtures.USER, seatId, "2030-03-05 10:00", "2030-03-05 12:00", 2, "예약완료");
            long others = fx.reservationAt(Fixtures.USER, otherSeatId, "2030-03-05 10:00", "2030-03-05 11:00", 1, "예약완료");

            mvc.perform(get("/api/owner/reservations/{id}", mine).session(ownerSession()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reservationId").value(mine))
                    .andExpect(jsonPath("$.memberId").value(Fixtures.USER))
                    .andExpect(jsonPath("$.seatId").value(seatId))
                    .andExpect(jsonPath("$.createdAt").exists());
            mvc.perform(get("/api/owner/reservations/{id}", others).session(ownerSession()))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("O-12 통계 (SP_ZONE_STATS 는 Mock, 인기 좌석은 실제 SP_POPULAR_SEATS)")
    class Stats {

        @Test
        void 프로시저에는_종료일_다음날_0시를_넘기고_인기좌석_TOP3를_함께_돌려준다() throws Exception {
            long s2 = fx.seat(zoneId, "A-2", "2인 테이블석", 2, null);
            long s3 = fx.seat(zoneId, "A-3", "2인 테이블석", 2, null);
            long s4 = fx.seat(zoneId, "A-4", "2인 테이블석", 2, null);
            // s2: 3건, seatId: 2건(+취소 2건은 제외), s3: 1건, s4: 1건(범위 밖)
            for (int d = 1; d <= 3; d++) {
                fx.reservationAt(Fixtures.USER, s2, "2030-04-0%d 10:00".formatted(d), "2030-04-0%d 11:00".formatted(d), 1, "이용완료");
            }
            fx.reservationAt(Fixtures.USER, seatId, "2030-04-01 13:00", "2030-04-01 14:00", 1, "노쇼");
            fx.reservationAt(Fixtures.USER, seatId, "2030-04-07 13:00", "2030-04-07 14:00", 1, "예약완료");
            fx.reservationAt(Fixtures.USER, seatId, "2030-04-02 13:00", "2030-04-02 14:00", 1, "예약취소");
            fx.reservationAt(Fixtures.USER, seatId, "2030-04-03 13:00", "2030-04-03 14:00", 1, "예약취소");
            fx.reservationAt(Fixtures.USER, s3, "2030-04-05 10:00", "2030-04-05 11:00", 1, "이용완료");
            fx.reservationAt(Fixtures.USER, s4, "2030-04-08 10:00", "2030-04-08 11:00", 1, "이용완료");

            LocalDate from = LocalDate.of(2030, 4, 1);
            LocalDate to = LocalDate.of(2030, 4, 7);
            when(statsProcedureDao.getZoneStats(cafeId, from.atStartOfDay(), to.plusDays(1).atStartOfDay()))
                    .thenReturn(List.of(new ZoneStatRow("1층", 7L, 60.0, 12.5, 1.75, 14.29)));

            mvc.perform(get("/api/owner/cafes/{id}/stats", cafeId).session(ownerSession())
                            .param("from", "2030-04-01").param("to", "2030-04-07"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.from").value("2030-04-01"))
                    .andExpect(jsonPath("$.to").value("2030-04-07"))
                    .andExpect(jsonPath("$.zones", hasSize(1)))
                    .andExpect(jsonPath("$.zones[0].zoneName").value("1층"))
                    .andExpect(jsonPath("$.zones[0].occupancyRate").value(12.5))
                    .andExpect(jsonPath("$.topSeats[*].seatId", contains((int) s2, (int) seatId, (int) s3)))
                    .andExpect(jsonPath("$.topSeats[0].resvCount").value(3))
                    .andExpect(jsonPath("$.topSeats[1].resvCount").value(2));
        }

        @Test
        void 기간을_생략하면_오늘까지_최근_7일() throws Exception {
            LocalDate today = jdbc.queryForObject("SELECT TRUNC(SYSDATE) FROM DUAL", java.sql.Date.class).toLocalDate();
            when(statsProcedureDao.getZoneStats(anyLong(), any(), any())).thenReturn(List.of());

            mvc.perform(get("/api/owner/cafes/{id}/stats", cafeId).session(ownerSession()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.from").value(today.minusDays(6).toString()))
                    .andExpect(jsonPath("$.to").value(today.toString()));
            verify(statsProcedureDao).getZoneStats(cafeId, today.minusDays(6).atStartOfDay(), today.plusDays(1).atStartOfDay());
        }

        @Test
        void 시작일이_종료일보다_늦으면_400() throws Exception {
            mvc.perform(get("/api/owner/cafes/{id}/stats", cafeId).session(ownerSession())
                            .param("from", "2030-04-08").param("to", "2030-04-07"))
                    .andExpect(status().isBadRequest());
            verify(statsProcedureDao, never()).getZoneStats(anyLong(), any(), any());
        }
    }
}
