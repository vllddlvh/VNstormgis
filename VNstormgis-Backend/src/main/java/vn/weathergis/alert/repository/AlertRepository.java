package vn.weathergis.alert.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.weathergis.alert.domain.AlertEntity;

public interface AlertRepository extends JpaRepository<AlertEntity, UUID> {

    @Query(
            """
            select a from AlertEntity a
            where a.status = :status
            order by a.triggeredAt desc, a.id asc
            """)
    List<AlertEntity> findByStatusOrderByTriggeredAtDescIdAsc(@Param("status") String status);
}
