package academic.cafecatchtable.api;

import academic.cafecatchtable.dto.CafeDto.CafeResponse;
import academic.cafecatchtable.dto.CafeDto.SeatResponse;
import academic.cafecatchtable.dto.CafeDto.SlotsResponse;
import academic.cafecatchtable.service.CafeQueryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** 카페·좌석 조회 API (C-01 ~ C-04) */
@RestController
@RequestMapping("/api/cafes")
public class CafeApiController {

    private final CafeQueryService cafeQueryService;

    public CafeApiController(CafeQueryService cafeQueryService) {
        this.cafeQueryService = cafeQueryService;
    }

    /** C-01 카페 검색 */
    @GetMapping
    public List<CafeResponse> search(@RequestParam(required = false) String keyword) {
        return cafeQueryService.search(keyword);
    }

    /** C-02 카페 상세(구역 포함) */
    @GetMapping("/{cafeId}")
    public CafeResponse detail(@PathVariable Long cafeId) {
        return cafeQueryService.detail(cafeId);
    }

    /** C-03 좌석 배치·조건 검색·현재 상태 */
    @GetMapping("/{cafeId}/seats")
    public List<SeatResponse> seats(@PathVariable Long cafeId,
                                    @RequestParam(required = false) Long zoneId,
                                    @RequestParam(required = false) String seatType,
                                    @RequestParam(required = false) String extraItem,
                                    @RequestParam(required = false) Integer visitors) {
        return cafeQueryService.seats(cafeId, zoneId, seatType, extraItem, visitors);
    }

    /** C-04 예약 가능 시간대 → SP_GET_AVAILABLE_SLOTS */
    @GetMapping("/{cafeId}/slots")
    public SlotsResponse slots(@PathVariable Long cafeId,
                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                               @RequestParam(defaultValue = "1") int visitors) {
        return cafeQueryService.slots(cafeId, date, visitors);
    }
}
