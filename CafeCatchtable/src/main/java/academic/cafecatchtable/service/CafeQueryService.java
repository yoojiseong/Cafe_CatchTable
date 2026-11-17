package academic.cafecatchtable.service;

import academic.cafecatchtable.dto.CafeDto.CafeResponse;
import academic.cafecatchtable.dto.CafeDto.SeatResponse;
import academic.cafecatchtable.dto.CafeDto.SlotsResponse;

import java.time.LocalDate;
import java.util.List;

/** 카페·좌석 조회 (FR-08 ~ FR-11 / C-01 ~ C-04). 구현: serviceimpl.CafeQueryServiceImpl */
public interface CafeQueryService {

    /** C-01 카페 검색: 이름·주소 포함, 이름순. 키워드가 비면 전체 */
    List<CafeResponse> search(String keyword);

    /** C-02 카페 상세 + 구역 목록 */
    CafeResponse detail(Long cafeId);

    /** C-03 좌석 조회 + 현재 상태(빈자리·예약됨·이용중·사용중지) */
    List<SeatResponse> seats(Long cafeId, Long zoneId, String seatType, String extraItem, Integer visitors);

    /** C-04 예약 가능 시간대 → SP_GET_AVAILABLE_SLOTS */
    SlotsResponse slots(Long cafeId, LocalDate date, int visitors);
}
