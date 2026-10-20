package vn.weathergis.location.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import vn.weathergis.common.AbstractPostGisIntegrationTest;
import vn.weathergis.location.domain.LocationEntity;

/**
 * Bộ kiểm thử tích hợp (Integration Test Suite) chuyên sâu cho LocationRepository. Chạy trên
 * Testcontainers PostGIS 16-3.4-alpine, xác thực toàn bộ các truy vấn không gian native: 1. Tính
 * toàn vẹn hình học JTS Point và hệ tọa độ WGS84 EPSG:4326. 2. Tính toán khoảng cách mặt cầu trắc
 * địa native ST_DistanceSphere & ST_DWithin. 3. Lọc khung nhìn Viewport Leaflet bằng toán tử bao
 * đóng không gian && và ST_MakeEnvelope. 4. Quản lý phân vùng địa chính và tìm kiếm trắc địa.
 */
@SpringBootTest
@DisplayName("LocationRepository Spatial Integration Tests (PostGIS 16 Testcontainers)")
class LocationRepositoryIT extends AbstractPostGisIntegrationTest {

    @Autowired private LocationRepository locationRepository;

    @Nested
    @DisplayName("Nhóm 1: Tính toàn vẹn hình học (Geometric Integrity & CRS EPSG:4326)")
    class GeometricIntegrityTests {

        @Test
        @DisplayName(
                "TC-IT-01: Xác thực trạm Hà Nội (HAN) tồn tại, SRID=4326 và không đảo trục X/Y")
        void testGeometricIntegrity_HanoiStation() {
            Optional<LocationEntity> hanoiOpt = locationRepository.findByCode("HAN");

            assertThat(hanoiOpt).isPresent();
            LocationEntity hanoi = hanoiOpt.get();

            assertThat(hanoi.getCode()).isEqualTo("HAN");
            assertThat(hanoi.getName()).isEqualTo("Hà Nội");
            assertThat(hanoi.getRegion()).isEqualTo("Bắc Bộ");
            assertThat(hanoi.getIsActive()).isTrue();

            Point geom = hanoi.getGeom();
            assertThat(geom).isNotNull();
            assertThat(geom.getSRID()).isEqualTo(4326);

            // Bắt buộc X là Longitude (Kinh độ: ~105.8542) và Y là Latitude (Vĩ độ: ~21.0285)
            assertThat(geom.getX()).isCloseTo(105.8542, within(0.0001));
            assertThat(geom.getY()).isCloseTo(21.0285, within(0.0001));
        }

        @Test
        @DisplayName("TC-IT-02: Xác thực trạm hải đảo Trường Sa (TSA) và Hoàng Sa (HSA)")
        void testGeometricIntegrity_IslandStations() {
            Optional<LocationEntity> truongSaOpt = locationRepository.findByCode("TSA");
            assertThat(truongSaOpt).isPresent();
            LocationEntity truongSa = truongSaOpt.get();
            assertThat(truongSa.getRegion()).isEqualTo("Biển Đông");
            assertThat(truongSa.getGeom().getX()).isCloseTo(114.3667, within(0.0001));
            assertThat(truongSa.getGeom().getY()).isCloseTo(8.6500, within(0.0001));

            Optional<LocationEntity> hoangSaOpt = locationRepository.findByCode("HSA");
            assertThat(hoangSaOpt).isPresent();
            LocationEntity hoangSa = hoangSaOpt.get();
            assertThat(hoangSa.getRegion()).isEqualTo("Biển Đông");
            assertThat(hoangSa.getGeom().getX()).isCloseTo(111.6000, within(0.0001));
            assertThat(hoangSa.getGeom().getY()).isCloseTo(16.5000, within(0.0001));
        }
    }

    @Nested
    @DisplayName("Nhóm 2: Truy vấn bán cầu trắc địa native (ST_DistanceSphere & ST_DWithin)")
    class SpatialDistanceSphereTests {

