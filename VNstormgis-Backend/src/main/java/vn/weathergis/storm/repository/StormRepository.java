package vn.weathergis.storm.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.weathergis.storm.domain.StormEntity;

/** JpaRepository cho bảng storms — Ingestion ghi (W2-05), Tuần 3 Storm Layer đọc. */
@Repository
public interface StormRepository extends JpaRepository<StormEntity, String> {

    /**
     * Danh sách bão theo trạng thái — đầu vào nhánh dissipated SYS-RULE-001 và lớp bản đồ Tuần 3.
     */
    List<StormEntity> findByStatusIgnoreCase(String status);
}
