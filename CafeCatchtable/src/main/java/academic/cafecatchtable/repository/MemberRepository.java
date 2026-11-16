package academic.cafecatchtable.repository;

import academic.cafecatchtable.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, String> {

    /** 트리거(TRG_OWNER_ROLE)가 바꾼 ROLE 을 영속성 컨텍스트 캐시 없이 DB 에서 다시 읽는다 */
    @Query("select m.role from Member m where m.memberId = :memberId")
    Optional<String> findRoleById(@Param("memberId") String memberId);
}
