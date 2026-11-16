package academic.cafecatchtable.service;

import academic.cafecatchtable.common.ApiException;
import academic.cafecatchtable.common.DbClock;
import academic.cafecatchtable.common.ErrorCode;
import academic.cafecatchtable.dto.OwnerDto.OwnerReservationResponse;
import academic.cafecatchtable.dto.OwnerDto.PopularSeat;
import academic.cafecatchtable.dto.OwnerDto.StatsResponse;
import academic.cafecatchtable.jdbc.OwnerQueryDao;
import academic.cafecatchtable.jdbc.StatsProcedureDao;
import academic.cafecatchtable.jdbc.ZoneStatRow;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/** 점주 예약 현황·통계 (FR-20, FR-21 / O-10 ~ O-12) — JDBC */
@Service
public class OwnerReservationService {

    /** 기간 미지정 시 최근 7일 (화면 기본값과 동일) */
    private static final int DEFAULT_STATS_DAYS = 7;

    private final OwnerStoreService storeService;
    private final OwnerQueryDao ownerQueryDao;
    private final StatsProcedureDao statsProcedureDao;
    private final DbClock clock;

    public OwnerReservationService(OwnerStoreService storeService, OwnerQueryDao ownerQueryDao,
                                   StatsProcedureDao statsProcedureDao, DbClock clock) {
        this.storeService = storeService;
        this.ownerQueryDao = ownerQueryDao;
        this.statsProcedureDao = statsProcedureDao;
        this.clock = clock;
    }

    /** O-10 선택한 날짜의 예약 타임라인 (예약자 이름·연락처·인원 포함) */
    public List<OwnerReservationResponse> daily(String ownerId, Long cafeId, LocalDate date) {
        storeService.loadOwnedCafe(ownerId, cafeId);
        LocalDate day = date != null ? date : clock.now().toLocalDate();
        return ownerQueryDao.findDailyReservations(cafeId, day);
    }

    /** O-11 예약자 상세. 다른 점주 카페의 예약이면 403 */
    public OwnerReservationResponse detail(String ownerId, Long reservationId) {
        OwnerQueryDao.OwnedReservation found = ownerQueryDao.findReservationWithOwner(reservationId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (!ownerId.equals(found.ownerId())) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        return found.reservation();
    }

    /**
     * O-12 구역 통계 + 인기 좌석 TOP 3
     *  - 구역별 점유율·회전율·평균 체류분·노쇼율 → SP_ZONE_STATS (StatsProcedureDao)
     *  - 인기 좌석 TOP 3                       → SELECT (OwnerQueryDao)
     */
    public StatsResponse stats(String ownerId, Long cafeId, LocalDate from, LocalDate to) {
        storeService.loadOwnedCafe(ownerId, cafeId);
        LocalDate end = to != null ? to : clock.now().toLocalDate();
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_STATS_DAYS - 1);
        if (start.isAfter(end)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "시작일이 종료일보다 늦습니다");
        }
        // 프로시저의 p_to 는 미포함 경계이므로 종료일 다음날 00:00 을 넘긴다
        List<ZoneStatRow> zones = statsProcedureDao.getZoneStats(
                cafeId, start.atStartOfDay(), end.plusDays(1).atStartOfDay());
        List<PopularSeat> topSeats = ownerQueryDao.findTopSeats(cafeId, start, end);
        return new StatsResponse(cafeId, start, end, zones, topSeats);
    }
}
