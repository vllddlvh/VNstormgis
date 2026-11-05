package vn.weathergis.alert.repository;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.weathergis.alert.domain.AlertViolationKeyEntity;

public interface AlertViolationKeyRepository
        extends JpaRepository<AlertViolationKeyEntity, String> {

    @Query(
            "select k.violationKey from AlertViolationKeyEntity k where k.violationKey in :violationKeys")
    Set<String> findExistingKeys(@Param("violationKeys") Collection<String> violationKeys);

    @Query(
            """
            select k from AlertViolationKeyEntity k
            where k.jobId = :jobId
            order by k.violationKey asc
            """)
    List<AlertViolationKeyEntity> findByJobIdOrderByViolationKeyAsc(@Param("jobId") UUID jobId);

    @Modifying(flushAutomatically = true)
    @Query(
            """
            update AlertViolationKeyEntity k
            set k.alertId = :alertId
            where k.jobId = :jobId
              and (k.alertId is null or k.alertId = :alertId)
            """)
    int linkKeysToAlert(@Param("jobId") UUID jobId, @Param("alertId") UUID alertId);
}
