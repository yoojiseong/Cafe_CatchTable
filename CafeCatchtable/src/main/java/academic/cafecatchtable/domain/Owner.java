package academic.cafecatchtable.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

/**
 * OWNER (FR-03)
 * 컬럼: OWNER_ID(PK, = MEMBER.MEMBER_ID) / BUSINESS_NUM(UNIQUE, 000-00-00000)
 *
 * INSERT 되면 트리거 TRG_OWNER_ROLE(DB-T5, AFTER INSERT ON OWNER)이 MEMBER.ROLE 을 'OWNER' 로 바꾼다.
 * → 앱에서 MEMBER.ROLE 을 직접 UPDATE 하지 않는다.
 */
@Entity
@Table(name = "OWNER")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Owner implements Persistable<String> {

    @Id
    @Column(name = "OWNER_ID")
    private String ownerId;

    @Column(name = "BUSINESS_NUM", nullable = false, unique = true)
    private String businessNum;

    @Transient
    private boolean isNew = false;

    public static Owner of(String memberId, String businessNum) {
        Owner o = new Owner();
        o.ownerId = memberId;
        o.businessNum = businessNum;
        o.isNew = true;
        return o;
    }

    @Override
    public String getId() { return ownerId; }

    @Override
    public boolean isNew() { return isNew; }

    @PostPersist
    @PostLoad
    void markNotNew() { this.isNew = false; }
}
