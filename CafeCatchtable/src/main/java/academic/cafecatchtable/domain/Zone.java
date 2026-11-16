package academic.cafecatchtable.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * ZONE (FR-05)
 * 컬럼: ZONE_ID(PK) / CAFE_ID(FK → CAFE) / ZONE_NAME
 */
@Entity
@Table(name = "ZONE")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Zone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ZONE_ID")
    private Long zoneId;

    @Column(name = "CAFE_ID", nullable = false)
    private Long cafeId;

    @Column(name = "ZONE_NAME", nullable = false)
    private String zoneName;

    public static Zone create(Long cafeId, String zoneName) {
        Zone z = new Zone();
        z.cafeId = cafeId;
        z.zoneName = zoneName;
        return z;
    }

    public void rename(String zoneName) {
        this.zoneName = zoneName;
    }
}
