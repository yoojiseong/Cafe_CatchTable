package academic.cafecatchtable.controller;

import academic.cafecatchtable.auth.SessionUser;
import academic.cafecatchtable.support.Fixtures;
import academic.cafecatchtable.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 회원·인증 API (A-01 ~ A-06) */
class AuthApiTest extends IntegrationTest {

    @Nested
    @DisplayName("A-01 회원가입")
    class Signup {

        @Test
        void 가입하면_USER_권한으로_BCrypt_비밀번호가_저장된다() throws Exception {
            mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                            {"memberId":"zznew01","password":"password123","name":"새회원","phone":"010-3333-4444"}
                            """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.memberId").value("zznew01"))
                    .andExpect(jsonPath("$.name").value("새회원"))
                    .andExpect(jsonPath("$.role").value("USER"))
                    .andExpect(jsonPath("$.phone").doesNotExist());

            String hash = jdbc.queryForObject("SELECT PASSWORD FROM MEMBER WHERE MEMBER_ID = 'zznew01'", String.class);
            assertThat(hash).startsWith("$2a$").isNotEqualTo("password123");
        }

        @Test
        void 이미_있는_아이디면_409_DUPLICATE_ID() throws Exception {
            mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                            {"memberId":"%s","password":"password123","name":"중복","phone":"010-3333-4444"}
                            """.formatted(Fixtures.USER)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("DUPLICATE_ID"));
        }

        @Test
        void 형식이_틀리면_400과_필드_메시지() throws Exception {
            mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                            {"memberId":"zznew02","password":"password123","name":"새회원","phone":"01033334444"}
                            """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.message").value("전화번호 형식은 010-0000-0000 입니다"));

            mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                            {"memberId":"zznew02","password":"short","name":"새회원","phone":"010-3333-4444"}
                            """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("비밀번호는 8자 이상입니다"));
        }
    }

    @Test
    @DisplayName("A-02 아이디 중복 확인")
    void 아이디_중복_확인() throws Exception {
        mvc.perform(get("/api/auth/check-id").param("memberId", Fixtures.USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));
        mvc.perform(get("/api/auth/check-id").param("memberId", "zznobody"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
    }

    @Nested
    @DisplayName("A-03 로그인 / A-04 로그아웃 / A-05 내 정보")
    class Login {

        @Test
        void 로그인하면_세션에_ID와_ROLE이_저장되고_내정보를_볼_수_있다() throws Exception {
            MvcResult result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                            {"memberId":"%s","password":"%s"}
                            """.formatted(Fixtures.USER, Fixtures.PASSWORD)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.memberId").value(Fixtures.USER))
                    .andExpect(jsonPath("$.role").value("USER"))
                    .andReturn();

            MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
            assertThat(session).isNotNull();
            SessionUser user = (SessionUser) session.getAttribute(SessionUser.SESSION_KEY);
            assertThat(user.memberId()).isEqualTo(Fixtures.USER);

            mvc.perform(get("/api/auth/me").session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("테스트회원"))
                    .andExpect(jsonPath("$.phone").value("010-1000-0001"));

            mvc.perform(post("/api/auth/logout").session(session))
                    .andExpect(status().isNoContent());
            assertThat(session.isInvalid()).isTrue();
        }

        @Test
        void 점주는_ROLE_OWNER로_로그인된다() throws Exception {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                            {"memberId":"%s","password":"%s"}
                            """.formatted(Fixtures.OWNER, Fixtures.PASSWORD)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("OWNER"));
        }

        @Test
        void 비밀번호가_틀리거나_없는_아이디면_같은_401() throws Exception {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                            {"memberId":"%s","password":"wrongpass"}
                            """.formatted(Fixtures.USER)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("아이디 또는 비밀번호가 올바르지 않습니다"));
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                            {"memberId":"zznobody","password":"wrongpass"}
                            """))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("아이디 또는 비밀번호가 올바르지 않습니다"));
        }

        @Test
        void 로그인하지_않으면_401() throws Exception {
            mvc.perform(get("/api/auth/me"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
            mvc.perform(get("/api/reservations/me"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("A-06 점주 신청")
    class ApplyOwner {

        @Test
        void 신청하면_트리거가_ROLE을_OWNER로_바꾸고_세션도_갱신된다() throws Exception {
            MockHttpSession session = userSession();

            mvc.perform(post("/api/owners").session(session).contentType(MediaType.APPLICATION_JSON).content("""
                            {"businessNum":"902-02-00001"}
                            """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.memberId").value(Fixtures.USER))
                    .andExpect(jsonPath("$.role").value("OWNER"));

            // TRG_OWNER_ROLE 결과가 DB 에 반영됨
            assertThat(jdbc.queryForObject("SELECT ROLE FROM MEMBER WHERE MEMBER_ID = ?", String.class, Fixtures.USER))
                    .isEqualTo("OWNER");
            // 세션 role 도 바뀌어 바로 점주 API 를 쓸 수 있음
            assertThat(((SessionUser) session.getAttribute(SessionUser.SESSION_KEY)).role()).isEqualTo("OWNER");
            mvc.perform(get("/api/owner/cafes").session(session)).andExpect(status().isOk());
        }

        @Test
        void 이미_점주면_409_ALREADY_OWNER() throws Exception {
            mvc.perform(post("/api/owners").session(ownerSession()).contentType(MediaType.APPLICATION_JSON).content("""
                            {"businessNum":"902-02-00002"}
                            """))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ALREADY_OWNER"));
        }

        @Test
        void 사업자등록번호가_중복이면_409_DUPLICATE_BUSINESS_NUM() throws Exception {
            mvc.perform(post("/api/owners").session(userSession()).contentType(MediaType.APPLICATION_JSON).content("""
                            {"businessNum":"901-01-00001"}
                            """))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("DUPLICATE_BUSINESS_NUM"));
        }

        @Test
        void 사업자등록번호_형식이_틀리면_400() throws Exception {
            mvc.perform(post("/api/owners").session(userSession()).contentType(MediaType.APPLICATION_JSON).content("""
                            {"businessNum":"12345"}
                            """))
                    .andExpect(status().isBadRequest());
        }
    }
}
