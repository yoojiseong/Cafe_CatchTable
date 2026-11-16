package academic.cafecatchtable.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 회원·인증 API (A-01 ~ A-06) 요청·응답 */
public final class AuthDto {

    private AuthDto() {}

    /** A-01 회원가입 */
    public record SignupRequest(
            @NotBlank(message = "아이디를 입력해 주세요")
            @Pattern(regexp = "^[A-Za-z0-9]{4,20}$", message = "아이디는 영문·숫자 4~20자입니다")
            String memberId,

            @NotBlank(message = "비밀번호를 입력해 주세요")
            @Size(min = 8, max = 72, message = "비밀번호는 8자 이상입니다")
            String password,

            @NotBlank(message = "이름을 입력해 주세요")
            @Size(max = 50, message = "이름은 50자 이하입니다")
            String name,

            @NotBlank(message = "전화번호를 입력해 주세요")
            @Pattern(regexp = "^010-\\d{4}-\\d{4}$", message = "전화번호 형식은 010-0000-0000 입니다")
            String phone
    ) {}

    /** A-03 로그인 */
    public record LoginRequest(
            @NotBlank String memberId,
            @NotBlank String password
    ) {}

    /** A-01 / A-03 / A-05 응답 (phone 은 A-05 에서만) */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MemberResponse(String memberId, String name, String phone, String role) {}

    /** A-02 아이디 중복 확인 */
    public record CheckIdResponse(String memberId, boolean available) {}

    /** A-06 점주 신청 */
    public record OwnerApplyRequest(
            @NotBlank(message = "사업자등록번호를 입력해 주세요")
            @Pattern(regexp = "^\\d{3}-\\d{2}-\\d{5}$", message = "사업자등록번호 형식은 000-00-00000 입니다")
            String businessNum
    ) {}

    public record OwnerApplyResponse(String memberId, String businessNum, String role) {}
}
