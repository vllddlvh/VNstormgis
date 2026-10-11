package vn.weathergis.location.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.weathergis.location.domain.LocationEntity;

/** Spring Data JPA Repository quản lý trạm quan trắc tích hợp truy vấn trắc địa PostGIS. */
@Repository
public interface LocationRepository extends JpaRepository<LocationEntity, UUID> {

    Optional<LocationEntity> findByCode(String code);

    List<LocationEntity> findByIsActiveTrue();

    List<LocationEntity> findByRegionAndIsActiveTrue(String region);

    @Query(
            "SELECT l FROM LocationEntity l WHERE "
                    + "(:region IS NULL OR l.region = :region) AND "
                    + "(:isActive IS NULL OR l.isActive = :isActive) AND "
                    + "(:search IS NULL OR LOWER(l.name) LIKE LOWER(CONCAT('%', CAST(:search AS String), '%')) OR LOWER(l.code) LIKE LOWER(CONCAT('%', CAST(:search AS String), '%')))")
    List<LocationEntity> searchLocations(
            @Param("region") String region,
            @Param("isActive") Boolean isActive,
            @Param("search") String search);

    /**
     * Tìm các trạm quan trắc nằm trong bán kính bán cầu trắc địa sử dụng PostGIS ST_DistanceSphere.
     */
    @Query(
            value =
                    """
            SELECT l.id, l.code, l.name, l.region,
                   ST_X(l.geom) AS longitude, ST_Y(l.geom) AS latitude,
                   ST_DistanceSphere(l.geom, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)) / 1000.0 AS distance_km
            FROM locations l
            WHERE l.is_active = true
              AND ST_DWithin(l.geom::geography, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, :radiusMeters)
            ORDER BY distance_km ASC
            """,
            nativeQuery = true)
    List<Object[]> findNearbyStationsNative(
            @Param("lng") double lng,
            @Param("lat") double lat,
            @Param("radiusMeters") double radiusMeters);

    /** Truy vấn lọc trạm quan trắc nằm trong khung nhìn Bounding Box của bản đồ Leaflet. */
    @Query(
            value =
                    """
            SELECT * FROM locations l
            WHERE l.is_active = true
              AND l.geom && ST_MakeEnvelope(:minLng, :minLat, :maxLng, :maxLat, 4326)
            """,
            nativeQuery = true)
    List<LocationEntity> findWithinBoundingBox(
            @Param("minLng") double minLng,
            @Param("minLat") double minLat,
            @Param("maxLng") double maxLng,
            @Param("maxLat") double maxLat);
}
