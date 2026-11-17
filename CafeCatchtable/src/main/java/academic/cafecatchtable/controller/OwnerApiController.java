package academic.cafecatchtable.controller;

import academic.cafecatchtable.auth.SessionUser;
import academic.cafecatchtable.dto.CafeDto.CafeResponse;
import academic.cafecatchtable.dto.CafeDto.SeatResponse;
import academic.cafecatchtable.dto.CafeDto.ZoneResponse;
import academic.cafecatchtable.dto.OwnerDto.*;
import academic.cafecatchtable.service.OwnerReservationService;
import academic.cafecatchtable.service.OwnerStoreService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 점주 API (O-01 ~ O-12). /api/owner/** 는 LoginCheckInterceptor 가 OWNER 만 통과시키고,
 * 본인 카페인지는 서비스가 검사한다 (아니면 403).
 */
@RestController
@RequestMapping("/api/owner")
public class OwnerApiController {

    private final OwnerStoreService storeService;
    private final OwnerReservationService reservationService;

    public OwnerApiController(OwnerStoreService storeService, OwnerReservationService reservationService) {
        this.storeService = storeService;
        this.reservationService = reservationService;
    }

    /* ---------- 카페 ---------- */

    /** O-01 내 카페 목록 */
    @GetMapping("/cafes")
    public List<CafeResponse> myCafes(SessionUser user) {
        return storeService.myCafes(user.memberId());
    }

    /** O-02 카페 등록 */
    @PostMapping("/cafes")
    @ResponseStatus(HttpStatus.CREATED)
    public CafeResponse createCafe(SessionUser user, @Valid @RequestBody CafeRequest req) {
        return storeService.createCafe(user.memberId(), req);
    }

    /** O-03 카페 수정 */
    @PutMapping("/cafes/{cafeId}")
    public CafeResponse updateCafe(SessionUser user, @PathVariable Long cafeId, @Valid @RequestBody CafeRequest req) {
        return storeService.updateCafe(user.memberId(), cafeId, req);
    }

    /* ---------- 구역 ---------- */

    /** O-04 구역 추가 */
    @PostMapping("/cafes/{cafeId}/zones")
    @ResponseStatus(HttpStatus.CREATED)
    public ZoneResponse addZone(SessionUser user, @PathVariable Long cafeId, @Valid @RequestBody ZoneRequest req) {
        return storeService.addZone(user.memberId(), cafeId, req);
    }

    /** O-05 구역 이름 변경 */
    @PutMapping("/zones/{zoneId}")
    public ZoneResponse renameZone(SessionUser user, @PathVariable Long zoneId, @Valid @RequestBody ZoneRequest req) {
        return storeService.renameZone(user.memberId(), zoneId, req);
    }

    /** O-06 구역 삭제 (좌석 포함, TRG_SEAT_DELETE_GUARD 적용) */
    @DeleteMapping("/zones/{zoneId}")
    public ResponseEntity<Void> deleteZone(SessionUser user, @PathVariable Long zoneId) {
        storeService.deleteZone(user.memberId(), zoneId);
        return ResponseEntity.noContent().build();
    }

    /* ---------- 좌석 ---------- */

    /** O-07 좌석 등록 */
    @PostMapping("/zones/{zoneId}/seats")
    @ResponseStatus(HttpStatus.CREATED)
    public SeatResponse addSeat(SessionUser user, @PathVariable Long zoneId, @Valid @RequestBody SeatRequest req) {
        return storeService.addSeat(user.memberId(), zoneId, req);
    }

    /** O-08 좌석 수정·사용중지 */
    @PutMapping("/seats/{seatId}")
    public SeatResponse updateSeat(SessionUser user, @PathVariable Long seatId, @Valid @RequestBody SeatRequest req) {
        return storeService.updateSeat(user.memberId(), seatId, req);
    }

    /** O-09 좌석 삭제 (TRG_SEAT_DELETE_GUARD 적용) */
    @DeleteMapping("/seats/{seatId}")
    public ResponseEntity<Void> deleteSeat(SessionUser user, @PathVariable Long seatId) {
        storeService.deleteSeat(user.memberId(), seatId);
        return ResponseEntity.noContent().build();
    }

    /* ---------- 예약 현황·통계 ---------- */

    /** O-10 일일 예약 타임라인 */
    @GetMapping("/cafes/{cafeId}/reservations")
    public List<OwnerReservationResponse> dailyReservations(
            SessionUser user, @PathVariable Long cafeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return reservationService.daily(user.memberId(), cafeId, date);
    }

    /** O-11 예약자 상세 */
    @GetMapping("/reservations/{id}")
    public OwnerReservationResponse reservationDetail(SessionUser user, @PathVariable Long id) {
        return reservationService.detail(user.memberId(), id);
    }

    /** O-12 구역 통계·인기 좌석 → SP_ZONE_STATS */
    @GetMapping("/cafes/{cafeId}/stats")
    public StatsResponse stats(
            SessionUser user, @PathVariable Long cafeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reservationService.stats(user.memberId(), cafeId, from, to);
    }
}
