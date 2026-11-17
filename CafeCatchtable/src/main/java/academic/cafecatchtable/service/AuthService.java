package academic.cafecatchtable.service;

import academic.cafecatchtable.auth.SessionUser;
import academic.cafecatchtable.dto.AuthDto.LoginRequest;
import academic.cafecatchtable.dto.AuthDto.MemberResponse;
import academic.cafecatchtable.dto.AuthDto.OwnerApplyRequest;
import academic.cafecatchtable.dto.AuthDto.OwnerApplyResponse;
import academic.cafecatchtable.dto.AuthDto.SignupRequest;

/** 회원·인증 (FR-01 ~ FR-03 / A-01 ~ A-06). 구현: serviceimpl.AuthServiceImpl */
public interface AuthService {

    /** A-01 회원가입: 비밀번호 BCrypt, ROLE='USER'. 아이디 중복이면 409 DUPLICATE_ID */
    MemberResponse signup(SignupRequest req);

    /** A-02 아이디 사용 가능 여부 */
    boolean isAvailable(String memberId);

    /** A-03 로그인: 아이디·비밀번호 중 무엇이 틀렸는지 구분하지 않고 401 */
    SessionUser login(LoginRequest req);

    /** A-05 내 정보 */
    MemberResponse me(String memberId);

    /** A-06 점주 신청: OWNER INSERT → 트리거 TRG_OWNER_ROLE 이 ROLE 을 'OWNER' 로 변경 */
    OwnerApplyResponse applyOwner(String memberId, OwnerApplyRequest req);
}
