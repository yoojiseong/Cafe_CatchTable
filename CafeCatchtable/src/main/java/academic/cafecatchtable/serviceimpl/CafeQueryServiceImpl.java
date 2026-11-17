package academic.cafecatchtable.serviceimpl;

import academic.cafecatchtable.service.CafeQueryService;
import academic.cafecatchtable.common.OperatingDays;

import academic.cafecatchtable.common.ApiException;
import academic.cafecatchtable.common.DbClock;
import academic.cafecatchtable.common.ErrorCode;
import academic.cafecatchtable.common.Status;
import academic.cafecatchtable.domain.Cafe;
import academic.cafecatchtable.dto.CafeDto.*;
import academic.cafecatchtable.jdbc.SlotProcedureDao;
import academic.cafecatchtable.jdbc.SlotRow;
import academic.cafecatchtable.repository.CafeRepository;
import academic.cafecatchtable.repository.ReservationRepository;
import academic.cafecatchtable.repository.SeatRepository;
import academic.cafecatchtable.repository.ZoneRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

/** 카페·좌석 조회 (FR-08 ~ FR-11, C-01 ~ C-04) */
@Service
public class CafeQueryServiceImpl implements CafeQueryService {

    /** 예약 가능 기간: 오늘 ~ 7일 후 (요구사항 FR-14 ②) */
    static final int BOOKING_RANGE_DAYS = 7;

    private final CafeRepository cafeRepository;
    private final ZoneRepository zoneRepository;
    private final SeatRepository seatRepository;
    private final ReservationRepository reservationRepository;
    private final SlotProcedureDao slotProcedureDao;
    private final DbClock clock;

    public CafeQueryServiceImpl(CafeRepository cafeRepository, ZoneRepository zoneRepository,
                            SeatRepository seatRepository, ReservationRepository reservationRepository,
                            SlotProcedureDao slotProcedureDao, DbClock clock) {
        this.cafeRepository = cafeRepository;
        this.zoneRepository = zoneRepository;
        this.seatRepository = seatRepository;
        this.reservationRepository = reservationRepository;
        this.slotProcedureDao = slotProcedureDao;
        this.clock = clock;
    }

    /** C-01 카페 검색: 이름·주소 포함, 이름순. 키워드가 비면 전체 */
    @Override
    @Transactional(readOnly = true)
    public List<CafeResponse> search(String keyword) {
        String pattern = (keyword == null || keyword.isBlank())
                ? null
                : "%" + escapeLike(keyword.trim().toLowerCase(Locale.ROOT)) + "%";
        return cafeRepository.search(pattern).stream().map(CafeResponse::of).toList();
    }

    /** C-02 카페 상세 + 구역 목록 */
    @Override
    @Transactional(readOnly = true)
    public CafeResponse detail(Long cafeId) {
        Cafe cafe = findCafe(cafeId);
        List<ZoneResponse> zones = zoneRepository.findByCafeIdOrderByZoneIdAsc(cafeId).stream()
                .map(ZoneResponse::brief).toList();
        return CafeResponse.of(cafe, zones);
    }

    /**
     * C-03 좌석 조회 + 현재 상태 (FR-09, FR-10).
     * currentStatus: 저장된 STATUS 가 '이용중'·'사용중지'면 그대로,
     *                아니면 지금(DB SYSDATE)에 걸친 '예약완료' 예약이 있으면 '예약됨', 없으면 '빈자리'.
     */
    @Override
    @Transactional(readOnly = true)
    public List<SeatResponse> seats(Long cafeId, Long zoneId, String seatType, String extraItem, Integer visitors) {
        findCafe(cafeId);
        String typeFilter = blankToNull(seatType);
        String itemPattern = blankToNull(extraItem) == null ? null : "%" + extraItem.trim() + "%";
        List<SeatRow> rows = seatRepository.search(cafeId, zoneId, typeFilter, itemPattern, visitors);
        if (rows.isEmpty()) return List.of();

        List<Long> ids = rows.stream().map(SeatRow::seatId).toList();
        Set<Long> reservedNow = new HashSet<>(
                reservationRepository.findSeatIdsReservedAt(ids, Status.RESV_BOOKED, clock.now()));

        return rows.stream().map(r -> new SeatResponse(
                r.seatId(), r.seatNum(), r.zoneId(), r.zoneName(), r.seatType(), r.extraItem(), r.capacity(),
                currentStatus(r.status(), reservedNow.contains(r.seatId()))
        )).toList();
    }

    /**
     * C-04 예약 가능 시간대 (FR-11) → SP_GET_AVAILABLE_SLOTS (SlotProcedureDao)
     * 휴무일·기간 밖은 프로시저 호출 전에 걸러 422 로 응답한다.
     */
    @Override
    @Transactional(readOnly = true)
    public SlotsResponse slots(Long cafeId, LocalDate date, int visitors) {
        Cafe cafe = findCafe(cafeId);
        if (visitors < 1) throw new ApiException(ErrorCode.VALIDATION_FAILED, "인원은 1명 이상입니다");

        LocalDate today = clock.now().toLocalDate();
        if (date.isBefore(today) || date.isAfter(today.plusDays(BOOKING_RANGE_DAYS))) {
            throw new ApiException(ErrorCode.OUT_OF_BOOKING_RANGE);
        }
        if (!OperatingDays.isOperatingDay(cafe.getOperatingDays(), date.getDayOfWeek())) {
            throw new ApiException(ErrorCode.OUT_OF_BUSINESS_HOURS, "영업일이 아닙니다");
        }

        List<SlotRow> rows = slotProcedureDao.getAvailableSlots(cafeId, date, visitors);

        // 커서 결과를 좌석별로 묶는다 (프로시저가 준 순서 유지)
        Map<Long, SeatSlots> bySeat = new LinkedHashMap<>();
        for (SlotRow row : rows) {
            bySeat.computeIfAbsent(row.seatId(),
                            id -> new SeatSlots(row.seatId(), row.seatNum(), row.zoneName(), new ArrayList<>()))
                    .slots().add(new Slot(row.slotStart(), row.available()));
        }
        return new SlotsResponse(cafeId, date, new ArrayList<>(bySeat.values()));
    }

    Cafe findCafe(Long cafeId) {
        return cafeRepository.findById(cafeId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    static String currentStatus(String stored, boolean reservedNow) {
        if (Status.SEAT_IN_USE.equals(stored) || Status.SEAT_DISABLED.equals(stored)) return stored;
        return reservedNow ? Status.SEAT_BOOKED : Status.SEAT_FREE;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** LIKE 와일드카드(% _)를 키워드에서는 글자로 취급 — Hibernate 기본 escape 가 없으므로 제거 */
    private static String escapeLike(String s) {
        return s.replace("%", "").replace("_", "");
    }
}