        @Test
        @DisplayName(
                "TC-IT-03: Tìm các trạm trong bán kính 150km từ tâm Hà Nội, khoảng cách tăng dần")
        void testNearbyStationsNative_Within150KmFromHanoi() {
            double hanoiLng = 105.8542;
            double hanoiLat = 21.0285;
            double radiusMeters = 150_000.0; // 150 km

            List<Object[]> nearby =
                    locationRepository.findNearbyStationsNative(hanoiLng, hanoiLat, radiusMeters);

            assertThat(nearby).isNotEmpty();

            // Trạm đầu tiên phải là chính Hà Nội (khoảng cách xấp xỉ 0 km)
            Object[] firstRow = nearby.get(0);
            assertThat(firstRow[1]).isEqualTo("HAN");
            double firstDistance = ((Number) firstRow[6]).doubleValue();
            assertThat(firstDistance).isCloseTo(0.0, within(0.01));

            // Xác thực khoảng cách tăng dần theo ORDER BY distance_km ASC
            double prevDistance = -0.001;
            for (Object[] row : nearby) {
                double distanceKm = ((Number) row[6]).doubleValue();
                assertThat(distanceKm)
                        .as("Khoảng cách phải tăng dần")
                        .isGreaterThanOrEqualTo(prevDistance);
                assertThat(distanceKm)
                        .as("Khoảng cách phải nằm trong bán kính 150km")
                        .isLessThanOrEqualTo(150.0);
                prevDistance = distanceKm;
            }

            // Danh sách trạm lân cận phải bao gồm các tỉnh đồng bằng Bắc Bộ (Hải Phòng, Hải Dương,
            // Nam Định)
            List<String> codes = nearby.stream().map(row -> (String) row[1]).toList();
            assertThat(codes).contains("HPH", "HDG", "NDH", "NBH", "BNH");
        }
    }

    @Nested
    @DisplayName("Nhóm 3: Truy vấn lọc khung nhìn Viewport Leaflet (ST_MakeEnvelope &&)")
    class BoundingBoxViewportTests {

        @Test
        @DisplayName(
                "TC-IT-04: Lọc các trạm trong Bounding Box Miền Trung [107.0°E, 15.0°N] đến [109.5°E, 17.0°N]")
        void testFindWithinBoundingBox_CentralVietnam() {
            double minLng = 107.0;
            double minLat = 15.0;
            double maxLng = 109.5;
            double maxLat = 17.0;

            List<LocationEntity> stations =
                    locationRepository.findWithinBoundingBox(minLng, minLat, maxLng, maxLat);

            assertThat(stations).isNotEmpty();

            List<String> codes = stations.stream().map(LocationEntity::getCode).toList();

            // Phải chứa các trạm thuộc dải Đà Nẵng, Huế, Quảng Nam, Quảng Trị, Quảng Ngãi
            assertThat(codes).contains("DNG", "HUE", "QNM", "QTI", "QNG");

            // Tuyệt đối không chứa trạm phía Bắc (HAN) hay phía Nam (SGN, CMU)
            assertThat(codes).doesNotContain("HAN", "SGN", "CMU");

            // Mọi trạm trả về đều phải nằm chuẩn xác bên trong BBox
            for (LocationEntity station : stations) {
                assertThat(station.getGeom().getX()).isBetween(minLng, maxLng);
                assertThat(station.getGeom().getY()).isBetween(minLat, maxLat);
                assertThat(station.getIsActive()).isTrue();
            }
        }
    }

    @Nested
    @DisplayName("Nhóm 4: Quản lý phân vùng địa chính & Tìm kiếm trắc địa")
    class RegionalAndSearchTests {

        @Test
        @DisplayName("TC-IT-05: Lọc trạm theo vùng Bắc Bộ và kiểm tra tổng số 68 trạm active")
        void testFindByRegionAndActive() {
            List<LocationEntity> northStations =
                    locationRepository.findByRegionAndIsActiveTrue("Bắc Bộ");
            assertThat(northStations).isNotEmpty();
            assertThat(northStations)
                    .allMatch(
                            st ->
                                    "Bắc Bộ".equals(st.getRegion())
                                            && Boolean.TRUE.equals(st.getIsActive()));

            List<LocationEntity> allActive = locationRepository.findByIsActiveTrue();
            // Flyway V3 nạp đầy đủ 68 trạm quan trắc/tỉnh thành WGS84 Việt Nam
            assertThat(allActive).hasSize(68);
        }

        @Test
        @DisplayName("TC-IT-06: Tìm kiếm trạm theo vùng, trạng thái và từ khóa tìm kiếm")
        void testSearchLocations() {
            List<LocationEntity> haStations =
                    locationRepository.searchLocations("Bắc Bộ", true, "Hà");
            List<String> codes = haStations.stream().map(LocationEntity::getCode).toList();

            // Chứa Hà Nội (HAN), Hà Nam (HNM), Hà Giang (HGG)
            assertThat(codes).contains("HAN", "HNM", "HGG");
            assertThat(codes).doesNotContain("SGN", "DNG", "HBH");
        }
    }
}
