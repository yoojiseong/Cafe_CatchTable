package academic.cafecatchtable.api;

import academic.cafecatchtable.auth.SessionUser;
import academic.cafecatchtable.dto.AuthDto.*;
import academic.cafecatchtable.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 회원·인증 API (A-01 ~ A-06) */
@RestController
@RequestMapping("/api")
public class AuthApiController {

    private final AuthService authService;

    public AuthApiController(AuthService authService) {
        this.authService = authService;
    }

    /** A-01 회원가입 */
    @PostMapping("/auth/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public MemberResponse signup(@Valid @RequestBody SignupRequest req) {
        return authService.signup(req);
    }

    /** A-02 아이디 중복 확인 */
    @GetMapping("/auth/check-id")
    public CheckIdResponse checkId(@RequestParam String memberId) {
        return new CheckIdResponse(memberId, authService.isAvailable(memberId));
    }

    /** A-03 로그인: 세션에 ID·ROLE 저장 (세션 고정 공격 방지를 위해 새 세션 발급) */
    @PostMapping("/auth/login")
    public MemberResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest request) {
        SessionUser user = authService.login(req);
        HttpSession old = request.getSession(false);
        if (old != null) old.invalidate();
        request.getSession(true).setAttribute(SessionUser.SESSION_KEY, user);
        return new MemberResponse(user.memberId(), user.name(), null, user.role());
    }

    /** A-04 로그아웃 */
    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        return ResponseEntity.noContent().build();
    }

    /** A-05 내 정보·권한 */
    @GetMapping("/auth/me")
    public MemberResponse me(SessionUser user) {
        return authService.me(user.memberId());
    }

    /** A-06 점주 신청: OWNER INSERT → TRG_OWNER_ROLE 이 ROLE 변경 → 세션 role 도 갱신 */
    @PostMapping("/owners")
    @ResponseStatus(HttpStatus.CREATED)
    public OwnerApplyResponse applyOwner(SessionUser user, @Valid @RequestBody OwnerApplyRequest req,
                                         HttpServletRequest request) {
        OwnerApplyResponse res = authService.applyOwner(user.memberId(), req);
        request.getSession().setAttribute(SessionUser.SESSION_KEY, user.withRole(res.role()));
        return res;
    }
}
