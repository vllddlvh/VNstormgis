package vn.weathergis.subscription.service;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import vn.weathergis.alert.dto.AlertFactualSnapshot;
import vn.weathergis.alert.dto.AlertFactualSnapshot.ViolationSnapshot;
import vn.weathergis.alert.event.AlertPublishedEvent;
import vn.weathergis.location.dto.SpatialPointContextResponse;
import vn.weathergis.subscription.config.SubscriptionConfig.SubscriptionMailProperties;
import vn.weathergis.subscription.domain.EmailSubscriptionEntity;

/**
 * Dịch vụ kết xuất và gửi thông báo Email chuẩn thiết kế San Rita (UTF-8 HTML + Plain Text). Hỗ trợ
 * cả SMTP thực tế (Gmail, Resend, AWS SES) lẫn Mailpit local container, đồng thời tự động chuyển
 * sang chế độ {@code SIMULATED} an toàn nếu tắt cờ mail hoặc máy chủ SMTP tạm thời chưa bật trong
 * môi trường phát triển.
 */
@Service
public class EmailDispatchService {

    private static final Logger log = LoggerFactory.getLogger(EmailDispatchService.class);
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VN_TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy 'UTC+7'");

    public record DispatchOutcome(String subject, String deliveryStatus, String errorMessage) {}

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final SubscriptionMailProperties properties;

    @Value("${spring.mail.username:}")
    private String smtpUsername = "";

    public EmailDispatchService(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            SubscriptionMailProperties properties) {
        this.mailSenderProvider = mailSenderProvider;
        this.properties = properties;
    }

    public DispatchOutcome sendVerificationEmail(
            EmailSubscriptionEntity subscription, String rawOtp, String rawVerifyToken) {
        String subject =
                "[VNstormgis] Mã xác thực "
                        + rawOtp
                        + " — Đăng ký cảnh báo khu vực "
                        + subscription.getLabel();

        String webVerifyUrl =
                trimTrailingSlash(properties.publicWebUrl())
                        + "/?sub_action=verify&token="
                        + urlEncode(rawVerifyToken);
        String apiVerifyUrl =
                trimTrailingSlash(properties.publicApiUrl())
                        + "/api/v1/subscriptions/verify?token="
                        + urlEncode(rawVerifyToken);

        String plainText =
                "VNSTORMGIS — XÁC THỰC ĐĂNG KÝ NHẬN CẢNH BÁO THIÊN TAI\n\n"
                        + "Khu vực theo dõi: "
                        + subscription.getLabel()
                        + " (bán kính "
                        + formatNum(subscription.getRadiusKm())
                        + " km)\n"
                        + "Mức cảnh báo tối thiểu: "
                        + subscription.getMinSeverity()
                        + "\n\n"
                        + "Mã xác thực OTP 6 chữ số của bạn: "
                        + rawOtp
                        + " (hiệu lực "
                        + properties.otpTtlMinutes()
                        + " phút)\n\n"
                        + "Hoặc nhấp liên kết xác thực 1-click trên WebGIS:\n"
                        + webVerifyUrl
                        + "\n\n"
                        + "Liên kết dự phòng trực tiếp:\n"
                        + apiVerifyUrl
                        + "\n\n"
                        + "Nếu bạn không thực hiện yêu cầu này, vui lòng bỏ qua email này.";

        String bodyHtml =
                "<p style=\"margin:0 0 12px;color:#dde2e4;\">Bạn vừa yêu cầu đăng ký nhận thông báo cảnh báo khí tượng - bão lũ tự động trên hệ thống <strong>VNstormgis</strong>:</p>"
                        + infoTable(
                                row("Khu vực giám sát", escapeHtml(subscription.getLabel())),
                                row(
                                        "Bán kính quét PostGIS",
                                        formatNum(subscription.getRadiusKm()) + " km"),
                                row(
                                        "Mức cảnh báo tối thiểu",
                                        escapeHtml(subscription.getMinSeverity())))
                        + "<div style=\"margin:18px 0;padding:16px;background:#1a2117;border:1px solid #e2ffcc;text-align:center;\">"
                        + "<div style=\"font-size:11px;color:#84907f;text-transform:uppercase;margin-bottom:6px;\">MÃ XÁC THỰC OTP (HIỆU LỰC "
                        + properties.otpTtlMinutes()
                        + " PHÚT)</div>"
                        + "<div style=\"font-size:28px;font-weight:700;letter-spacing:6px;color:#e2ffcc;\">"
                        + escapeHtml(rawOtp)
                        + "</div>"
                        + "</div>"
                        + "<p style=\"margin:14px 0;text-align:center;\">"
                        + "<a href=\""
                        + escapeHtml(webVerifyUrl)
                        + "\" style=\"display:inline-block;padding:10px 20px;background:#e2ffcc;color:#161b13;text-decoration:none;font-weight:700;text-transform:uppercase;font-size:12px;\">XÁC NHẬN KÍCH HOẠT NGAY</a>"
                        + "</p>"
                        + "<p style=\"margin:10px 0 0;font-size:11px;color:#84907f;\">Liên kết dự phòng: <a href=\""
                        + escapeHtml(apiVerifyUrl)
                        + "\" style=\"color:#e2ffcc;\">"
                        + escapeHtml(apiVerifyUrl)
                        + "</a></p>";

        return send(
                subscription.getEmail(),
                subject,
                plainText,
                wrapSanRitaTemplate("XÁC THỰC ĐĂNG KÝ CẢNH BÁO", bodyHtml, null));
    }

