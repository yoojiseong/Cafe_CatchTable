package academic.cafecatchtable.common;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 요구사항 6장 "시각은 DB 서버의 SYSDATE(Asia/Seoul)를 기준으로 한다".
 * 좌석 현재 상태·남은 시간·예약 가능 날짜 계산에 쓴다.
 */
@Component
public class DbClock {

    private final JdbcTemplate jdbcTemplate;

    public DbClock(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public LocalDateTime now() {
        return jdbcTemplate.queryForObject("SELECT SYSDATE FROM DUAL", LocalDateTime.class);
    }
}
