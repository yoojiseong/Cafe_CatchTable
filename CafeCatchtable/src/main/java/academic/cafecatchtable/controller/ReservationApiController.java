package academic.cafecatchtable.controller;

import academic.cafecatchtable.auth.SessionUser;
import academic.cafecatchtable.dto.ReservationDto.CreateRequest;
import academic.cafecatchtable.dto.ReservationDto.ReservationResponse;
import academic.cafecatchtable.dto.ReservationDto.StatusResponse;
import academic.cafecatchtable.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/**
 * 예약·이용 API (R-01 ~ R-06)
 * 회원 ID 는 세션(SessionUser)에서 꺼내 프로시저 p_member_id 로 넘긴다.
 */
@RestController
@RequestMapping("/api/reservations")
public class ReservationApiController {

    private final ReservationService reservationService;

    public ReservationApiController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    /** R-01 예약 생성 → SP_CREATE_RESERVATION. 201 + Location */
    @PostMapping
    public ResponseEntity<ReservationResponse> create(SessionUser user, @Valid @RequestBody CreateRequest req) {
        ReservationResponse res = reservationService.create(user.memberId(), req);
        return ResponseEntity.created(URI.create("/api/reservations/" + res.reservationId())).body(res);
    }

    /** R-02 내 예약 목록 (type = active | past) */
    @GetMapping("/me")
    public List<ReservationResponse> myReservations(SessionUser user,
                                                    @RequestParam(required = false) String type) {
        return reservationService.myReservations(user.memberId(), type);
    }

    /** R-03 예약 상세 */
    @GetMapping("/{id}")
    public ReservationResponse detail(SessionUser user, @PathVariable Long id) {
        return reservationService.detail(user.memberId(), id);
    }

    /** R-04 예약 취소 → SP_CANCEL_RESERVATION */
    @PostMapping("/{id}/cancel")
    public StatusResponse cancel(SessionUser user, @PathVariable Long id) {
        return reservationService.cancel(user.memberId(), id);
    }

    /** R-05 체크인 → SP_CHECK_IN */
    @PostMapping("/{id}/check-in")
    public StatusResponse checkIn(SessionUser user, @PathVariable Long id) {
        return reservationService.checkIn(user.memberId(), id);
    }

    /** R-06 퇴실 → SP_CHECK_OUT */
    @PostMapping("/{id}/check-out")
    public StatusResponse checkOut(SessionUser user, @PathVariable Long id) {
        return reservationService.checkOut(user.memberId(), id);
    }
}
