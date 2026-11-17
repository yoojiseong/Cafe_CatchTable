package academic.cafecatchtable.service;

import academic.cafecatchtable.dto.OwnerDto.OwnerReservationResponse;
import academic.cafecatchtable.dto.OwnerDto.StatsResponse;

import java.time.LocalDate;
import java.util.List;

/** 점주 예약 현황·통계 (FR-20, FR-21 / O-10 ~ O-12). 구현: serviceimpl.OwnerReservationServiceImpl */
public interface OwnerReservationService {

    /** O-10 선택한 날짜의 예약 타임라인 (date 가 null 이면 오늘) */
    List<OwnerReservationResponse> daily(String ownerId, Long cafeId, LocalDate date);

    /** O-11 예약자 상세. 다른 점주 카페의 예약이면 403 */
    OwnerReservationResponse detail(String ownerId, Long reservationId);

    /** O-12 구역 통계(SP_ZONE_STATS) + 인기 좌석 TOP 3 */
    StatsResponse stats(String ownerId, Long cafeId, LocalDate from, LocalDate to);
}
