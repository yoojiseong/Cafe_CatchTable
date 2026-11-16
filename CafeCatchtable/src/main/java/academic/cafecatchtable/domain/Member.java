package academic.cafecatchtable.domain;

import academic.cafecatchtable.common.Status;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

/**
 * MEMBER (FR-01, FR-02)
 * 컬럼: MEMBER_ID(PK, VARCHAR2) / PASSWORD(BCrypt) / NAME / PHONE / ROLE('USER'|'OWNER')
 *      / CREATED_AT(DEFAULT SYSDATE — 매핑하지 않아 INSERT 시 DB 기본값이 들어감)
 * ROLE 은 가입 시 'USER', 점주 신청 시 트리거 TRG_OWNER_ROLE 이 'OWNER' 로 바꾼다.
 *
 * PK 를 직접 지정하므로 Persistable 로 "새 엔티티"임을 알려 save() 가 UPDATE(merge) 대신 INSERT 하게 한다.
 */
@Entity
@Table(name = "MEMBER")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member implements Persistable<String> {

    @Id
    @Column(name = "MEMBER_ID")
    private String memberId;

    @Column(name = "PASSWORD", nullable = false)
    private String password;

    @Column(name = "NAME", nullable = false)
    private String name;

    @Column(name = "PHONE", nullable = false)
    private String phone;

    @Column(name = "ROLE", nullable = false)
    private String role;

    @Transient
    private boolean isNew = false;

    public static Member newUser(String memberId, String encodedPassword, String name, String phone) {
        Member m = new Member();
        m.memberId = memberId;
        m.password = encodedPassword;
        m.name = name;
        m.phone = phone;
        m.role = Status.ROLE_USER;
        m.isNew = true;
        return m;
    }

    @Override
    public String getId() { return memberId; }

    @Override
    public boolean isNew() { return isNew; }

    @PostPersist
    @PostLoad
    void markNotNew() { this.isNew = false; }
}
