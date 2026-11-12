package academic.cafecatchtable.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * HTML 화면(Thymeleaf) 라우팅.
 * 화면은 데이터를 직접 받지 않고, 각 템플릿의 스크립트가 /api/** (RestController)를 fetch로 호출한다.
 * 로그인·점주 권한 확인도 화면에서는 /api/auth/me 로 보조 처리하고, 최종 차단은 API가 맡는다.
 */
@Controller
public class PageController {

    @GetMapping("/")
    public String home() {
        return "redirect:/cafes";
    }

    /* 회원·인증 */
    @GetMapping("/login")
    public String login() { return "login"; }                       // FR-02

    @GetMapping("/signup")
    public String signup() { return "signup"; }                     // FR-01

    @GetMapping("/owner/apply")
    public String ownerApply() { return "owner-apply"; }            // FR-03

    /* 카페 조회·예약 */
    @GetMapping("/cafes")
    public String cafes() { return "cafes"; }                       // FR-08

    @GetMapping("/cafes/{cafeId}")
    public String cafeDetail() { return "cafe-detail"; }            // FR-09, FR-10

    @GetMapping("/cafes/{cafeId}/reserve")
    public String reserve() { return "reserve"; }                   // FR-11 ~ FR-14

    @GetMapping("/mypage")
    public String mypage() { return "mypage"; }                     // FR-15 ~ FR-17, FR-19

    /* 점주 */
    @GetMapping("/owner/cafes")
    public String ownerCafes() { return "owner/manage"; }           // FR-04 ~ FR-07

    @GetMapping("/owner/reservations")
    public String ownerReservations() { return "owner/reservations"; } // FR-20

    @GetMapping("/owner/stats")
    public String ownerStats() { return "owner/stats"; }            // FR-21
}
