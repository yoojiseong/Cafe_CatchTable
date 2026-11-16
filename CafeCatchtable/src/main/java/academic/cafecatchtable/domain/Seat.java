package academic.cafecatchtable.domain;

import academic.cafecatchtable.common.Status;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * SEAT (FR-06, FR-07, FR-09, FR-10)
 * 컬럼: SEAT_ID(PK) / ZONE_ID(FK → ZONE) / SEAT_NUM(구역 내 UNIQUE) / SEAT_TYPE / CAPACITY(≥1)
 *      / EXTRA_ITEM(콘센트·쿠션 등) / STATUS('빈자리'|'이용중'|'사용중지')
 *
 * STATUS 의 '이용중' ↔ '빈자리' 전환은 트리거 TRG_RESV_SEAT_SYNC(DB-T2)가 맡는다.
 * 앱은 점주의 '사용중지' ↔ 사용재개만 바꾼다.
 * DELETE 시 복합 트리거 TRG_SEAT_DELETE_GUARD(DB-T4)가 END_DT > SYSDATE 인 유효 예약을 검사한다(-20010).
 * 통과하면 FK_RESV_SEAT(ON DELETE CASCADE)로 지난 예약행도 함께 지워지고,
 * 지워진 예약은 TRG_RESV_HISTORY 가 RESERVATION_LOG 에 'DELETE' 이력으로 남긴다.
 */
@Entity
@Table(name = "SEAT")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "SEAT_ID")
    private Long seatId;

    @Column(name = "ZONE_ID", nullable = false)
    private Long zoneId;

    @Column(name = "SEAT_NUM", nullable = false)
    private String seatNum;

    @Column(name = "SEAT_TYPE", nullable = false)
    private String seatType;

    @Column(name = "CAPACITY", nullable = false)
    private Integer capacity;

    @Column(name = "EXTRA_ITEM")
    private String extraItem;

    @Column(name = "STATUS", nullable = false)
    private String status;

    public static Seat create(Long zoneId, String seatNum, String seatType, Integer capacity, String extraItem) {
        Seat s = new Seat();
        s.zoneId = zoneId;
        s.seatNum = seatNum;
        s.seatType = seatType;
        s.capacity = capacity;
        s.extraItem = extraItem;
        s.status = Status.SEAT_FREE;
        return s;
    }

    public void update(String seatNum, String seatType, Integer capacity, String extraItem) {
        this.seatNum = seatNum;
        this.seatType = seatType;
        this.capacity = capacity;
        this.extraItem = extraItem;
    }

    /**
     * 점주의 사용중지/사용재개 (FR-06).
     * '이용중' 은 체크인·퇴실 트리거가 관리하므로 사용재개 요청이 와도 덮어쓰지 않는다.
     */
    public void changeAvailability(boolean disabled) {
        if (disabled) {
            this.status = Status.SEAT_DISABLED;
        } else if (Status.SEAT_DISABLED.equals(this.status)) {
            this.status = Status.SEAT_FREE;
        }
    }
}
