package vn.weathergis.subscription.repository;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.weathergis.subscription.domain.EmailNotificationLogEntity;

@Repository
public interface EmailNotificationLogRepository
        extends JpaRepository<EmailNotificationLogEntity, Long> {

    @Query(
            """
            SELECT COUNT(l) FROM EmailNotificationLogEntity l
            WHERE l.recipientEmail = :email
              AND l.notificationType = 'VERIFICATION'
              AND l.sentAt >= :since
            """)
    long countRecentVerificationEmails(
            @Param("email") String email, @Param("since") OffsetDateTime since);

    boolean existsBySubscriptionIdAndAlertId(UUID subscriptionId, UUID alertId);

    /**
     * Ghi nhận lũy đẳng thông báo cảnh báo theo cặp (subscription_id, alert_id). Trả về 1 nếu vừa
     * giành quyền gửi lần đầu, 0 nếu cảnh báo này đã được gửi cho đăng ký đó.
     */
    @Modifying
    @Query(
            value =
                    """
            INSERT INTO email_notification_logs (
                subscription_id, alert_id, notification_type, recipient_email,
                subject, delivery_status, error_message, sent_at
            ) VALUES (
                :subscriptionId, :alertId, 'PROACTIVE_ALERT', :recipientEmail,
                :subject, :deliveryStatus, :errorMessage, CURRENT_TIMESTAMP
            )
            ON CONFLICT (subscription_id, alert_id) DO NOTHING
            """,
            nativeQuery = true)
    int insertAlertNotificationIdempotent(
            @Param("subscriptionId") UUID subscriptionId,
            @Param("alertId") UUID alertId,
            @Param("recipientEmail") String recipientEmail,
            @Param("subject") String subject,
            @Param("deliveryStatus") String deliveryStatus,
            @Param("errorMessage") String errorMessage);
}
