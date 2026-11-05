package vn.weathergis.alert.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.server.RequestPath;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import vn.weathergis.alert.exception.AdminUnauthorizedException;

/** Protects writes to threshold configuration while leaving public reads accessible. */
@Component
public class AdminApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Admin-Api-Key";
    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final PathPattern THRESHOLD_PATH =
            PathPatternParser.defaultInstance.parse("/api/v1/alert-thresholds/**");

    private final byte[] expectedKey;
    private final HandlerExceptionResolver exceptionResolver;

    public AdminApiKeyFilter(
            @Value("${weathergis.admin.api-key:}") String expectedKey,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        this.expectedKey =
                expectedKey == null || expectedKey.isBlank()
                        ? new byte[0]
                        : expectedKey.getBytes(StandardCharsets.UTF_8);
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!WRITE_METHODS.contains(request.getMethod())) {
            return true;
        }
        // Match the decoded segments and matrix parameters exactly as Spring MVC does.
        RequestPath requestPath =
                RequestPath.parse(request.getRequestURI(), request.getContextPath());
        return !THRESHOLD_PATH.matches(requestPath.pathWithinApplication());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String providedKey = request.getHeader(HEADER_NAME);
        if (expectedKey.length == 0
                || providedKey == null
                || !MessageDigest.isEqual(
                        expectedKey, providedKey.getBytes(StandardCharsets.UTF_8))) {
            exceptionResolver.resolveException(
                    request, response, null, new AdminUnauthorizedException());
            return;
        }
        filterChain.doFilter(request, response);
    }
}
