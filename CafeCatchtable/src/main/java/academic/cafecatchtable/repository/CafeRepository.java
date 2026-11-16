package academic.cafecatchtable.repository;

import academic.cafecatchtable.domain.Cafe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CafeRepository extends JpaRepository<Cafe, Long> {

    /** C-01 카페 검색 (FR-08, 질의 1): 이름 또는 주소에 키워드 포함, 이름순. keywordPattern 이 null 이면 전체 */
    @Query("""
            select c from Cafe c
             where :kw is null
                or lower(c.cafeName) like :kw
                or lower(c.address)  like :kw
             order by c.cafeName
            """)
    List<Cafe> search(@Param("kw") String keywordPattern);

    /** O-01 내 카페 목록 */
    List<Cafe> findByOwnerIdOrderByCafeNameAsc(String ownerId);
}
