package academic.cafecatchtable.serviceimpl;

import academic.cafecatchtable.domain.Seat;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/** 좌석 상태 규칙 (FR-06, FR-10) — DB 불필요 */
class SeatStatusTest {

    @Test
    void C03_현재상태_계산() {
        assertThat(CafeQueryServiceImpl.currentStatus("빈자리", false)).isEqualTo("빈자리");
        assertThat(CafeQueryServiceImpl.currentStatus("빈자리", true)).isEqualTo("예약됨");
        // 저장값이 이용중·사용중지면 예약 여부와 관계없이 그대로
        assertThat(CafeQueryServiceImpl.currentStatus("이용중", true)).isEqualTo("이용중");
        assertThat(CafeQueryServiceImpl.currentStatus("사용중지", true)).isEqualTo("사용중지");
    }

    @Test
    void 사용중지와_사용재개() {
        Seat seat = Seat.create(1L, "A-1", "2인석", 2, null);
        assertThat(seat.getStatus()).isEqualTo("빈자리");

        seat.changeAvailability(true);
        assertThat(seat.getStatus()).isEqualTo("사용중지");

        seat.changeAvailability(false);
        assertThat(seat.getStatus()).isEqualTo("빈자리");
    }

    @Test
    void 이용중_좌석은_사용재개로_덮어쓰지_않는다() {
        Seat seat = Seat.create(1L, "A-1", "2인석", 2, null);
        ReflectionTestUtils.setField(seat, "status", "이용중");

        seat.changeAvailability(false);
        assertThat(seat.getStatus()).isEqualTo("이용중");

        seat.changeAvailability(true);   // 사용중지는 점주가 언제든 지정 가능
        assertThat(seat.getStatus()).isEqualTo("사용중지");
    }
}
