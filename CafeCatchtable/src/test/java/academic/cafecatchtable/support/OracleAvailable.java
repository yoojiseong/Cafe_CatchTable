package academic.cafecatchtable.support;

import java.sql.Connection;
import java.sql.DriverManager;

/**
 * 통합 테스트 실행 조건: 로컬 Oracle(CAFE_CATCHTABLE)에 접속되는지 확인한다.
 * DB 가 꺼져 있으면 통합 테스트는 실패 대신 "건너뜀"으로 처리된다.
 * 접속 정보는 application.properties 와 같은 환경변수(DB_URL, DB_USERNAME, DB_PASSWORD)를 따른다.
 *
 * 사용: @EnabledIf("academic.cafecatchtable.support.OracleAvailable#isUp")
 */
public final class OracleAvailable {

    private static Boolean up;

    private OracleAvailable() {}

    public static synchronized boolean isUp() {
        if (up == null) up = check();
        return up;
    }

    private static boolean check() {
        String url = env("DB_URL", "jdbc:oracle:thin:@localhost:1521:xe");
        String user = env("DB_USERNAME", "CAFE_CATCHTABLE");
        String password = env("DB_PASSWORD", "1234");
        try {
            Class.forName("oracle.jdbc.OracleDriver");
            DriverManager.setLoginTimeout(3);
            try (Connection ignored = DriverManager.getConnection(url, user, password)) {
                return true;
            }
        } catch (Exception e) {
            System.err.println("[OracleAvailable] DB 에 접속할 수 없어 통합 테스트를 건너뜁니다: " + e.getMessage());
            return false;
        }
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? def : v;
    }
}
