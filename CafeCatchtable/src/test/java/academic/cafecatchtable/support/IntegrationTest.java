package academic.cafecatchtable.support;

import academic.cafecatchtable.auth.SessionUser;
import academic.cafecatchtable.jdbc.ReservationProcedureCaller;
import academic.cafecatchtable.jdbc.SlotProcedureCaller;
import academic.cafecatchtable.jdbc.StatsProcedureCaller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * API 통합 테스트 공통 설정.
 *
 * - 실제 Oracle(CAFE_CATCHTABLE)에 연결한다. 트리거·제약조건까지 진짜로 동작한다.
 * - @Transactional: 테스트 메서드가 끝나면 전부 롤백된다. 더미 데이터는 바뀌지 않는다.
 *   (MockMvc 요청은 테스트와 같은 스레드에서 실행되므로 서비스의 트랜잭션도 테스트 트랜잭션에 합류한다.
 *    IDENTITY 번호만 소모된다.)
 * - 테스트용 회원·카페·좌석·예약은 {@link Fixtures} 가 매번 새로 만든다. 더미 데이터 내용에 의존하지 않는다.
 * - 저장프로시저 호출 클래스 3개는 아직 미구현이라 Mockito 로 대체한다.
 *   서비스가 프로시저에 무엇을 넘기고, 돌아온 결과 코드를 어떤 HTTP 응답으로 바꾸는지를 검증한다.
 *   (프로시저 자체 검증은 ProcedureCallerContractTest 참고)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIf("academic.cafecatchtable.support.OracleAvailable#isUp")
public abstract class IntegrationTest {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @MockitoBean
    protected ReservationProcedureCaller reservationProcedureCaller;

    @MockitoBean
    protected SlotProcedureCaller slotProcedureCaller;

    @MockitoBean
    protected StatsProcedureCaller statsProcedureCaller;

    protected Fixtures fx;

    @BeforeEach
    void setUpFixtures() {
        fx = new Fixtures(jdbc);
    }

    /** 로그인 API 를 거치지 않고 세션만 만든다 (로그인 자체는 AuthApiTest 에서 검증) */
    protected MockHttpSession sessionOf(String memberId, String name, String role) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionUser.SESSION_KEY, new SessionUser(memberId, name, role));
        return session;
    }

    protected MockHttpSession userSession() {
        return sessionOf(Fixtures.USER, "테스트회원", "USER");
    }

    protected MockHttpSession ownerSession() {
        return sessionOf(Fixtures.OWNER, "테스트점주", "OWNER");
    }

    protected MockHttpSession otherOwnerSession() {
        return sessionOf(Fixtures.OTHER_OWNER, "다른점주", "OWNER");
    }
}
