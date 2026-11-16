package academic.cafecatchtable.service;

import academic.cafecatchtable.common.ApiException;
import academic.cafecatchtable.common.DbClock;
import academic.cafecatchtable.common.ErrorCode;
import academic.cafecatchtable.common.Status;
import academic.cafecatchtable.dto.ReservationDto.*;
import academic.cafecatchtable.jdbc.CreateReservationResult;
import academic.cafecatchtable.jdbc.ReservationProcedureDao;
import academic.cafecatchtable.repository.ReservationRepository;
import academic.cafecatchtable.repository.SeatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 예약·이용 (FR-12 ~ FR-17, FR-19 / R-01 ~ R-06)
 *
 * 변경(생성·취소·체크인·퇴실)은 ReservationProcedureDao 의 저장프로시저로만 한다.
 * 프로시저가 COMMIT/ROLLBACK 을 직접 하므로 변경 메서드에는 @Transactional 을 걸지 않는다.
 * 프로시저가 돌려준 p_result_code 는 ApiException.throwIfFailed() 로 HTTP 오류로 바꾼다.
 */
@Service
public class ReservationService {

    /** 이용 시간 단위 (FR-12: 1~4시간, 30분 단위) */
    private static final int STAY_UNIT_MINUTES = 30;

    private final ReservationRepository reservationRepository;
    private final SeatRepository seatRepository;
    private final ReservationProcedureDao procedureDao;
    private final DbClock clock;

    public ReservationService(ReservationRepository reservationRepository, SeatRepository seatRepository,
                              ReservationProcedureDao procedureDao, DbClock clock) {
        this.reservationRepository = reservationRepository;
        this.seatRepository = seatRepository;
        this.procedureDao = procedureDao;
        this.clock = clock;
    }

    /** R-01 예약 생성 → SP_CREATE_RESERVATION */
    public ReservationResponse create(String memberId, CreateRequest req) {
        if (req.stayMinutes() % STAY_UNIT_MINUTES != 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "이용 시간은 30분 단위로 선택해 주세요");
        }
        if (!seatRepository.existsById(req.seatId())) {
            throw new ApiException(ErrorCode.NOT_FOUND, "존재하지 않는 좌석입니다");
        }
        LocalDateTime startDt = req.startDt().withSecond(0).withNano(0);
        LocalDateTime endDt = startDt.plusMinutes(req.stayMinutes());   // 서버가 endDt 계산 (API 명세 R-01)

        CreateReservationResult result = procedureDao.createReservation(
                memberId, req.seatId(), startDt, endDt, req.visitorCount());
        ApiException.throwIfFailed(result.resultCode());
        if (result.reservationId() == null) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "예약번호를 받지 못했습니다");
        }
        ReservationRow row = reservationRepository.findRowById(result.reservationId())
                .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR));
        return toResponse(row, null);
    }

    /** R-02 내 예약 목록 (type = active | past | 생략), 최신순 */
    @Transactional(readOnly = true)
    public List<ReservationResponse> myReservations(String memberId, String type) {
        List<ReservationRow> rows;
        if (type == null || type.isBlank() || "all".equalsIgnoreCase(type)) {
            rows = reservationRepository.findRowsByMember(memberId);
        } else if ("active".equalsIgnoreCase(type)) {
            rows = reservationRepository.findRowsByMemberAndStatusIn(memberId, Status.RESV_ACTIVE);
        } else if ("past".equalsIgnoreCase(type)) {
            rows = reservationRepository.findRowsByMemberAndStatusNotIn(memberId, Status.RESV_ACTIVE);
        } else {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "type 은 active 또는 past 입니다");
        }
        LocalDateTime now = clock.now();
        return rows.stream().map(r -> toResponse(r, now)).toList();
    }

    /** R-03 예약 상세: 본인 예약만 */
    @Transactional(readOnly = true)
    public ReservationResponse detail(String memberId, Long reservationId) {
        ReservationRow row = reservationRepository.findRowById(reservationId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (!row.memberId().equals(memberId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        return toResponse(row, clock.now());
    }

    /** R-04 예약 취소 → SP_CANCEL_RESERVATION */
    public StatusResponse cancel(String memberId, Long reservationId) {
        ensureExists(reservationId);
        ApiException.throwIfFailed(procedureDao.cancelReservation(reservationId, memberId));
        return statusOf(reservationId);
    }

    /** R-05 체크인 → SP_CHECK_IN (좌석 '이용중'은 TRG_RESV_SEAT_SYNC 가 처리) */
    public StatusResponse checkIn(String memberId, Long reservationId) {
        ensureExists(reservationId);
        ApiException.throwIfFailed(procedureDao.checkIn(reservationId, memberId));
        return statusOf(reservationId);
    }

    /** R-06 퇴실 → SP_CHECK_OUT (좌석 '빈자리'는 TRG_RESV_SEAT_SYNC 가 처리) */
    public StatusResponse checkOut(String memberId, Long reservationId) {
        ensureExists(reservationId);
        ApiException.throwIfFailed(procedureDao.checkOut(reservationId, memberId));
        return statusOf(reservationId);
    }

    private void ensureExists(Long reservationId) {
        if (!reservationRepository.existsById(reservationId)) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
    }

    /** 프로시저가 COMMIT 한 뒤의 최신 상태를 다시 읽는다 */
    private StatusResponse statusOf(Long reservationId) {
        return reservationRepository.findById(reservationId)
                .map(r -> new StatusResponse(r.getReservationId(), r.getStatus(), r.getActualIn(), r.getActualOut()))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    /** now 가 null 이면 남은 시간 계산을 생략 */
    private static ReservationResponse toResponse(ReservationRow r, LocalDateTime now) {
        Long remaining = null;
        if (now != null && Status.RESV_IN_USE.equals(r.status()) && r.endDt() != null) {
            remaining = Math.max(0, Duration.between(now, r.endDt()).toMinutes());
        }
        return new ReservationResponse(
                r.reservationId(), r.cafeName(), r.zoneName(), r.seatId(), r.seatNum(),
                r.startDt(), r.endDt(), r.visitorCount(), r.status(),
                r.actualIn(), r.actualOut(), remaining, r.createdAt());
    }
}
