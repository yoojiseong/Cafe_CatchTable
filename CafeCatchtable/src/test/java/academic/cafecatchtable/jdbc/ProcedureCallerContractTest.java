package academic.cafecatchtable.jdbc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.abort;

/**
 * 프로시저 호출 클래스(순수 JDBC) 계약 테스트.
 *
 * ReservationProcedureCaller / SlotProcedureCaller / StatsProcedureCaller 를 구현하면 실제 프로시저를 불러 검증한다.
 * 아직 UnsupportedOperationException 을 던지는 메서드의 테스트는 "건너뜀(aborted)"으로 표시된다.
 *
 * 주의: 프로시저가 직접 COMMIT 하므로 이 테스트는 롤백 방식을 쓸 수 없다.
 *       테스트 전용 회원·카페(ID 접두어 zzc)를 COMMIT 으로 만들고, @AfterEach 에서 지운다.
 */
@SpringBootTest
@EnabledIf("academic.cafecatchtable.support.OracleAvailable#isUp")
class ProcedureCallerContractTest {

    private static final String USER = "zzcuser01";
    private static final String OTHER_USER = "zzcuser02";
    private static final String OWNER = "zzcowner01";
    private static final String HASH = "$2a$10$OjewBF9Fq5BCqjUsIVwYvO4qTp2ZN8PBDaiaen3EzVTBW/Xmau59K";

    @Autowired ReservationProcedureCaller reservationCaller;
    @Autowired SlotProcedureCaller slotCaller;
    @Autowired StatsProcedureCaller statsCaller;
    @Autowired JdbcTemplate jdbc;

    private long cafeId;
    private long seatId;      // 2인석
    private long seatOff;     // 사용중지
    private final List<Long> createdReservations = new ArrayList<>();

    @BeforeEach
    void setUp() {
        cleanUp();   // 이전 실행이 중간에 끊겼을 때 남은 데이터 정리
        for (String id : List.of(USER, OTHER_USER, OWNER)) {
            jdbc.update("INSERT INTO MEMBER (MEMBER_ID, PASSWORD, NAME, PHONE, ROLE) VALUES (?, ?, ?, '010-9000-0000', 'USER')",
                    id, HASH, "계약테스트");
        }
        jdbc.update("INSERT INTO OWNER (OWNER_ID, BUSINESS_NUM) VALUES (?, '909-09-09090')", OWNER);
        cafeId = insert("INSERT INTO CAFE (OWNER_ID, CAFE_NAME, ADDRESS, PHONE, START_TIME, END_TIME, OPERATING_DAYS)"
                + " VALUES (?, 'ZZC계약테스트카페', '서울', '02-000-0000', '09:00', '22:00', 'Mon,Tue,Wed,Thu,Fri,Sat,Sun')",
                "CAFE_ID", OWNER);
        long zoneId = insert("INSERT INTO ZONE (CAFE_ID, ZONE_NAME) VALUES (?, 'ZZC홀')", "ZONE_ID", cafeId);
        seatId = insert("INSERT INTO SEAT (ZONE_ID, SEAT_NUM, SEAT_TYPE, CAPACITY, STATUS) VALUES (?, 'C-1', '2인석', 2, '빈자리')",
                "SEAT_ID", zoneId);
        seatOff = insert("INSERT INTO SEAT (ZONE_ID, SEAT_NUM, SEAT_TYPE, CAPACITY, STATUS) VALUES (?, 'C-2', '2인석', 2, '사용중지')",
                "SEAT_ID", zoneId);
    }

    @AfterEach
    void cleanUp() {
        List<Long> ids = jdbc.queryForList(
                "SELECT RESERVATION_ID FROM RESERVATION WHERE MEMBER_ID IN (?, ?)", Long.class, USER, OTHER_USER);
        ids.addAll(createdReservations);
        jdbc.update("DELETE FROM RESERVATION WHERE MEMBER_ID IN (?, ?)", USER, OTHER_USER);
        for (Long id : ids) jdbc.update("DELETE FROM RESERVATION_LOG WHERE RESERVATION_ID = ?", id);
        jdbc.update("DELETE FROM SEAT WHERE ZONE_ID IN (SELECT ZONE_ID FROM ZONE WHERE CAFE_ID IN (SELECT CAFE_ID FROM CAFE WHERE OWNER_ID = ?))", OWNER);
        jdbc.update("DELETE FROM ZONE WHERE CAFE_ID IN (SELECT CAFE_ID FROM CAFE WHERE OWNER_ID = ?)", OWNER);
        jdbc.update("DELETE FROM CAFE WHERE OWNER_ID = ?", OWNER);
        jdbc.update("DELETE FROM OWNER WHERE OWNER_ID = ?", OWNER);
        jdbc.update("DELETE FROM MEMBER WHERE MEMBER_ID IN (?, ?, ?)", USER, OTHER_USER, OWNER);
        createdReservations.clear();
    }

    /* ------------------------------ SP_CREATE_RESERVATION ------------------------------ */