    public DispatchOutcome sendWelcomeEmail(EmailSubscriptionEntity subscription) {
        String subject = "[VNstormgis] Đã kích hoạt nhận cảnh báo: " + subscription.getLabel();
        String unsubUrl = buildUnsubscribeUrl(subscription.getUnsubscribeToken());

        String plainText =
                "VNSTORMGIS — ĐĂNG KÝ NHẬN CẢNH BÁO ĐÃ KÍCH HOẠT\n\n"
                        + "Hệ thống đã kích hoạt gửi cảnh báo tự động tới email của bạn.\n"
                        + "- Khu vực: "
                        + subscription.getLabel()
                        + "\n"
                        + "- Bán kính giám sát: "
                        + formatNum(subscription.getRadiusKm())
                        + " km\n"
                        + "- Mức độ tối thiểu: "
                        + subscription.getMinSeverity()
                        + "\n\n"
                        + "Hủy đăng ký bất cứ lúc nào (1-click): "
                        + unsubUrl;

        String bodyHtml =
                "<p style=\"margin:0 0 12px;color:#dde2e4;\">Đăng ký nhận thông báo cảnh báo thiên tai của bạn đã được <strong style=\"color:#e2ffcc;\">KÍCH HOẠT THÀNH CÔNG</strong>.</p>"
                        + infoTable(
                                row("Khu vực giám sát", escapeHtml(subscription.getLabel())),
                                row(
                                        "Tọa độ tâm (WGS84)",
                                        formatCoord(
                                                subscription.getTargetGeom().getY(),
                                                subscription.getTargetGeom().getX())),
                                row(
                                        "Bán kính cảnh báo",
                                        formatNum(subscription.getRadiusKm()) + " km"),
                                row(
                                        "Ngưỡng mức độ nhận tin",
                                        escapeHtml(subscription.getMinSeverity()) + " trở lên"))
                        + "<p style=\"margin:12px 0 0;color:#84907f;font-size:12px;\">Ngay khi trạm quan trắc hoặc quỹ đạo bão vượt ngưỡng an toàn trong vùng đăng ký, hệ thống sẽ tự động gửi bản tin đối chứng số liệu thực tế tới hộp thư của bạn.</p>";

        return send(
                subscription.getEmail(),
                subject,
                plainText,
                wrapSanRitaTemplate("ĐĂNG KÝ ĐÃ KÍCH HOẠT", bodyHtml, unsubUrl));
    }

