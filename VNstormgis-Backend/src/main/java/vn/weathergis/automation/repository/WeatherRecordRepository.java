package vn.weathergis.automation.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.weathergis.weather.domain.WeatherRecordEntity;

/** JpaRepository ghi chuỗi thời gian khí tượng — chỉ Ingestion Engine ghi trong Tuần 2. */
@Repository
public interface WeatherRecordRepository extends JpaRepository<WeatherRecordEntity, Long> {

    long countByLocationId(UUID locationId); // phục vụ kiểm đếm TC-05
}
