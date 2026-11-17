package academic.cafecatchtable.service;

import academic.cafecatchtable.dto.ReservationDto.CreateRequest;
import academic.cafecatchtable.dto.ReservationDto.ReservationResponse;
import academic.cafecatchtable.dto.ReservationDto.StatusResponse;

import java.util.List;

/**
 * 예약·이용 (FR-12 ~ FR-17, FR-19 / R-01 ~ R-06). 구현: serviceimpl.ReservationServiceImpl
 * 생성·취소·체크인·퇴실은 저장프로시저(ReservationProcedureDao)로만 처리한다.
 */
public interface ReservationService {

    /** R-01 예약 생성 → SP_CREATE_RESERVATION */
    ReservationResponse create(String memberId, CreateRequest req);

    /** R-02 내 예약 목록 (type = active | past | 생략), 최신순 */
    List<ReservationResponse> myReservations(String memberId, String type);

    /** R-03 예약 상세: 본인 예약만 */
    ReservationResponse detail(String memberId, Long reservationId);

    /** R-04 예약 취소 → SP_CANCEL_RESERVATION */
    StatusResponse cancel(String memberId, Long reservationId);

    /** R-05 체크인 → SP_CHECK_IN */
    StatusResponse checkIn(String memberId, Long reservationId);

    /** R-06 퇴실 → SP_CHECK_OUT */
    StatusResponse checkOut(String memberId, Long reservationId);
}
