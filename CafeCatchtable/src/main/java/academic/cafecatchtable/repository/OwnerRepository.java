package academic.cafecatchtable.repository;

import academic.cafecatchtable.domain.Owner;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OwnerRepository extends JpaRepository<Owner, String> {

    boolean existsByBusinessNum(String businessNum);
}
