package academic.cafecatchtable.auth;

import academic.cafecatchtable.common.ApiException;
import academic.cafecatchtable.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * /api/** 인증·권한 검사 (API 명세서 1장 "인증", "권한").
 * - 로그인 안 함 → 401 UNAUTHORIZED
 * - /api/owner/** 를 OWNER 가 아닌 회원이 호출 → 403 FORBIDDEN
 * 던진 ApiException 은 GlobalExceptionHandler 가 JSON 으로 바꾼다.
 */
public class LoginCheckInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        HttpSession session = request.getSession(false);
        Object user = session == null ? null : session.getAttribute(SessionUser.SESSION_KEY);
        if (!(user instanceof SessionUser loginUser)) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.startsWith("/api/owner/") && !loginUser.isOwner()) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        return true;
    }
}
