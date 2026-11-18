package vn.weathergis.subscription.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.weathergis.subscription.domain.EmailSubscriptionEntity;

@Repository
public interface EmailSubscriptionRepository extends JpaRepository<EmailSubscriptionEntity, UUID> {

    Optional<EmailSubscriptionEntity> findByVerifyTokenHash(String verifyTokenHash);

    Optional<EmailSubscriptionEntity> findByUnsubscribeToken(String unsubscribeToken);

    Optional<EmailSubscriptionEntity>
            findFirstByEmailAndScopeTypeAndLocationIdAndStatusInOrderByCreatedAtDesc(
                    String email, String scopeType, UUID locationId, List<String> statuses);

    /**
     * Truy vấn không gian PostGIS tìm toàn bộ các đăng ký đang hoạt động (ACTIVE) khớp với cảnh báo
     * vừa phát sinh theo: (1) cùng location_id hoặc khoảng cách mặt cầu ST_DWithin <= radius_km, và
     * (2) mức độ nghiêm trọng của cảnh báo >= min_severity của người đăng ký.
     */
    @Query(
            value =
                    """
            SELECT s.*
            FROM email_subscriptions s
            WHERE s.status = 'ACTIVE'
              AND (
                  (:locationId IS NOT NULL AND s.location_id = :locationId)
                  OR ST_DWithin(
                      s.target_geom::geography,
                      ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
                      s.radius_km * 1000.0
                  )
              )
              AND (
                  CASE s.min_severity
                      WHEN 'INFO' THEN 1
                      WHEN 'WARNING' THEN 2
                      WHEN 'DANGER' THEN 3
                      WHEN 'CRITICAL' THEN 4
                      ELSE 2
                  END
              ) <= :severityRank
            ORDER BY s.created_at ASC
            """,
            nativeQuery = true)
    List<EmailSubscriptionEntity> findMatchingActiveSubscriptions(
            @Param("lng") double lng,
            @Param("lat") double lat,
            @Param("locationId") UUID locationId,
            @Param("severityRank") int severityRank);
}
