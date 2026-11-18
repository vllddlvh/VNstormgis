package vn.weathergis.alert.event;

import java.time.OffsetDateTime;
import java.util.UUID;
import vn.weathergis.alert.dto.AlertFactualSnapshot;

/**
 * Sự kiện miền phát đi ngay khi một cảnh báo mới được xuất bản thành công sang trạng thái {@code
 * active} trong bảng {@code alerts}. Các phân hệ phụ trợ (ví dụ: gửi thông báo Email cho người đăng
 * ký) lắng nghe ở pha {@code AFTER_COMMIT} để không bao giờ làm chậm hoặc rollback giao dịch xuất
 * bản cảnh báo chính.
 */
public record AlertPublishedEvent(
        UUID alertId,
        UUID locationId,
        String stormId,
        String title,
        String severityLevel,
        String triggerReason,
        String aiExplanation,
        boolean isFallback,
        double longitude,
        double latitude,
        OffsetDateTime triggeredAt,
        AlertFactualSnapshot factualSnapshot) {}
