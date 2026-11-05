package vn.weathergis.alert.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerExceptionResolver;
import vn.weathergis.alert.exception.AdminUnauthorizedException;

class AdminApiKeyFilterTest {

    private static final String TEST_KEY = "unit-test-admin-credential";
    private final HandlerExceptionResolver resolver = mock(HandlerExceptionResolver.class);
    private final FilterChain chain = mock(FilterChain.class);
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    void validKeyAllowsEachWriteMethod(String method) throws Exception {
        MockHttpServletRequest request = request(method, "/api/v1/alert-thresholds/7");
        request.addHeader(AdminApiKeyFilter.HEADER_NAME, TEST_KEY);

        new AdminApiKeyFilter(TEST_KEY, resolver).doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(resolver);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    void missingKeyRejectsEachWriteMethod(String method) throws Exception {
        assertUnauthorized(
                new AdminApiKeyFilter(TEST_KEY, resolver),
                request(method, "/api/v1/alert-thresholds/7"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "wrong",
                "",
                "unit-test-admin-credentia",
                "unit-test-admin-credential-extra"
            })
    void incorrectKeyIsRejected(String key) throws Exception {
        MockHttpServletRequest request = request("POST", "/api/v1/alert-thresholds");
        request.addHeader(AdminApiKeyFilter.HEADER_NAME, key);

        assertUnauthorized(new AdminApiKeyFilter(TEST_KEY, resolver), request);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void unconfiguredKeyFailsClosed(String configuredKey) throws Exception {
        MockHttpServletRequest request = request("POST", "/api/v1/alert-thresholds");
        request.addHeader(AdminApiKeyFilter.HEADER_NAME, TEST_KEY);

        assertUnauthorized(new AdminApiKeyFilter(configuredKey, resolver), request);
    }

    @ParameterizedTest
    @CsvSource({
        "GET,/api/v1/alert-thresholds",
        "GET,/api/v1/alert-thresholds/7",
        "HEAD,/api/v1/alert-thresholds",
        "OPTIONS,/api/v1/alert-thresholds",
        "POST,/api/v1/internal/sync/weather",
        "DELETE,/api/v1/alert-thresholds-other/7"
    })
    void publicReadsAndUnrelatedPathsRemainAccessible(String method, String path) throws Exception {
        MockHttpServletRequest request = request(method, path);

        new AdminApiKeyFilter("", resolver).doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(resolver);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/api/v1/alert-thresholds",
                "/api/v1/alert-thresholds/",
                "/api/v1/alert-thresholds/7/toggle",
                "/api/v1/alert-thresholds;version=1",
                "/api/v1;version=1/alert-thresholds/7",
                "/api/v1/%61lert-thresholds/7"
            })
    void allMappedThresholdPathsRequireAuthentication(String path) throws Exception {
        assertUnauthorized(new AdminApiKeyFilter(TEST_KEY, resolver), request("POST", path));
    }

    @Test
    void contextPathDoesNotBypassAuthentication() throws Exception {
        MockHttpServletRequest request = request("PUT", "/weathergis/api/v1/alert-thresholds/7");
        request.setContextPath("/weathergis");

        assertUnauthorized(new AdminApiKeyFilter(TEST_KEY, resolver), request);
    }

    private MockHttpServletRequest request(String method, String path) {
        return new MockHttpServletRequest(method, path);
    }

    private void assertUnauthorized(AdminApiKeyFilter filter, MockHttpServletRequest request)
            throws Exception {
        filter.doFilter(request, response, chain);

        verifyNoInteractions(chain);
        verify(resolver)
                .resolveException(
                        eq(request), eq(response), isNull(), any(AdminUnauthorizedException.class));
    }
}
