package academic.cafecatchtable.jdbc;

/**
 * SP_CREATE_RESERVATION 의 OUT 파라미터를 담는 그릇 (호출은 ReservationProcedureCaller 가 한다).
 * @param reservationId p_reservation_id (실패 시 null)
 * @param resultCode    p_result_code (성공 0, 실패 -20001 ~ -20010)
 */
public record CreateReservationResult(Long reservationId, int resultCode) {}
