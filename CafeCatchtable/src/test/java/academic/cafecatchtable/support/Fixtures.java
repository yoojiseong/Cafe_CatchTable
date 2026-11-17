package academic.cafecatchtable.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.util.Objects;

/**
 * 통합 테스트용 데이터. 테스트 트랜잭션 안에서 INSERT 하므로 끝나면 롤백된다.
 *
 * 회원 3명(일반 1, 점주 2)은 생성자에서 만든다. 비밀번호는 모두 test1234.
 * 카페·구역·좌석·예약은 필요한 테스트에서 메서드로 만든다.
 */
public class Fixtures {

    public static final String USER = "zztuser01";
    public static final String OTHER_USER = "zztuser02";
    public static final String OWNER = "zztowner01";
    public static final String OTHER_OWNER = "zztowner02";
    public static final String PASSWORD = "test1234";
    /** BCrypt("test1234") — Document/DB_SETUP_GUIDE.md 샘플 계정과 같은 값 */
    public static final String PASSWORD_HASH = "$2a$10$OjewBF9Fq5BCqjUsIVwYvO4qTp2ZN8PBDaiaen3EzVTBW/Xmau59K";

    private final JdbcTemplate jdbc;

    public Fixtures(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        member(USER, "테스트회원", "010-1000-0001");
        member(OTHER_USER, "다른회원", "010-1000-0002");
        member(OWNER, "테스트점주", "010-2000-0001");
        member(OTHER_OWNER, "다른점주", "010-2000-0002");
        // OWNER INSERT → TRG_OWNER_ROLE 이 MEMBER.ROLE 을 'OWNER' 로 바꾼다
        jdbc.update("INSERT INTO OWNER (OWNER_ID, BUSINESS_NUM) VALUES (?, ?)", OWNER, "901-01-00001");
        jdbc.update("INSERT INTO OWNER (OWNER_ID, BUSINESS_NUM) VALUES (?, ?)", OTHER_OWNER, "901-01-00002");
    }

    public void member(String id, String name, String phone) {
        jdbc.update("INSERT INTO MEMBER (MEMBER_ID, PASSWORD, NAME, PHONE, ROLE) VALUES (?, ?, ?, ?, 'USER')",
                id, PASSWORD_HASH, name, phone);
    }

    /** 매일 09:00~22:00 영업 */
    public long cafe(String ownerId, String name) {
        return cafe(ownerId, name, "Mon,Tue,Wed,Thu,Fri,Sat,Sun");
    }

    public long cafe(String ownerId, String name, String operatingDays) {
        return insert("INSERT INTO CAFE (OWNER_ID, CAFE_NAME, ADDRESS, PHONE, START_TIME, END_TIME, OPERATING_DAYS)"
                        + " VALUES (?, ?, '서울 테스트구 테스트로 1', '02-000-0000', '09:00', '22:00', ?)",
                "CAFE_ID", ownerId, name, operatingDays);
    }

    public long zone(long cafeId, String name) {
        return insert("INSERT INTO ZONE (CAFE_ID, ZONE_NAME) VALUES (?, ?)", "ZONE_ID", cafeId, name);
    }

    public long seat(long zoneId, String seatNum, String seatType, int capacity, String extraItem) {
        return seat(zoneId, seatNum, seatType, capacity, extraItem, "빈자리");
    }

    public long seat(long zoneId, String seatNum, String seatType, int capacity, String extraItem, String status) {
        return insert("INSERT INTO SEAT (ZONE_ID, SEAT_NUM, SEAT_TYPE, CAPACITY, EXTRA_ITEM, STATUS)"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                "SEAT_ID", zoneId, seatNum, seatType, capacity, extraItem, status);
    }

    /**
     * 예약 행을 직접 넣는다 (프로시저 결과를 흉내 낼 때 사용).
     * RESERVATION 은 TRG_RESV_VALIDATE 가 PKG_RESV_DML.G_ALLOWED 로 직접 DML 을 막으므로,
     * 같은 세션(=테스트 트랜잭션의 커넥션)에서 잠깐 TRUE 로 바꾼다. 정원·사용중지 검사는 그대로 적용된다.
     *
     * @param startOffsetHours SYSDATE 기준 시작 시각(시간 단위, 음수면 과거)
     * @param hours            이용 시간
     */
    public long reservation(String memberId, long seatId, double startOffsetHours, double hours,
                            int visitors, String status) {
        jdbc.execute("BEGIN PKG_RESV_DML.G_ALLOWED := TRUE; END;");
        try {
            long id = insert("INSERT INTO RESERVATION (MEMBER_ID, SEAT_ID, START_DT, END_DT, VISITOR_COUNT, STATUS)"
                            + " VALUES (?, ?, TRUNC(SYSDATE + ?/24, 'MI'), TRUNC(SYSDATE + ?/24, 'MI'), ?, ?)",
                    "RESERVATION_ID", memberId, seatId, startOffsetHours, startOffsetHours + hours, visitors, status);
            // 이용중·이용완료는 실제 입실·퇴실 시각도 채운다 (STATUS 는 그대로라 TRG_RESV_SEAT_SYNC 는 실행되지 않음)
            if ("이용중".equals(status)) {
                jdbc.update("UPDATE RESERVATION SET ACTUAL_IN = START_DT WHERE RESERVATION_ID = ?", id);
            } else if ("이용완료".equals(status)) {
                jdbc.update("UPDATE RESERVATION SET ACTUAL_IN = START_DT, ACTUAL_OUT = END_DT WHERE RESERVATION_ID = ?", id);
            }
            return id;
        } finally {
            jdbc.execute("BEGIN PKG_RESV_DML.G_ALLOWED := FALSE; END;");
        }
    }

    /** 특정 날짜·시각에 예약 행을 넣는다 (O-10 일일 조회, 인기 좌석 등) */
    public long reservationAt(String memberId, long seatId, String startDt, String endDt, int visitors, String status) {
        jdbc.execute("BEGIN PKG_RESV_DML.G_ALLOWED := TRUE; END;");
        try {
            return insert("INSERT INTO RESERVATION (MEMBER_ID, SEAT_ID, START_DT, END_DT, VISITOR_COUNT, STATUS)"
                            + " VALUES (?, ?, TO_DATE(?, 'YYYY-MM-DD HH24:MI'), TO_DATE(?, 'YYYY-MM-DD HH24:MI'), ?, ?)",
                    "RESERVATION_ID", memberId, seatId, startDt, endDt, visitors, status);
        } finally {
            jdbc.execute("BEGIN PKG_RESV_DML.G_ALLOWED := FALSE; END;");
        }
    }

    public String seatStatus(long seatId) {
        return jdbc.queryForObject("SELECT STATUS FROM SEAT WHERE SEAT_ID = ?", String.class, seatId);
    }

    public int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
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
