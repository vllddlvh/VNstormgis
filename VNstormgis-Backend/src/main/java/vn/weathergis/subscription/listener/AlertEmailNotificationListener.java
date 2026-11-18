package vn.weathergis.subscription.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import vn.weathergis.alert.event.AlertPublishedEvent;
import vn.weathergis.subscription.service.EmailSubscriptionService;

/**
 * Lắng nghe sự kiện cảnh báo mới xuất bản ở pha {@code AFTER_COMMIT} để đối chiếu không gian
 * PostGIS và gửi thông báo email cho các người đăng ký phù hợp. Bọc an toàn mọi ngoại lệ để tuyệt
 * đối không ảnh hưởng đến luồng xuất bản cảnh báo chính.
 */
@Component
public class AlertEmailNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(AlertEmailNotificationListener.class);

    private final EmailSubscriptionService subscriptionService;

    public AlertEmailNotificationListener(EmailSubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAlertPublished(AlertPublishedEvent event) {
        try {
            int count = subscriptionService.dispatchForPublishedAlert(event);
            if (count > 0) {
                log.info(
                        "Đã phát thông báo email cảnh báo alertId={} tới {} người đăng ký",
                        event.alertId(),
                        count);
            }
        } catch (RuntimeException ex) {
            log.error(
                    "Lỗi không mong muốn trong AlertEmailNotificationListener cho alertId={}: {}",
                    event != null ? event.alertId() : null,
                    ex.getMessage());
        }
    }
}
