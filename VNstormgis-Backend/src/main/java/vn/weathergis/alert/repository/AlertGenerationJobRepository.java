package vn.weathergis.alert.repository;

import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.weathergis.alert.domain.AlertGenerationJobEntity;
import vn.weathergis.alert.domain.AlertJobStatus;

public interface AlertGenerationJobRepository
        extends JpaRepository<AlertGenerationJobEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from AlertGenerationJobEntity j where j.id = :id")
    Optional<AlertGenerationJobEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query(
            value = "select * from alert_generation_jobs where id = :id for update skip locked",
            nativeQuery = true)
    Optional<AlertGenerationJobEntity> findByIdForUpdateSkipLocked(@Param("id") UUID id);

    @Query(
            """
            select j.id from AlertGenerationJobEntity j
            where j.status = vn.weathergis.alert.domain.AlertJobStatus.READY
              and (j.nextAttemptAt is null or j.nextAttemptAt <= :now)
            order by j.nextAttemptAt asc nulls first, j.createdAt asc, j.id asc
            """)
    List<UUID> findDueReadyJobIds(@Param("now") OffsetDateTime now, Pageable pageable);

    @Query(
            """
            select j from AlertGenerationJobEntity j
            where j.status = :status
            order by j.createdAt asc, j.id asc
            """)
    List<AlertGenerationJobEntity> findByStatusOrderByCreatedAtAscIdAsc(
            @Param("status") AlertJobStatus status);

    @Query(value = "select pg_advisory_xact_lock(:lockKey)", nativeQuery = true)
    void acquireStationLock(@Param("lockKey") long lockKey);

    @Query(
            value =
                    "select exists(select 1 from alert_generation_jobs where id = :id and status = 'PENDING' and claim_token = :token and lease_until > clock_timestamp())",
            nativeQuery = true)
    boolean hasLiveClaim(@Param("id") UUID id, @Param("token") UUID token);
}
