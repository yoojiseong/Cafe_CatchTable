package academic.cafecatchtable.service;

import academic.cafecatchtable.domain.Cafe;
import academic.cafecatchtable.dto.CafeDto.CafeResponse;
import academic.cafecatchtable.dto.CafeDto.SeatResponse;
import academic.cafecatchtable.dto.CafeDto.ZoneResponse;
import academic.cafecatchtable.dto.OwnerDto.CafeRequest;
import academic.cafecatchtable.dto.OwnerDto.SeatRequest;
import academic.cafecatchtable.dto.OwnerDto.ZoneRequest;

import java.util.List;

/** 점주 카페·구역·좌석 관리 (FR-04 ~ FR-07 / O-01 ~ O-09). 구현: serviceimpl.OwnerStoreServiceImpl */
public interface OwnerStoreService {

    /** O-01 내 카페 목록 */
    List<CafeResponse> myCafes(String ownerId);

    /** O-02 카페 등록 */
    CafeResponse createCafe(String ownerId, CafeRequest req);

    /** O-03 카페 수정 */
    CafeResponse updateCafe(String ownerId, Long cafeId, CafeRequest req);

    /** O-04 구역 추가 */
    ZoneResponse addZone(String ownerId, Long cafeId, ZoneRequest req);

    /** O-05 구역 이름 변경 */
    ZoneResponse renameZone(String ownerId, Long zoneId, ZoneRequest req);

    /** O-06 구역 삭제 (좌석 포함, TRG_SEAT_DELETE_GUARD 적용) */
    void deleteZone(String ownerId, Long zoneId);

    /** O-07 좌석 등록 */
    SeatResponse addSeat(String ownerId, Long zoneId, SeatRequest req);

    /** O-08 좌석 수정·사용중지 */
    SeatResponse updateSeat(String ownerId, Long seatId, SeatRequest req);

    /** O-09 좌석 삭제 (TRG_SEAT_DELETE_GUARD 적용) */
    void deleteSeat(String ownerId, Long seatId);

    /** 카페가 없으면 404, 본인 카페가 아니면 403 */
    Cafe loadOwnedCafe(String ownerId, Long cafeId);
}
