package academic.cafecatchtable.auth;

import academic.cafecatchtable.common.Status;

import java.io.Serializable;

/**
 * 로그인 시 세션에 저장하는 값 (FR-02: 세션에 ID·ROLE 저장).
 * 컨트롤러 메서드 파라미터에 SessionUser 를 선언하면 LoginUserArgumentResolver 가 넣어 준다.
 */
public record SessionUser(String memberId, String name, String role) implements Serializable {

    public static final String SESSION_KEY = "LOGIN_USER";

    public boolean isOwner() {
        return Status.ROLE_OWNER.equals(role);
    }

    public SessionUser withRole(String newRole) {
        return new SessionUser(memberId, name, newRole);
    }
}
