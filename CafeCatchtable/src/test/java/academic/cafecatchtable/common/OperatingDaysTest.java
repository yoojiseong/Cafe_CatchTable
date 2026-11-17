package academic.cafecatchtable.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.DayOfWeek;

import static org.assertj.core.api.Assertions.assertThat;

/** 영업일 문자열 처리 (FR-04, FR-11) — DB 불필요 */
class OperatingDaysTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Mon,Tue           | Mon,Tue",
            "sun, MON ,wed     | Mon,Wed,Sun",
            "MON,TUE,WED,THU,FRI,SAT,SUN | Mon,Tue,Wed,Thu,Fri,Sat,Sun",
            "Fri,Fri,,Mon      | Mon,Fri"
    })
    void 대소문자_공백_중복을_정리해_월요일부터_순서대로(String input, String expected) {
        assertThat(OperatingDays.normalize(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" , ", "Mon,Funday", "월,화"})
    void 비었거나_알_수_없는_요일이면_null(String input) {
        assertThat(OperatingDays.normalize(input)).isNull();
    }

    @Test
    void 영업일_여부는_대소문자를_가리지_않는다() {
        assertThat(OperatingDays.isOperatingDay("MON,WED", DayOfWeek.MONDAY)).isTrue();
        assertThat(OperatingDays.isOperatingDay("Mon, Wed", DayOfWeek.WEDNESDAY)).isTrue();
        assertThat(OperatingDays.isOperatingDay("Mon,Wed", DayOfWeek.SUNDAY)).isFalse();
        assertThat(OperatingDays.isOperatingDay(null, DayOfWeek.MONDAY)).isFalse();
        assertThat(OperatingDays.isOperatingDay("", DayOfWeek.MONDAY)).isFalse();
    }
}
