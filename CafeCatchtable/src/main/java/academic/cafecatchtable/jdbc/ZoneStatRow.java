package academic.cafecatchtable.jdbc;

/**
 * SP_ZONE_STATS 커서 한 행 = 구역 1개의 통계. O-12 응답의 zones[] 로 그대로 나간다.
 * 커서 열: ZONE_NAME, RESV_COUNT, AVG_STAY_MIN, OCCUPANCY_RATE, TURNOVER, NOSHOW_RATE
 *  - OCCUPANCY_RATE, NOSHOW_RATE : 백분율(0~100, 소수 2자리)
 *  - AVG_STAY_MIN               : 이용완료 예약이 없으면 NULL
 *  - NOSHOW_RATE                : 예약이 0건이면 NULL
 *  - TURNOVER                   : 좌석이 0개면 NULL
 */
public record ZoneStatRow(String zoneName, Long resvCount, Double avgStayMin,
                          Double occupancyRate, Double turnover, Double noshowRate) {}