    public DispatchOutcome sendTestConnectionEmail(
            EmailSubscriptionEntity subscription, SpatialPointContextResponse spatialContext) {
        String subject =
                "[VNstormgis] Bản tin kiểm tra kết nối — Số liệu thực tế khu vực "
                        + subscription.getLabel();
        String unsubUrl = buildUnsubscribeUrl(subscription.getUnsubscribeToken());

        String stationLine =
                spatialContext.nearestStation() != null
                        ? spatialContext.nearestStation().name()
                                + " ("
                                + spatialContext.nearestStation().code()
                                + " — cách "
                                + formatNum(spatialContext.nearestStation().distanceKm())
                                + " km)"
                        : "Chưa có dữ liệu trạm";

        String weatherLine;
        if (spatialContext.currentWeather() != null) {
            var w = spatialContext.currentWeather();
            weatherLine =
                    "Nhiệt độ: "
                            + formatNullable(w.temperature(), "°C")
                            + " | Mưa 1h: "
                            + formatNullable(w.rain1h(), "mm")
                            + " | Gió: "
                            + formatNullable(w.windSpeed(), "m/s")
                            + " | Độ ẩm: "
                            + formatNullable(w.humidity(), "%");
        } else {
            weatherLine = "Chưa có bản ghi quan trắc mới tại trạm gần nhất";
        }

        String stormLine =
                spatialContext.nearestStorm() != null
                        ? spatialContext.nearestStorm().name()
                                + " ("
                                + spatialContext.nearestStorm().category()
                                + ") — cách "
                                + formatNum(spatialContext.nearestStorm().distanceKm())
                                + " km"
                        : "Hiện không có xoáy thuận nhiệt đới đang hoạt động";

        String plainText =
                "VNSTORMGIS — BẢN TIN KIỂM TRA KẾT NỐI EMAIL (DỮ LIỆU THỰC TẾ POSTGIS)\n\n"
                        + "Khu vực đăng ký: "
                        + subscription.getLabel()
                        + "\n"
                        + "Trạm quan trắc gần nhất: "
                        + stationLine
                        + "\n"
                        + "Quan trắc hiện tại: "
                        + weatherLine
                        + "\n"
                        + "Tình trạng bão gần nhất: "
                        + stormLine
                        + "\n"
                        + "Cảnh báo đang hiệu lực quanh vị trí: "
                        + spatialContext.nearbyAlerts().size()
                        + " cảnh báo\n\n"
                        + "Hủy đăng ký 1-click: "
                        + unsubUrl;

        String bodyHtml =
                "<p style=\"margin:0 0 12px;color:#dde2e4;\">Đây là bản tin kiểm tra kết nối từ <strong>VNstormgis</strong>, trích xuất trực tiếp từ cơ sở dữ liệu không gian PostGIS tại thời điểm gửi (tuân thủ nguyên tắc chống bịa đặt số liệu <code>SYS-RULE-004</code>):</p>"
                        + infoTable(
                                row("Khu vực đăng ký", escapeHtml(subscription.getLabel())),
                                row("Trạm PostGIS gần nhất", escapeHtml(stationLine)),
                                row("Số liệu khí tượng thực đo", escapeHtml(weatherLine)),
                                row("Xoáy thuận nhiệt đới", escapeHtml(stormLine)),
                                row(
                                        "Cảnh báo active lân cận",
                                        spatialContext.nearbyAlerts().size() + " bản tin"))
                        + "<p style=\"margin:12px 0 0;font-size:12px;color:#e2ffcc;\">Kết nối hộp thư hoạt động tốt. Bạn sẽ tự động nhận thông báo khi phát sinh cảnh báo mới.</p>";

        return send(
                subscription.getEmail(),
                subject,
                plainText,
                wrapSanRitaTemplate("BẢN TIN KIỂM TRA KẾT NỐI", bodyHtml, unsubUrl));
    }

