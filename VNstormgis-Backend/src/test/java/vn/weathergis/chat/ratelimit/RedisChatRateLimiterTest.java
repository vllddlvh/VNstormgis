package vn.weathergis.chat.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.distributed.proxy.RemoteBucketBuilder;
import io.lettuce.core.RedisCommandTimeoutException;
import io.lettuce.core.RedisException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import vn.weathergis.chat.config.ChatRateLimitProperties;
import vn.weathergis.chat.exception.ChatRateLimitedException;
import vn.weathergis.chat.exception.ChatRateLimiterUnavailableException;
import vn.weathergis.chat.port.ChatRateLimitKey;

@ExtendWith(OutputCaptureExtension.class)
class RedisChatRateLimiterTest {
    private static final ChatRateLimitKey KEY =
            new ChatRateLimitKey("a".repeat(64), "203.0.113.7", "forged.example");

    private ProxyManager<byte[]> manager;
    private RemoteBucketBuilder<byte[]> builder;
    private BucketProxy session;
    private BucketProxy ip;
    private SimpleMeterRegistry meters;
    private RedisChatRateLimiter limiter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        manager = mock(ProxyManager.class);
        builder = mock(RemoteBucketBuilder.class);
        session = mock(BucketProxy.class);
        ip = mock(BucketProxy.class);
        meters = new SimpleMeterRegistry();
        when(manager.builder()).thenReturn(builder);
        when(builder.build(any(byte[].class), any(java.util.function.Supplier.class)))
                .thenAnswer(
                        invocation ->
                                new String(invocation.getArgument(0), StandardCharsets.UTF_8)
                                                .contains(":ip:")
                                        ? ip
                                        : session);
        ChatRateLimitProperties properties = new ChatRateLimitProperties();
        limiter =
                new RedisChatRateLimiter(
                        () -> manager, new ChatClientIpResolver(properties), properties, meters);
    }

    @Test
    void allowedConsumesBothAndKeysUseVerifierAndUntrustedRemote(CapturedOutput output) {
        when(session.tryConsumeAndReturnRemaining(1)).thenReturn(ConsumptionProbe.consumed(9, 0));
        when(ip.tryConsumeAndReturnRemaining(1)).thenReturn(ConsumptionProbe.consumed(29, 0));
        limiter.acquire(KEY);

        ArgumentCaptor<byte[]> keys = ArgumentCaptor.forClass(byte[].class);
        verify(builder, org.mockito.Mockito.times(2))
                .build(keys.capture(), any(java.util.function.Supplier.class));
        assertThat(
                        keys.getAllValues().stream()
                                .map(bytes -> new String(bytes, StandardCharsets.UTF_8)))
                .containsExactly(
                        RedisChatRateLimiter.KEY_PREFIX + "s:" + KEY.tokenHash(),
                        RedisChatRateLimiter.KEY_PREFIX + "ip:203.0.113.7");
        assertThat(meters.get("weathergis.chat.ratelimit.allowed").counter().count()).isEqualTo(1);
        assertThat(output).doesNotContain(KEY.tokenHash(), "forged.example");
    }

    @Test
    void sessionRejectionDoesNotTouchIpAndRoundsRetryUp() {
        when(session.tryConsumeAndReturnRemaining(1))
                .thenReturn(ConsumptionProbe.rejected(0, 1_000_000_001L, 0));
        assertThatThrownBy(() -> limiter.acquire(KEY))
                .isInstanceOfSatisfying(
                        ChatRateLimitedException.class,
                        ex -> {
                            assertThat(ex.getScope()).isEqualTo("session");
                            assertThat(ex.getRetryAfterSeconds()).isEqualTo(2);
                        });
        org.mockito.Mockito.verifyNoInteractions(ip);
        assertThat(
                        meters.get("weathergis.chat.ratelimit.rejected")
                                .tag("scope", "session")
                                .counter()
                                .count())
                .isEqualTo(1);
    }

    @Test
    void ipRejectionRefundsSession() {
        when(session.tryConsumeAndReturnRemaining(1)).thenReturn(ConsumptionProbe.consumed(9, 0));
        when(ip.tryConsumeAndReturnRemaining(1))
                .thenReturn(ConsumptionProbe.rejected(0, 20_000_000_000L, 0));
        assertThatThrownBy(() -> limiter.acquire(KEY))
                .isInstanceOfSatisfying(
                        ChatRateLimitedException.class,
                        ex -> {
                            assertThat(ex.getScope()).isEqualTo("ip");
                            assertThat(ex.getRetryAfterSeconds()).isEqualTo(20);
                        });
        verify(session).addTokens(1);
        assertThat(
                        meters.get("weathergis.chat.ratelimit.rejected")
                                .tag("scope", "ip")
                                .counter()
                                .count())
                .isEqualTo(1);
    }

    @Test
    void redisConnectionAndTimeoutAreFailClosed() {
        when(session.tryConsumeAndReturnRemaining(1))
                .thenThrow(new RedisConnectionFailureException("offline"));
        assertThatThrownBy(() -> limiter.acquire(KEY))
                .isInstanceOf(ChatRateLimiterUnavailableException.class);
        assertThat(
                        meters.get("weathergis.chat.ratelimit.outage")
                                .tag("reason", "connection")
                                .counter()
                                .count())
                .isEqualTo(1);

        org.mockito.Mockito.doThrow(new RedisCommandTimeoutException("timeout"))
                .when(session)
                .tryConsumeAndReturnRemaining(1);
        assertThatThrownBy(() -> limiter.acquire(KEY))
                .isInstanceOf(ChatRateLimiterUnavailableException.class);
        assertThat(
                        meters.get("weathergis.chat.ratelimit.outage")
                                .tag("reason", "timeout")
                                .counter()
                                .count())
                .isEqualTo(1);

        org.mockito.Mockito.doThrow(new QueryTimeoutException("query timeout"))
                .when(session)
                .tryConsumeAndReturnRemaining(1);
        assertThatThrownBy(() -> limiter.acquire(KEY))
                .isInstanceOf(ChatRateLimiterUnavailableException.class);
        assertThat(
                        meters.get("weathergis.chat.ratelimit.outage")
                                .tag("reason", "timeout")
                                .counter()
                                .count())
                .isEqualTo(2);

        org.mockito.Mockito.doThrow(
                        new RedisException("wrapped", new RedisCommandTimeoutException("timeout")))
                .when(session)
                .tryConsumeAndReturnRemaining(1);
        assertThatThrownBy(() -> limiter.acquire(KEY))
                .isInstanceOf(ChatRateLimiterUnavailableException.class);
        assertThat(
                        meters.get("weathergis.chat.ratelimit.outage")
                                .tag("reason", "timeout")
                                .counter()
                                .count())
                .isEqualTo(3);
    }

    @Test
    void programmingErrorPropagatesInsteadOfMasqueradingAsOutage() {
        when(session.tryConsumeAndReturnRemaining(1)).thenThrow(new IllegalStateException("bug"));
        assertThatThrownBy(() -> limiter.acquire(KEY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("bug");
        assertThat(meters.find("weathergis.chat.ratelimit.outage").counter()).isNull();
    }

    @Test
    void refundFailureFailsClosed() {
        when(session.tryConsumeAndReturnRemaining(1)).thenReturn(ConsumptionProbe.consumed(9, 0));
        when(ip.tryConsumeAndReturnRemaining(1))
                .thenReturn(ConsumptionProbe.rejected(0, 1_000_000_000L, 0));
        org.mockito.Mockito.doThrow(new RedisConnectionFailureException("offline"))
                .when(session)
                .addTokens(1);
        assertThatThrownBy(() -> limiter.acquire(KEY))
                .isInstanceOf(ChatRateLimiterUnavailableException.class);
    }
}
