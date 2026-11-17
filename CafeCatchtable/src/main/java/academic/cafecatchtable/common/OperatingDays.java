package academic.cafecatchtable.common;

import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** CAFE.OPERATING_DAYS ('Mon,Tue,Wed,...') 도우미 */
public final class OperatingDays {

    private OperatingDays() {}

    /** 저장·표시 순서 */
    public static final List<String> CODES = List.of("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun");

    public static boolean isOperatingDay(String operatingDays, DayOfWeek day) {
        if (operatingDays == null || operatingDays.isBlank()) return false;
        String code = CODES.get(day.getValue() - 1);   // MONDAY=1
        return Arrays.stream(operatingDays.split(","))
                .map(String::trim)
                .anyMatch(code::equalsIgnoreCase);
    }

    /**
     * 입력값을 검증하고 'Mon,Tue,...' 표준 순서로 정리한다.
     * 알 수 없는 요일이 있거나 비어 있으면 null.
     */
    public static String normalize(String operatingDays) {
        if (operatingDays == null) return null;
        Set<String> picked = new LinkedHashSet<>();
        for (String raw : operatingDays.split(",")) {
            String token = raw.trim();
            if (token.isEmpty()) continue;
            String code = CODES.stream().filter(c -> c.equalsIgnoreCase(token)).findFirst().orElse(null);
            if (code == null) return null;
            picked.add(code);
        }
        if (picked.isEmpty()) return null;
        return String.join(",", CODES.stream().filter(picked::contains).toList());
    }
}
