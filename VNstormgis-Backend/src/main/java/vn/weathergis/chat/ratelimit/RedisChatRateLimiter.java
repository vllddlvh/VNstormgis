package vn.weathergis.chat.ratelimit;

import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.lettuce.core.RedisCommandTimeoutException;
import io.lettuce.core.RedisException;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.QueryTimeoutException;
import vn.weathergis.chat.config.ChatRateLimitProperties;
import vn.weathergis.chat.exception.ChatRateLimitedException;
import vn.weathergis.chat.exception.ChatRateLimiterUnavailableException;
import vn.weathergis.chat.port.ChatRateLimitKey;
import vn.weathergis.chat.port.ChatRateLimiter;

/** Distributed CAS quota: each accepted message consumes one session token and one IP token. */
public class RedisChatRateLimiter implements ChatRateLimiter {
    public static final String KEY_PREFIX = "weathergis:ratelimit:chat:";
    private static final Logger log = LoggerFactory.getLogger(RedisChatRateLimiter.class);

    private final Supplier<ProxyManager<byte[]>> managerSupplier;
    private final ChatClientIpResolver ipResolver;
    private final BucketConfiguration sessionConfiguration;
    private final BucketConfiguration ipConfiguration;
    private final Duration window;
    private final MeterRegistry meters;
    private volatile ProxyManager<byte[]> manager;

    public RedisChatRateLimiter(
            Supplier<ProxyManager<byte[]>> managerSupplier,
            ChatClientIpResolver ipResolver,
            ChatRateLimitProperties properties,
            MeterRegistry meters) {
        properties.validate();
        this.managerSupplier = managerSupplier;
        this.ipResolver = ipResolver;
        this.window = properties.getWindow();
        this.meters = meters;
        this.sessionConfiguration = configuration(properties.getSessionLimit(), window);
        this.ipConfiguration = configuration(properties.getIpLimit(), window);
    }

    private static BucketConfiguration configuration(int capacity, Duration window) {
        return BucketConfiguration.builder()
                .addLimit(limit -> limit.capacity(capacity).refillIntervally(capacity, window))
                .build();
    }

    @Override
    public void acquire(ChatRateLimitKey key) {
        long start = System.nanoTime();
        String clientIp = ipResolver.resolve(key.remoteAddress(), key.forwardedFor());
        try {
            ProxyManager<byte[]> store = store();
            BucketProxy session = bucket(store, "s:" + key.tokenHash(), sessionConfiguration);
            ConsumptionProbe sessionProbe = session.tryConsumeAndReturnRemaining(1);
            if (!sessionProbe.isConsumed()) {
                reject("session", sessionProbe);
            }
            BucketProxy ip = bucket(store, "ip:" + clientIp, ipConfiguration);
            ConsumptionProbe ipProbe = ip.tryConsumeAndReturnRemaining(1);
            if (!ipProbe.isConsumed()) {
                session.addTokens(1);
                reject("ip", ipProbe);
            }
            meters.counter("weathergis.chat.ratelimit.allowed").increment();
        } catch (RuntimeException ex) {
            String reason = outageReason(ex);
            if (reason == null) {
                throw ex;
            }
            meters.counter("weathergis.chat.ratelimit.outage", "reason", reason).increment();
            log.warn(
                    "Chat rate limiter unavailable: cause={} durationMs={}",
                    ex.getClass().getSimpleName(),
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
            throw new ChatRateLimiterUnavailableException();
        }
    }

    private ProxyManager<byte[]> store() {
        ProxyManager<byte[]> existing = manager;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (manager == null) {
                manager = managerSupplier.get();
            }
            return manager;
        }
    }

    private static BucketProxy bucket(
            ProxyManager<byte[]> store, String suffix, BucketConfiguration configuration) {
        return store.builder()
                .build((KEY_PREFIX + suffix).getBytes(StandardCharsets.UTF_8), () -> configuration);
    }

    private void reject(String scope, ConsumptionProbe probe) {
        long nanos = probe.getNanosToWaitForRefill();
        long seconds =
                Math.max(1, (nanos / 1_000_000_000L) + (nanos % 1_000_000_000L == 0 ? 0 : 1));
        seconds = Math.min(seconds, Math.max(1, window.toSeconds()));
        meters.counter("weathergis.chat.ratelimit.rejected", "scope", scope).increment();
        log.info("Chat rate limit rejected: scope={} retryAfterSeconds={}", scope, seconds);
        throw new ChatRateLimitedException(scope, seconds);
    }

    private static String outageReason(Throwable error) {
        boolean redisFailure = false;
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof RedisCommandTimeoutException
                    || cause instanceof QueryTimeoutException
                    || cause instanceof io.github.bucket4j.TimeoutException) {
                return "timeout";
            }
            if (cause instanceof RedisException || cause instanceof DataAccessException) {
                redisFailure = true;
            }
        }
        return redisFailure ? "connection" : null;
    }
}
