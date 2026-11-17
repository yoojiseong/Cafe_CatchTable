package academic.cafecatchtable;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;

/** 스프링 컨텍스트 + Oracle 연결 + 엔티티 매핑 확인. DB 가 꺼져 있으면 건너뛴다. */
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@EnabledIf("academic.cafecatchtable.support.OracleAvailable#isUp")
class CafeCatchtableApplicationTests {

    @Test
    void contextLoads() {
    }

}
