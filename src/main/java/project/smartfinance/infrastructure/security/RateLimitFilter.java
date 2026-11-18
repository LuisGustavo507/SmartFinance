package project.smartfinance.infrastructure.security;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import project.smartfinance.infrastructure.audit.SecurityAudit;
import project.smartfinance.infrastructure.web.RequestIdFilter;

/**
 * Limits authentication endpoints to slow down credential stuffing and mass
 * registration. Runs inside the security chain so responses keep the security
 * headers, and after CSRF validation so forged requests never consume quota.
 *
 * State is per instance on purpose: the application runs as a single replica.
 */
@Component
@Order(-99) // Just after the Spring Security chain (-100), so CSRF and headers apply.
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String PROBLEM_JSON = "application/problem+json";

    private final boolean enabled;
    private final boolean trustForwardedFor;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final SecurityAudit audit;
    private final List<Rule> rules;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final long longestWindowMillis;

    RateLimitFilter(Clock clock, ObjectMapper objectMapper, SecurityAudit audit,
            @Value("${smartfinance.security.rate-limit.enabled:true}") boolean enabled,
            @Value("${smartfinance.security.rate-limit.trust-forwarded-for:false}")
            boolean trustForwardedFor,
            @Value("${smartfinance.security.rate-limit.login-failures:10}") int loginFailures,
            @Value("${smartfinance.security.rate-limit.login-window:PT15M}") Duration loginWindow,
            @Value("${smartfinance.security.rate-limit.register:5}") int registerLimit,
            @Value("${smartfinance.security.rate-limit.register-window:PT1H}") Duration registerWindow,
            @Value("${smartfinance.security.rate-limit.refresh:60}") int refreshLimit,
            @Value("${smartfinance.security.rate-limit.refresh-window:PT15M}") Duration refreshWindow) {
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.audit = audit;
        this.enabled = enabled;
        this.trustForwardedFor = trustForwardedFor;
        this.rules = List.of(
                new Rule("POST", "/api/auth/login", loginFailures, loginWindow, true),
                new Rule("POST", "/api/auth/register", registerLimit, registerWindow, false),
                new Rule("POST", "/api/auth/refresh", refreshLimit, refreshWindow, false));
        this.longestWindowMillis = rules.stream()
                .mapToLong(rule -> rule.window().toMillis())
                .max().orElse(0);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        Rule rule = enabled ? match(request) : null;
        if (rule == null) {
            chain.doFilter(request, response);
            return;
        }

        String key = rule.method() + " " + rule.path() + " " + clientIp(request);
        long retryAfter = rule.failuresOnly()
                ? blockedFor(key, rule)
                : consumeOrRetryAfter(key, rule);
        if (retryAfter > 0) {
            reject(response, request, retryAfter);
            return;
        }

        chain.doFilter(request, response);

        // Login only spends quota when authentication actually fails, so a
        // successful session is never punished by previous typos.
        if (rule.failuresOnly() && response.getStatus() == HttpServletResponse.SC_UNAUTHORIZED) {
            recordFailure(key, rule);
        }
    }

    @Scheduled(fixedDelayString = "PT10M")
    void purgeExpiredWindows() {
        long now = clock.millis();
        windows.entrySet().removeIf(entry ->
                now - entry.getValue().start() >= longestWindowMillis);
    }

    private Rule match(HttpServletRequest request) {
        for (Rule rule : rules) {
            if (rule.method().equals(request.getMethod())
                    && rule.path().equals(request.getRequestURI())) {
                return rule;
            }
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        if (trustForwardedFor) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                String first = forwarded.split(",")[0].strip();
                if (!first.isEmpty() && first.length() <= 64) {
                    return first;
                }
            }
        }
        return request.getRemoteAddr();
    }

    /** Returns the seconds to wait when the current window is already full. */
    private long blockedFor(String key, Rule rule) {
        long now = clock.millis();
        Window window = windows.compute(key, (ignored, current) ->
                expired(current, now, rule) ? new Window(now, 0) : current);
        return window.count() >= rule.limit() ? retryAfterSeconds(window, now, rule) : 0;
    }

    /** Consumes one request from the window, or returns the seconds to wait. */
    private long consumeOrRetryAfter(String key, Rule rule) {
        long now = clock.millis();
        long[] retryAfter = {0};
        windows.compute(key, (ignored, current) -> {
            Window window = expired(current, now, rule) ? new Window(now, 0) : current;
            if (window.count() >= rule.limit()) {
                retryAfter[0] = retryAfterSeconds(window, now, rule);
                return window;
            }
            return new Window(window.start(), window.count() + 1);
        });
        return retryAfter[0];
    }

    private void recordFailure(String key, Rule rule) {
        long now = clock.millis();
        windows.compute(key, (ignored, current) -> {
            Window window = expired(current, now, rule) ? new Window(now, 0) : current;
            return new Window(window.start(), Math.min(window.count() + 1, rule.limit()));
        });
    }

    private static boolean expired(Window window, long now, Rule rule) {
        return window == null || now - window.start() >= rule.window().toMillis();
    }

    private static long retryAfterSeconds(Window window, long now, Rule rule) {
        long remaining = window.start() + rule.window().toMillis() - now;
        return Math.max(1, (remaining + 999) / 1000);
    }

    private void reject(HttpServletResponse response, HttpServletRequest request, long retryAfter)
            throws IOException {
        audit.rateLimited(request.getRequestURI(), retryAfter);
        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "about:blank");
        problem.put("title", "Muitas requisições");
        problem.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
        problem.put("detail", "Aguarde antes de tentar novamente.");
        problem.put("instance", request.getRequestURI());
        problem.put("code", "too_many_requests");
        problem.put("requestId", RequestIdFilter.current(request));
        problem.put("retryAfter", retryAfter);

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", Long.toString(retryAfter));
        response.setContentType(PROBLEM_JSON);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }

    private record Rule(String method, String path, int limit, Duration window,
            boolean failuresOnly) {
    }

    private record Window(long start, int count) {
    }
}
