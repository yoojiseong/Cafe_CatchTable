package academic.cafecatchtable.jdbc;

import java.time.LocalDateTime;

/** SP_GET_AVAILABLE_SLOTS 커서 한 행 (SEAT_ID, SEAT_NUM, ZONE_NAME, SLOT_START, AVAILABLE) */
public record SlotRow(Long seatId, String seatNum, String zoneName, LocalDateTime slotStart, boolean available) {}