    public DispatchOutcome sendProactiveAlertEmail(
            EmailSubscriptionEntity subscription, AlertPublishedEvent event) {
        String subject = "[VNstormgis][" + event.severityLevel() + "] " + event.title();
        String unsubUrl = buildUnsubscribeUrl(subscription.getUnsubscribeToken());
        String webMapUrl = trimTrailingSlash(properties.publicWebUrl());

        String timeText =
                event.triggeredAt() != null
                        ? event.triggeredAt().atZoneSameInstant(VN_ZONE).format(VN_TIME_FMT)
                        : "Không xác định";

        StringBuilder violationsText = new StringBuilder();
        StringBuilder violationsHtml = new StringBuilder();
        AlertFactualSnapshot snapshot = event.factualSnapshot();
        if (snapshot != null && snapshot.violations() != null && !snapshot.violations().isEmpty()) {
            for (ViolationSnapshot v : snapshot.violations()) {
                String label = parameterLabel(v.parameter());
                violationsText
                        .append("  • ")
                        .append(label)
                        .append(": ")
                        .append(formatNum(v.measuredValue()))
                        .append(" ")
                        .append(v.unit())
                        .append(" (ngưỡng ")
                        .append(v.operator())
                        .append(" ")
                        .append(formatNum(v.thresholdValue()))
                        .append(" ")
                        .append(v.unit())
                        .append(")\n");
                violationsHtml.append(
                        row(
                                escapeHtml(label),
                                "<strong>"
                                        + formatNum(v.measuredValue())
                                        + " "
                                        + escapeHtml(v.unit())
                                        + "</strong> (vượt ngưỡng "
                                        + escapeHtml(v.operator())
                                        + " "
                                        + formatNum(v.thresholdValue())
                                        + " "
                                        + escapeHtml(v.unit())
                                        + ")"));
            }
        }

        String locationDisplay =
                snapshot != null && snapshot.locationName() != null
                        ? snapshot.locationName() + " (" + snapshot.locationCode() + ")"
                        : formatCoord(event.latitude(), event.longitude());

        String plainText =
                "CẢNH BÁO KHÍ TƯỢNG - BÃO LŨ VNSTORMGIS\n"
                        + "========================================\n"
                        + "Tiêu đề: "
                        + event.title()
                        + "\n"
                        + "Mức độ: "
                        + event.severityLevel()
                        + "\n"
                        + "Khu vực phát cảnh báo: "
                        + locationDisplay
                        + "\n"
                        + "Thời điểm ghi nhận: "
                        + timeText
                        + "\n\n"
                        + "Chỉ số vượt ngưỡng thực đo:\n"
                        + violationsText
                        + "\nNội dung cảnh báo:\n"
                        + event.aiExplanation()
                        + "\n\nXem bản đồ trực tiếp: "
                        + webMapUrl
                        + "\nHủy đăng ký nhận tin (1-click): "
                        + unsubUrl;

        String severityColor = severityHex(event.severityLevel());
        String bodyHtml =
                "<div style=\"margin:0 0 14px;padding:12px;border-left:4px solid "
                        + severityColor
                        + ";background:#1a2117;\">"
                        + "<div style=\"font-size:11px;font-weight:700;color:"
                        + severityColor
                        + ";text-transform:uppercase;\">MỨC ĐỘ: "
                        + escapeHtml(event.severityLevel())
                        + "</div>"
                        + "<div style=\"font-size:16px;font-weight:700;color:#dde2e4;margin-top:4px;\">"
                        + escapeHtml(event.title())
                        + "</div>"
                        + "</div>"
                        + infoTable(
                                row("Điểm cảnh báo", escapeHtml(locationDisplay)),
                                row("Thời điểm phát hiện", escapeHtml(timeText)),
                                row("Đăng ký khớp vùng", escapeHtml(subscription.getLabel())),
                                violationsHtml.toString())
                        + "<div style=\"margin:14px 0;padding:12px;border:1px solid #2d3329;background:#161b13;color:#dde2e4;line-height:1.5;\">"
                        + escapeHtml(event.aiExplanation())
                        + "</div>"
                        + "<p style=\"margin:14px 0;text-align:center;\">"
                        + "<a href=\""
                        + escapeHtml(webMapUrl)
                        + "\" style=\"display:inline-block;padding:10px 20px;background:#e2ffcc;color:#161b13;text-decoration:none;font-weight:700;text-transform:uppercase;font-size:12px;\">MỞ BẢN ĐỒ GIÁM SÁT TRỰC TUYẾN</a>"
                        + "</p>";

        return send(
                subscription.getEmail(),
                subject,
                plainText,
                wrapSanRitaTemplate("CẢNH BÁO THIÊN TAI KHẨN CẤP", bodyHtml, unsubUrl));
    }

