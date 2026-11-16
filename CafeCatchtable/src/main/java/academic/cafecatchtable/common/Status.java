package academic.cafecatchtable.common;

import java.util.List;

/** DB에 저장되는 역할·상태 문자열 (요구사항 명세서 용어 정의) */
public final class Status {

    private Status() {}

    /* MEMBER.ROLE */
    public static final String ROLE_USER = "USER";
    public static final String ROLE_OWNER = "OWNER";

    /* SEAT.STATUS */
    public static final String SEAT_FREE = "빈자리";
    public static final String SEAT_IN_USE = "이용중";
    public static final String SEAT_DISABLED = "사용중지";
    /** 저장값이 아니라 조회 시점 계산값 (C-03 currentStatus) */
    public static final String SEAT_BOOKED = "예약됨";

    /* RESERVATION.STATUS */
    public static final String RESV_BOOKED = "예약완료";
    public static final String RESV_IN_USE = "이용중";
    public static final String RESV_DONE = "이용완료";
    public static final String RESV_CANCELED = "예약취소";
    public static final String RESV_NOSHOW = "노쇼";

    /** 유효 예약 = '예약완료' 또는 '이용중' */
    public static final List<String> RESV_ACTIVE = List.of(RESV_BOOKED, RESV_IN_USE);
}
