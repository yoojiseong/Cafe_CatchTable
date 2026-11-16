package academic.cafecatchtable.service;

import academic.cafecatchtable.common.ApiException;
import academic.cafecatchtable.common.ErrorCode;
import academic.cafecatchtable.common.Status;
import academic.cafecatchtable.domain.Cafe;
import academic.cafecatchtable.domain.Seat;
import academic.cafecatchtable.domain.Zone;
import academic.cafecatchtable.dto.CafeDto.CafeResponse;
import academic.cafecatchtable.dto.CafeDto.SeatResponse;
import academic.cafecatchtable.dto.CafeDto.ZoneResponse;
import academic.cafecatchtable.dto.OwnerDto.CafeRequest;
import academic.cafecatchtable.dto.OwnerDto.SeatRequest;
import academic.cafecatchtable.dto.OwnerDto.ZoneRequest;
import academic.cafecatchtable.repository.CafeRepository;
import academic.cafecatchtable.repository.SeatRepository;
import academic.cafecatchtable.repository.ZoneRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 점주 매장 관리 (FR-04 ~ FR-07, O-01 ~ O-09) — JPA
 * 모든 메서드는 점주 본인 카페(CAFE.OWNER_ID = 로그인 ID)인지 먼저 확인한다 (아니면 403).
 */
@Service
public class OwnerStoreService {

    private final CafeRepository cafeRepository;
    private final ZoneRepository zoneRepository;
    private final SeatRepository seatRepository;

    public OwnerStoreService(CafeRepository cafeRepository, ZoneRepository zoneRepository,
                             SeatRepository seatRepository) {
        this.cafeRepository = cafeRepository;
        this.zoneRepository = zoneRepository;
        this.seatRepository = seatRepository;
    }

    /* ======================= 카페 (FR-04) ======================= */

    /** O-01 내 카페 목록 */
    @Transactional(readOnly = true)
    public List<CafeResponse> myCafes(String ownerId) {
        return cafeRepository.findByOwnerIdOrderByCafeNameAsc(ownerId).stream().map(CafeResponse::of).toList();
    }

    /** O-02 카페 등록 (점주 1명이 여러 카페 운영 가능) */
    @Transactional
    public CafeResponse createCafe(String ownerId, CafeRequest req) {
        String days = validateCafe(req);
        Cafe cafe = cafeRepository.save(Cafe.create(ownerId, req.cafeName().trim(), req.address().trim(),
                req.phone().trim(), toDbTime(req.startTime()), toDbTime(req.endTime()), days));
        return CafeResponse.of(cafe);
    }

    /** O-03 카페 수정 */
    @Transactional
    public CafeResponse updateCafe(String ownerId, Long cafeId, CafeRequest req) {
        Cafe cafe = loadOwnedCafe(ownerId, cafeId);
        String days = validateCafe(req);
        cafe.update(req.cafeName().trim(), req.address().trim(), req.phone().trim(),
                toDbTime(req.startTime()), toDbTime(req.endTime()), days);
        return CafeResponse.of(cafe);
    }

    /* ======================= 구역 (FR-05) ======================= */

    /** O-04 구역 추가 */
    @Transactional
    public ZoneResponse addZone(String ownerId, Long cafeId, ZoneRequest req) {
        loadOwnedCafe(ownerId, cafeId);
        Zone zone = zoneRepository.save(Zone.create(cafeId, req.zoneName().trim()));
        return ZoneResponse.of(zone);
    }

    /** O-05 구역 이름 변경 */
    @Transactional
    public ZoneResponse renameZone(String ownerId, Long zoneId, ZoneRequest req) {
        Zone zone = loadOwnedZone(ownerId, zoneId);
        zone.rename(req.zoneName().trim());
        return ZoneResponse.of(zone);
    }

    /**
     * O-06 구역 삭제 (FR-05, FR-07)
     * 화면에서 확인을 받은 뒤 호출되며, 구역의 좌석까지 함께 DELETE 한다.
     * 좌석 DELETE 마다 트리거 TRG_SEAT_DELETE_GUARD(DB-T4, BEFORE DELETE ON SEAT)가 실행되어
     * 앞으로 유효 예약이 남은 좌석이 하나라도 있으면 ORA-20010 → 전체 롤백 → 409 SEAT_HAS_RESERVATION.
     */
    @Transactional
    public void deleteZone(String ownerId, Long zoneId) {
        loadOwnedZone(ownerId, zoneId);
        seatRepository.deleteAllInZoneNow(zoneId);   // → TRG_SEAT_DELETE_GUARD
        zoneRepository.deleteNow(zoneId);
    }

    /* ======================= 좌석 (FR-06, FR-07) ======================= */

