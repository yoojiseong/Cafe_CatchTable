package academic.cafecatchtable.auth;

import academic.cafecatchtable.common.ApiException;
import academic.cafecatchtable.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** 컨트롤러 파라미터 SessionUser 에 세션의 로그인 정보를 넣는다. */
public class LoginUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return SessionUser.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        HttpSession session = request == null ? null : request.getSession(false);
        Object user = session == null ? null : session.getAttribute(SessionUser.SESSION_KEY);
        if (user instanceof SessionUser su) return su;
        throw new ApiException(ErrorCode.UNAUTHORIZED);
    }
}
