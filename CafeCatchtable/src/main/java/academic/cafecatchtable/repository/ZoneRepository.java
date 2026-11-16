package academic.cafecatchtable.repository;

import academic.cafecatchtable.domain.Zone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ZoneRepository extends JpaRepository<Zone, Long> {

    List<Zone> findByCafeIdOrderByZoneIdAsc(Long cafeId);

    /** 즉시 DELETE 문을 실행한다 (flush 시점까지 미루지 않음) */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Zone z where z.zoneId = :zoneId")
    int deleteNow(@Param("zoneId") Long zoneId);
}
