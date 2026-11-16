package academic.cafecatchtable.repository;

import academic.cafecatchtable.domain.Reservation;
import academic.cafecatchtable.dto.ReservationDto.ReservationRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** RESERVATION 읽기 전용 조회 (변경은 저장프로시저로만) */
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    String ROW_SELECT = """
            select new academic.cafecatchtable.dto.ReservationDto$ReservationRow(
                       r.reservationId, r.memberId, c.cafeId, c.cafeName, z.zoneName,
                       s.seatId, s.seatNum, r.startDt, r.endDt, r.visitorCount,
                       r.status, r.actualIn, r.actualOut, r.createdAt)
              from Reservation r
              join Seat s on s.seatId = r.seatId
              join Zone z on z.zoneId = s.zoneId
              join Cafe c on c.cafeId = z.cafeId
            """;

    /** C-03 현재 상태 (FR-10, 질의 3): 지금 시각에 걸친 '예약완료' 예약이 있는 좌석 ID */
    @Query("""
            select distinct r.seatId from Reservation r
             where r.seatId in :seatIds
               and r.status = :status
               and r.startDt <= :now and r.endDt > :now
            """)
    List<Long> findSeatIdsReservedAt(@Param("seatIds") Collection<Long> seatIds,
                                     @Param("status") String status,
                                     @Param("now") LocalDateTime now);

    /** R-02 내 예약 전체, 최신순 (FR-19, 질의 7) */
    @Query(ROW_SELECT + " where r.memberId = :memberId order by r.startDt desc")
    List<ReservationRow> findRowsByMember(@Param("memberId") String memberId);

    /** R-02 type=active: '예약완료'·'이용중' (질의 9) */
    @Query(ROW_SELECT + " where r.memberId = :memberId and r.status in :statuses order by r.startDt desc")
    List<ReservationRow> findRowsByMemberAndStatusIn(@Param("memberId") String memberId,
                                                     @Param("statuses") Collection<String> statuses);

    /** R-02 type=past: 나머지 상태 (질의 10) */
    @Query(ROW_SELECT + " where r.memberId = :memberId and r.status not in :statuses order by r.startDt desc")
    List<ReservationRow> findRowsByMemberAndStatusNotIn(@Param("memberId") String memberId,
                                                        @Param("statuses") Collection<String> statuses);

    /** R-01 응답, R-03 상세 */
    @Query(ROW_SELECT + " where r.reservationId = :id")
    Optional<ReservationRow> findRowById(@Param("id") Long reservationId);
}
