package academic.cafecatchtable.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

/**
 * RESERVATION (FR-12 ~ FR-19)
 * 컬럼: RESERVATION_ID(PK) / MEMBER_ID / SEAT_ID / START_DT / END_DT (DATE) / VISITOR_COUNT
 *      / STATUS('예약완료'|'이용중'|'이용완료'|'예약취소'|'노쇼') / ACTUAL_IN / ACTUAL_OUT / CREATED_AT
 *
 * 앱에서는 읽기 전용(@Immutable).
 * 생성·취소·체크인·퇴실은 반드시 저장프로시저(jdbc 패키지)로만 바꾼다 (NFR-01).
 */
@Entity
@Immutable
@Table(name = "RESERVATION")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation {

    @Id
    @Column(name = "RESERVATION_ID")
    private Long reservationId;

    @Column(name = "MEMBER_ID")
    private String memberId;

    @Column(name = "SEAT_ID")
    private Long seatId;

    @Column(name = "START_DT")
    private LocalDateTime startDt;

    @Column(name = "END_DT")
    private LocalDateTime endDt;

    @Column(name = "VISITOR_COUNT")
    private Integer visitorCount;

    @Column(name = "STATUS")
    private String status;

    @Column(name = "ACTUAL_IN")
    private LocalDateTime actualIn;

    @Column(name = "ACTUAL_OUT")
    private LocalDateTime actualOut;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;
}
