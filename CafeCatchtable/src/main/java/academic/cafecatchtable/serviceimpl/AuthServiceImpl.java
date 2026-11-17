package academic.cafecatchtable.serviceimpl;

import academic.cafecatchtable.service.AuthService;

import academic.cafecatchtable.auth.SessionUser;
import academic.cafecatchtable.common.ApiException;
import academic.cafecatchtable.common.ErrorCode;
import academic.cafecatchtable.domain.Member;
import academic.cafecatchtable.domain.Owner;
import academic.cafecatchtable.dto.AuthDto.*;
import academic.cafecatchtable.repository.MemberRepository;
import academic.cafecatchtable.repository.OwnerRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원가입·로그인·점주 신청 (FR-01 ~ FR-03, A-01 ~ A-06) — JPA */
@Service
public class AuthServiceImpl implements AuthService {

    private final MemberRepository memberRepository;
    private final OwnerRepository ownerRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthServiceImpl(MemberRepository memberRepository, OwnerRepository ownerRepository,
                       PasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.ownerRepository = ownerRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** A-01 회원가입: 비밀번호 BCrypt, ROLE='USER' */
    @Override
    @Transactional
    public MemberResponse signup(SignupRequest req) {
        if (memberRepository.existsById(req.memberId())) {
            throw new ApiException(ErrorCode.DUPLICATE_ID);
        }
        Member member = Member.newUser(req.memberId(), passwordEncoder.encode(req.password()),
                req.name().trim(), req.phone());
        try {
            memberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException e) {   // 동시에 같은 아이디로 가입한 경우 (PK 위반)
            throw new ApiException(ErrorCode.DUPLICATE_ID);
        }
        return new MemberResponse(member.getMemberId(), member.getName(), null, member.getRole());
    }

    /** A-02 아이디 사용 가능 여부 */
    @Override
    @Transactional(readOnly = true)
    public boolean isAvailable(String memberId) {
        return memberId != null && !memberId.isBlank() && !memberRepository.existsById(memberId);
    }

    /** A-03 로그인: 아이디·비밀번호 중 무엇이 틀렸는지 구분하지 않는다 */
    @Override
    @Transactional(readOnly = true)
    public SessionUser login(LoginRequest req) {
        Member m = memberRepository.findById(req.memberId()).orElse(null);
        if (m == null || !passwordEncoder.matches(req.password(), m.getPassword())) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다");
        }
        return new SessionUser(m.getMemberId(), m.getName(), m.getRole());
    }

    /** A-05 내 정보 */
    @Override
    @Transactional(readOnly = true)
    public MemberResponse me(String memberId) {
        Member m = memberRepository.findById(memberId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
        return new MemberResponse(m.getMemberId(), m.getName(), m.getPhone(), m.getRole());
    }

    /**
     * A-06 점주 신청 (FR-03)
     * OWNER 에 INSERT 만 한다. MEMBER.ROLE 은 트리거 TRG_OWNER_ROLE(DB-T5, AFTER INSERT ON OWNER)이
     * 같은 트랜잭션 안에서 'OWNER' 로 바꾸므로, flush 후 DB 에서 ROLE 을 다시 읽어 응답·세션에 쓴다.
     */
    @Override
    @Transactional
    public OwnerApplyResponse applyOwner(String memberId, OwnerApplyRequest req) {
        if (ownerRepository.existsById(memberId)) {
            throw new ApiException(ErrorCode.ALREADY_OWNER);
        }
        if (ownerRepository.existsByBusinessNum(req.businessNum())) {
            throw new ApiException(ErrorCode.DUPLICATE_BUSINESS_NUM);
        }
        try {
            ownerRepository.saveAndFlush(Owner.of(memberId, req.businessNum()));   // → TRG_OWNER_ROLE 실행
        } catch (DataIntegrityViolationException e) {   // BUSINESS_NUM UNIQUE 위반
            throw new ApiException(ErrorCode.DUPLICATE_BUSINESS_NUM);
        }
        String role = memberRepository.findRoleById(memberId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
        return new OwnerApplyResponse(memberId, req.businessNum(), role);
    }
}
