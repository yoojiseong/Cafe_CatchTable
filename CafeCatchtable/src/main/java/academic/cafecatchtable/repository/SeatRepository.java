package academic.cafecatchtable.repository;

import academic.cafecatchtable.domain.Seat;
import academic.cafecatchtable.dto.CafeDto.SeatRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    /**
     * C-03 조건별 좌석 조회 (FR-09, 질의 2·8) — 구역명과 함께.
     * null 인 조건은 무시한다. extraItemPattern 은 '%콘센트%' 형태.
     */
    @Query("""
            select new academic.cafecatchtable.dto.CafeDto$SeatRow(
                       s.seatId, s.seatNum, z.zoneId, z.zoneName,
                       s.seatType, s.extraItem, s.capacity, s.status)
              from Seat s
              join Zone z on z.zoneId = s.zoneId
             where z.cafeId = :cafeId
               and (:zoneId is null or z.zoneId = :zoneId)
               and (:seatType is null or s.seatType = :seatType)
               and (:extraItem is null or s.extraItem like :extraItem)
               and (:visitors is null or s.capacity >= :visitors)
             order by z.zoneId, s.seatNum
            """)
    List<SeatRow> search(@Param("cafeId") Long cafeId,
                         @Param("zoneId") Long zoneId,
                         @Param("seatType") String seatType,
                         @Param("extraItem") String extraItemPattern,
                         @Param("visitors") Integer visitors);

    /** 좌석 → 카페 ID (점주 권한 검사, 예약 생성 시 존재 확인) */
    @Query("select z.cafeId from Seat s join Zone z on z.zoneId = s.zoneId where s.seatId = :seatId")
    List<Long> findCafeIdBySeatId(@Param("seatId") Long seatId);

    long countByZoneId(Long zoneId);

    boolean existsByZoneIdAndSeatNum(Long zoneId, String seatNum);

    boolean existsByZoneIdAndSeatNumAndSeatIdNot(Long zoneId, String seatNum, Long seatId);

    /**
     * 즉시 DELETE 실행 → 행마다 TRG_SEAT_DELETE_GUARD(BEFORE DELETE ON SEAT)가 돌아
     * 유효 예약이 남은 좌석이면 ORA-20010 으로 실패한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Seat s where s.seatId = :seatId")
    int deleteNow(@Param("seatId") Long seatId);

    /** 구역의 좌석 전체 즉시 DELETE (구역 삭제 FR-05). 트리거 동작은 deleteNow 와 같다 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Seat s where s.zoneId = :zoneId")
    int deleteAllInZoneNow(@Param("zoneId") Long zoneId);
}