    @Test
    @DisplayName("SP_CREATE_RESERVATION: 성공하면 0과 예약번호, 행은 '예약완료'")
    void createReservation_success() {
        LocalDateTime start = tomorrowAt(10, 0);
        CreateReservationResult r = call(() -> reservationCaller.createReservation(USER, seatId, start, start.plusHours(2), 2));

        assertThat(r.resultCode()).isZero();
        assertThat(r.reservationId()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT STATUS FROM RESERVATION WHERE RESERVATION_ID = ?", String.class, r.reservationId()))
                .isEqualTo("예약완료");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM RESERVATION_LOG WHERE RESERVATION_ID = ? AND ACTION = 'INSERT'",
                Integer.class, r.reservationId())).isEqualTo(1);
    }

    @Test
    @DisplayName("SP_CREATE_RESERVATION: 실패하면 예약번호 null 과 오류 코드")
    void createReservation_failures() {
        LocalDateTime start = tomorrowAt(10, 0);
        assertThat(call(() -> reservationCaller.createReservation(USER, seatId, start, start.plusHours(2), 2)).resultCode()).isZero();

        CreateReservationResult conflict = call(() -> reservationCaller.createReservation(OTHER_USER, seatId, start.plusHours(1), start.plusHours(2), 1));
        assertThat(conflict.resultCode()).isEqualTo(-20004);
        assertThat(conflict.reservationId()).isNull();

        assertThat(call(() -> reservationCaller.createReservation(OTHER_USER, seatId, start.plusHours(3), start.plusHours(4), 3)).resultCode())
                .isEqualTo(-20003);   // 정원 초과
        assertThat(call(() -> reservationCaller.createReservation(OTHER_USER, seatOff, start, start.plusHours(1), 1)).resultCode())
                .isEqualTo(-20009);   // 사용중지
        assertThat(call(() -> reservationCaller.createReservation(OTHER_USER, seatId, tomorrowAt(7, 0), tomorrowAt(8, 0), 1)).resultCode())
                .isEqualTo(-20001);   // 영업시간 밖
        LocalDateTime far = tomorrowAt(10, 0).plusDays(10);
        assertThat(call(() -> reservationCaller.createReservation(OTHER_USER, seatId, far, far.plusHours(1), 1)).resultCode())
                .isEqualTo(-20002);   // 7일 밖
    }

    /* ------------------------------ SP_CANCEL_RESERVATION ------------------------------ */

    @Test
    @DisplayName("SP_CANCEL_RESERVATION: 본인 아니면 -20008, 본인이면 0 → '예약취소'")
    void cancelReservation() {
        LocalDateTime start = tomorrowAt(14, 0);
        long id = call(() -> reservationCaller.createReservation(USER, seatId, start, start.plusHours(1), 1)).reservationId();

        assertThat(call(() -> reservationCaller.cancelReservation(id, OTHER_USER))).isEqualTo(-20008);
        assertThat(call(() -> reservationCaller.cancelReservation(id, USER))).isZero();
        assertThat(statusOf(id)).isEqualTo("예약취소");
        assertThat(call(() -> reservationCaller.cancelReservation(id, USER))).isEqualTo(-20007);   // 이미 취소됨
    }

    /* ------------------------------ SP_CHECK_IN / SP_CHECK_OUT ------------------------------ */

    @Test
    @DisplayName("SP_CHECK_IN / SP_CHECK_OUT: 좌석이 이용중 → 빈자리로 바뀐다")
    void checkInAndOut() {
        long id = directReservation(5.0 / 60, 1);   // 5분 뒤 입실 → 체크인 가능 시간

        assertThat(call(() -> reservationCaller.checkOut(id, USER))).isEqualTo(-20007);   // 아직 이용중 아님
        assertThat(call(() -> reservationCaller.checkIn(id, OTHER_USER))).isEqualTo(-20008);
        assertThat(call(() -> reservationCaller.checkIn(id, USER))).isZero();
        assertThat(statusOf(id)).isEqualTo("이용중");
        assertThat(seatStatus()).isEqualTo("이용중");   // TRG_RESV_SEAT_SYNC

        assertThat(call(() -> reservationCaller.checkOut(id, USER))).isZero();
        assertThat(statusOf(id)).isEqualTo("이용완료");
        assertThat(seatStatus()).isEqualTo("빈자리");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM RESERVATION WHERE RESERVATION_ID = ? AND ACTUAL_IN IS NOT NULL AND ACTUAL_OUT IS NOT NULL",
                Integer.class, id)).isEqualTo(1);
    }

    @Test
    @DisplayName("SP_CHECK_IN: 입실 10분 전~15분 후 밖이면 -20006")
    void checkIn_outsideWindow() {
        long id = directReservation(3, 1);   // 3시간 뒤 입실
        assertThat(call(() -> reservationCaller.checkIn(id, USER))).isEqualTo(-20006);
    }

    /* ------------------------------ SP_GET_AVAILABLE_SLOTS ------------------------------ */

    @Test
    @DisplayName("SP_GET_AVAILABLE_SLOTS: 예약된 시간대는 available=false, 사용중지 좌석은 빠진다")
    void availableSlots() {
        LocalDateTime start = tomorrowAt(10, 0);
        assertThat(call(() -> reservationCaller.createReservation(USER, seatId, start, start.plusHours(1), 1)).resultCode()).isZero();

        List<SlotRow> rows = call(() -> slotCaller.getAvailableSlots(cafeId, start.toLocalDate(), 1));

        assertThat(rows).isNotEmpty().allMatch(r -> r.seatId() == seatId);   // seatOff 제외
        assertThat(rows).filteredOn(r -> r.slotStart().equals(start)).singleElement()
                .satisfies(r -> assertThat(r.available()).isFalse());
        assertThat(rows).filteredOn(r -> r.slotStart().equals(tomorrowAt(15, 0))).singleElement()
                .satisfies(r -> assertThat(r.available()).isTrue());
        assertThat(rows.get(0).seatNum()).isEqualTo("C-1");
        assertThat(rows.get(0).zoneName()).isEqualTo("ZZC홀");
    }

    @Test
    @DisplayName("SP_GET_AVAILABLE_SLOTS: 정원보다 인원이 많으면 해당 좌석은 나오지 않는다")
    void availableSlots_capacity() {
        List<SlotRow> rows = call(() -> slotCaller.getAvailableSlots(cafeId, tomorrowAt(0, 0).toLocalDate(), 3));
        assertThat(rows).isEmpty();
    }

    /* ------------------------------ SP_ZONE_STATS ------------------------------ */

    @Test
    @DisplayName("SP_ZONE_STATS: 구역별 한 행, 백분율 값")
    void zoneStats() {
        long id = directReservation(-48, 2);
        withDmlAllowed("UPDATE RESERVATION SET STATUS = '노쇼' WHERE RESERVATION_ID = " + id);
        LocalDate today = jdbc.queryForObject("SELECT TRUNC(SYSDATE) FROM DUAL", java.sql.Date.class).toLocalDate();

        List<ZoneStatRow> rows = call(() -> statsCaller.getZoneStats(cafeId,
                today.minusDays(6).atStartOfDay(), today.plusDays(1).atStartOfDay()));

        assertThat(rows).singleElement().satisfies(z -> {
            assertThat(z.zoneName()).isEqualTo("ZZC홀");
            assertThat(z.resvCount()).isEqualTo(1L);
            assertThat(z.noshowRate()).isEqualTo(100.0);
            assertThat(z.avgStayMin()).isNull();   // 이용완료 없음
        });
    }

    /* ------------------------------ 도우미 ------------------------------ */

    /** 미구현이면 이 테스트를 건너뛴다 */
    private static <T> T call(Callable<T> c) {
        try {
            return c.call();
        } catch (UnsupportedOperationException e) {
            abort("아직 구현되지 않음: " + e.getMessage());
            return null;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private LocalDateTime tomorrowAt(int h, int m) {
        LocalDate today = jdbc.queryForObject("SELECT TRUNC(SYSDATE) FROM DUAL", java.sql.Date.class).toLocalDate();
        return today.plusDays(1).atTime(h, m);
    }

    /** 체크인 시간 조건을 맞추기 위해 프로시저 없이 '예약완료' 행을 COMMIT 으로 넣는다 */
    private long directReservation(double startOffsetHours, double hours) {
        withDmlAllowed("INSERT INTO RESERVATION (MEMBER_ID, SEAT_ID, START_DT, END_DT, VISITOR_COUNT, STATUS)"
                + " VALUES ('" + USER + "', " + seatId + ", SYSDATE + " + startOffsetHours + "/24, SYSDATE + "
                + (startOffsetHours + hours) + "/24, 1, '예약완료')");
        Long id = jdbc.queryForObject("SELECT MAX(RESERVATION_ID) FROM RESERVATION WHERE MEMBER_ID = ?", Long.class, USER);
        createdReservations.add(id);
        return id;
    }

    /**
     * PKG_RESV_DML.G_ALLOWED 는 세션 변수라 TRUE 설정과 DML 이 같은 커넥션에서 실행되어야 한다.
     * 커넥션 풀이 다른 커넥션을 줄 수 있으므로 한 PL/SQL 블록으로 묶어 실행·COMMIT 한다.
     */
    private void withDmlAllowed(String dml) {
        jdbc.execute("BEGIN PKG_RESV_DML.G_ALLOWED := TRUE; " + dml + "; PKG_RESV_DML.G_ALLOWED := FALSE; COMMIT; "
                + "EXCEPTION WHEN OTHERS THEN PKG_RESV_DML.G_ALLOWED := FALSE; RAISE; END;");
    }

    private String statusOf(long id) {
        return jdbc.queryForObject("SELECT STATUS FROM RESERVATION WHERE RESERVATION_ID = ?", String.class, id);
    }

    private String seatStatus() {
        return jdbc.queryForObject("SELECT STATUS FROM SEAT WHERE SEAT_ID = ?", String.class, seatId);
    }

    private long insert(String sql, String keyColumn, Object... args) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, new String[]{keyColumn});
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            return ps;
        }, kh);
        return Objects.requireNonNull(kh.getKey()).longValue();
    }
}
