package academic.cafecatchtable.jdbc;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;

public record CreateReservationResult(Long reservationId, int resultCode) {

    private static final String URL = "jdbc:oracle:thin:@localhost:1521:XE";
    private static final String ID = "CAFE_CATCHTABLE";
    private static final String PASSWORD = "1234";

    private static Connection DB_Connect() throws SQLException {
        return DriverManager.getConnection(URL, ID, PASSWORD);
    }


    public static CreateReservationResult createReservation(String memberId, Long seatId,
                                                           LocalDateTime startDt, LocalDateTime endDt,
                                                           int visitorCount) {
        String sql = "{call SP_CREATE_RESERVATION(?, ?, ?, ?, ?, ?, ?)}";
        try (Connection con = DB_Connect();
             CallableStatement cstmt = con.prepareCall(sql)) {

            cstmt.setString(1, memberId);
            cstmt.setLong(2, seatId);
            cstmt.setTimestamp(3, Timestamp.valueOf(startDt));
            cstmt.setTimestamp(4, Timestamp.valueOf(endDt));
            cstmt.setInt(5, visitorCount);
            cstmt.registerOutParameter(6, Types.NUMERIC);
            cstmt.registerOutParameter(7, Types.INTEGER);

            cstmt.execute();

            long id = cstmt.getLong(6);
            Long reservationId = cstmt.wasNull() ? null : id;
            int resultCode = cstmt.getInt(7);
            return new CreateReservationResult(reservationId, resultCode);

        } catch (SQLException e) {
            throw new RuntimeException("SP_CREATE_RESERVATION 호출 실패", e);
        }
    }
}
