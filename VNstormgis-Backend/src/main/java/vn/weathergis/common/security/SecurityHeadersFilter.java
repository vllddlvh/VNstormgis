package vn.weathergis.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Bộ lọc gắn các HTTP Security Headers chuẩn OWASP cho mọi phản hồi từ Backend (Task W6-06). Bảo
 * đảm phòng thủ chiều sâu (Defense-in-Depth) ngay tại tầng ứng dụng Spring Boot, đồng bộ với cấu
 * hình Nginx Reverse Proxy.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    public static final String HEADER_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";
    public static final String HEADER_FRAME_OPTIONS = "X-Frame-Options";
    public static final String HEADER_REFERRER_POLICY = "Referrer-Policy";
    public static final String HEADER_PERMISSIONS_POLICY = "Permissions-Policy";
    public static final String HEADER_CSP = "Content-Security-Policy";

    public static final String API_CSP_VALUE =
            "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'";
    public static final String SWAGGER_CSP_VALUE =
            "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; frame-ancestors 'none'";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        setIfAbsent(response, HEADER_CONTENT_TYPE_OPTIONS, "nosniff");
        setIfAbsent(response, HEADER_FRAME_OPTIONS, "DENY");
        setIfAbsent(response, HEADER_REFERRER_POLICY, "strict-origin-when-cross-origin");
        setIfAbsent(
                response, HEADER_PERMISSIONS_POLICY, "geolocation=(), microphone=(), camera=()");

        String uri = request.getRequestURI();
        if (uri != null && (uri.startsWith("/swagger-ui") || uri.startsWith("/v3/api-docs"))) {
            setIfAbsent(response, HEADER_CSP, SWAGGER_CSP_VALUE);
        } else {
            setIfAbsent(response, HEADER_CSP, API_CSP_VALUE);
        }

        filterChain.doFilter(request, response);
    }

    private static void setIfAbsent(HttpServletResponse response, String name, String value) {
        if (!response.containsHeader(name)) {
            response.setHeader(name, value);
        }
    }
}