    private DispatchOutcome send(
            String toEmail, String subject, String plainText, String htmlContent) {
        if (!properties.enabled()) {
            log.info(
                    "[MAIL-OUTBOX] Chế độ giả lập (MAIL_ENABLED=false) gửi tới {}: {}",
                    SubscriptionTokenCodec.maskEmail(toEmail),
                    subject);
            return new DispatchOutcome(subject, "SIMULATED", null);
        }

        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            log.warn(
                    "[MAIL-OUTBOX] Không có JavaMailSender khả dụng, chuyển sang SIMULATED cho {}",
                    SubscriptionTokenCodec.maskEmail(toEmail));
            return new DispatchOutcome(subject, "SIMULATED", "JavaMailSender unavailable");
        }

        try {
            String effectiveFrom = properties.from();
            if ("no-reply@weathergis.vn".equalsIgnoreCase(effectiveFrom)
                    && smtpUsername != null
                    && smtpUsername.contains("@")) {
                effectiveFrom = smtpUsername.trim();
            }
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(effectiveFrom, properties.fromName(), "UTF-8"));
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(plainText, htmlContent);
            sender.send(message);
            log.info(
                    "[MAIL-OUTBOX] Đã gửi email SMTP thành công tới {}: {}",
                    SubscriptionTokenCodec.maskEmail(toEmail),
                    subject);
            return new DispatchOutcome(subject, "SENT", null);
        } catch (Exception ex) {
            log.warn(
                    "[MAIL-OUTBOX] Không kết nối được máy chủ SMTP ({}), lưu trạng thái SIMULATED: {}",
                    ex.getClass().getSimpleName(),
                    ex.getMessage());
            return new DispatchOutcome(
                    subject,
                    "SIMULATED",
                    ex.getClass().getSimpleName() + ": " + safeTruncate(ex.getMessage(), 240));
        }
    }

    private String buildUnsubscribeUrl(String unsubscribeToken) {
        return trimTrailingSlash(properties.publicWebUrl())
                + "/?sub_action=unsubscribe&token="
                + urlEncode(unsubscribeToken);
    }

    private String wrapSanRitaTemplate(String badge, String innerHtml, String unsubscribeUrl) {
        String unsubBlock = "";
        if (unsubscribeUrl != null && !unsubscribeUrl.isBlank()) {
            String directApiUnsub =
                    trimTrailingSlash(properties.publicApiUrl())
                            + "/api/v1/subscriptions/unsubscribe?token="
                            + unsubscribeUrl.substring(unsubscribeUrl.indexOf("token=") + 6);
            unsubBlock =
                    "<p style=\"margin:8px 0 0;font-size:11px;color:#84907f;\">"
                            + "Bạn nhận email này do đã đăng ký cảnh báo trên VNstormgis. "
                            + "<a href=\""
                            + escapeHtml(unsubscribeUrl)
                            + "\" style=\"color:#e2ffcc;\">Hủy đăng ký nhận tin (1-click)</a> · "
                            + "<a href=\""
                            + escapeHtml(directApiUnsub)
                            + "\" style=\"color:#84907f;\">Liên kết hủy trực tiếp</a>"
                            + "</p>";
        }

        return "<!DOCTYPE html><html lang=\"vi\"><body style=\"margin:0;padding:20px;background:#0d100b;font-family:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;color:#dde2e4;\">"
                + "<div style=\"max-width:620px;margin:0 auto;background:#161b13;border:1px solid #2d3329;padding:24px;\">"
                + "<div style=\"border-bottom:1px solid #2d3329;padding-bottom:12px;margin-bottom:16px;display:flex;justify-content:space-between;align-items:center;\">"
                + "<span style=\"font-size:18px;font-weight:700;color:#e2ffcc;letter-spacing:1px;\">VNSTORMGIS</span>"
                + "<span style=\"font-size:10px;padding:3px 8px;border:1px solid #2d3329;background:#1a2117;color:#84907f;\">"
                + escapeHtml(badge)
                + "</span>"
                + "</div>"
                + innerHtml
                + "<div style=\"border-top:1px solid #2d3329;margin-top:20px;padding-top:12px;font-size:11px;color:#84907f;\">"
                + "<div>Hệ thống WebGIS Giám sát Thời tiết &amp; Cảnh báo Bão lũ Việt Nam (Dữ liệu tham khảo học thuật; vui lòng theo dõi thêm bản tin chính thức từ NCHMF).</div>"
                + unsubBlock
                + "</div>"
                + "</div></body></html>";
    }

    private static String infoTable(String... rows) {
        StringBuilder sb =
                new StringBuilder(
                        "<table style=\"width:100%;border-collapse:collapse;margin:12px 0;font-size:12px;\">");
        for (String r : rows) {
            if (r != null && !r.isBlank()) {
                sb.append(r);
            }
        }
        sb.append("</table>");
        return sb.toString();
    }

    private static String row(String key, String valueHtml) {
        return "<tr>"
                + "<td style=\"padding:6px 8px;border:1px solid #2d3329;color:#84907f;width:38%;\">"
                + key
                + "</td>"
                + "<td style=\"padding:6px 8px;border:1px solid #2d3329;color:#dde2e4;\">"
                + valueHtml
                + "</td>"
                + "</tr>";
    }

    private static String parameterLabel(String parameter) {
        if (parameter == null) {
            return "Chỉ số";
        }
        return switch (parameter) {
            case "rain_1h" -> "Lượng mưa 1 giờ";
            case "rain_3h" -> "Lượng mưa 3 giờ";
            case "wind_speed" -> "Tốc độ gió";
            case "wind_gust" -> "Gió giật";
            case "storm_dist" -> "Khoảng cách tâm bão";
            default -> parameter;
        };
    }

    private static String severityHex(String severity) {
        if (severity == null) {
            return "#e2ffcc";
        }
        return switch (severity.toUpperCase(Locale.ROOT)) {
            case "CRITICAL" -> "#dc2626";
            case "DANGER" -> "#f97316";
            case "WARNING" -> "#f59e0b";
            default -> "#38bdf8";
        };
    }

    private static String formatNum(Double val) {
        if (val == null) {
            return "—";
        }
        return String.format(Locale.ROOT, "%.1f", val);
    }

    private static String formatNullable(Double val, String unit) {
        if (val == null) {
            return "chưa có số liệu";
        }
        return String.format(Locale.ROOT, "%.1f %s", val, unit);
    }

    private static String formatCoord(double lat, double lng) {
        return String.format(Locale.ROOT, "%.4f°N, %.4f°E", lat, lng);
    }

    private static String trimTrailingSlash(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String urlEncode(String val) {
        return URLEncoder.encode(val, StandardCharsets.UTF_8);
    }

    private static String safeTruncate(String s, int maxLen) {
        if (s == null) {
            return "";
        }
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }

    public static String escapeHtml(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
