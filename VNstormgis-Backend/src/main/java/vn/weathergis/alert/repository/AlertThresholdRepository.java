package vn.weathergis.alert.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.weathergis.alert.domain.AlertThresholdEntity;

public interface AlertThresholdRepository extends JpaRepository<AlertThresholdEntity, Integer> {

    @Query(
            "select t from AlertThresholdEntity t where "
                    + "(:parameter is null or t.parameter = :parameter) and "
                    + "(:isEnabled is null or t.isEnabled = :isEnabled) order by t.id")
    List<AlertThresholdEntity> findByFilters(
            @Param("parameter") String parameter, @Param("isEnabled") Boolean isEnabled);

    /** Serialize writes so each concurrent toggle flips the latest committed value once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from AlertThresholdEntity t where t.id = :id")
    Optional<AlertThresholdEntity> findByIdForUpdate(@Param("id") Integer id);
}