    /** O-07 좌석 등록. 좌석번호는 구역 안에서 유일 */
    @Transactional
    public SeatResponse addSeat(String ownerId, Long zoneId, SeatRequest req) {
        Zone zone = loadOwnedZone(ownerId, zoneId);
        String seatNum = req.seatNum().trim();
        if (seatRepository.existsByZoneIdAndSeatNum(zoneId, seatNum)) {
            throw new ApiException(ErrorCode.DUPLICATE_SEAT_NUM);
        }
        Seat seat = Seat.create(zoneId, seatNum, req.seatType().trim(), req.capacity(), blankToNull(req.extraItem()));
        try {
            seatRepository.saveAndFlush(seat);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(ErrorCode.DUPLICATE_SEAT_NUM);
        }
        return toSeatResponse(seat, zone);
    }

    /** O-08 좌석 수정·사용중지 ('사용중지'로 바꾸면 예약 가능 목록에서 빠진다) */
    @Transactional
    public SeatResponse updateSeat(String ownerId, Long seatId, SeatRequest req) {
        Seat seat = seatRepository.findById(seatId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        Zone zone = loadOwnedZone(ownerId, seat.getZoneId());

        String status = blankToNull(req.status());
        if (status != null && !Status.SEAT_FREE.equals(status) && !Status.SEAT_DISABLED.equals(status)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "좌석 상태는 '빈자리' 또는 '사용중지'만 지정할 수 있습니다");
        }
        String seatNum = req.seatNum().trim();
        if (seatRepository.existsByZoneIdAndSeatNumAndSeatIdNot(seat.getZoneId(), seatNum, seatId)) {
            throw new ApiException(ErrorCode.DUPLICATE_SEAT_NUM);
        }
        seat.update(seatNum, req.seatType().trim(), req.capacity(), blankToNull(req.extraItem()));
        if (status != null) {
            seat.changeAvailability(Status.SEAT_DISABLED.equals(status));
        }
        try {
            seatRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(ErrorCode.DUPLICATE_SEAT_NUM);
        }
        return toSeatResponse(seat, zone);
    }

    /**
     * O-09 좌석 삭제 (FR-07)
     * DELETE 시 트리거 TRG_SEAT_DELETE_GUARD(DB-T4)가 END_DT > SYSDATE 인 유효 예약을 검사해
     * 있으면 ORA-20010 → GlobalExceptionHandler 가 409 SEAT_HAS_RESERVATION 으로 바꾼다.
     */
    @Transactional
    public void deleteSeat(String ownerId, Long seatId) {
        Seat seat = seatRepository.findById(seatId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        loadOwnedZone(ownerId, seat.getZoneId());
        seatRepository.deleteNow(seatId);   // → TRG_SEAT_DELETE_GUARD
    }

    /* ======================= 권한 검사 ======================= */

    /** 카페가 없으면 404, 본인 카페가 아니면 403 */
    @Transactional(readOnly = true)
    public Cafe loadOwnedCafe(String ownerId, Long cafeId) {
        Cafe cafe = cafeRepository.findById(cafeId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (!cafe.isOwnedBy(ownerId)) throw new ApiException(ErrorCode.FORBIDDEN);
        return cafe;
    }

    private Zone loadOwnedZone(String ownerId, Long zoneId) {
        Zone zone = zoneRepository.findById(zoneId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        loadOwnedCafe(ownerId, zone.getCafeId());
        return zone;
    }

    /* ======================= 도우미 ======================= */

    /**
     * FR-04: 종료 ≤ 시작이면 거부 (DB 제약 CK_CAFE_TIME_ORDER 와 같은 규칙), 영업일 1개 이상.
     * 정리된 영업일 문자열('Mon,Tue,...')을 돌려준다. 프로시저는 UPPER 로 비교하므로 대소문자는 상관없다.
     */
    private static String validateCafe(CafeRequest req) {
        // 'HH:mm' 고정 길이라 문자열 비교 = 시간 비교
        if (toDbTime(req.endTime()).compareTo(toDbTime(req.startTime())) <= 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "영업 종료 시간은 시작 시간보다 늦어야 합니다");
        }
        String days = OperatingDays.normalize(req.operatingDays());
        if (days == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "영업일을 하나 이상 올바르게 선택해 주세요");
        }
        return days;
    }

    /** 요구사항 6장: 24:00 종료는 23:59 로 본다 (CK_CAFE_END_TIME 은 23:59 까지만 허용) */
    private static String toDbTime(String hhmm) {
        return "24:00".equals(hhmm) ? "23:59" : hhmm;
    }

    private static SeatResponse toSeatResponse(Seat s, Zone z) {
        return new SeatResponse(s.getSeatId(), s.getSeatNum(), z.getZoneId(), z.getZoneName(),
                s.getSeatType(), s.getExtraItem(), s.getCapacity(), s.getStatus());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
