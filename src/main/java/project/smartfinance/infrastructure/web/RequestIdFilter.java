package project.smartfinance.infrastructure.web;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Assigns every request a correlation id that appears in logs and in the
 * response, so a reported failure can be traced back to the server logs.
 * The id is reused on the error dispatch of the same request.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";
    public static final String ATTRIBUTE = RequestIdFilter.class.getName() + ".requestId";

    private static final Pattern ACCEPTED = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final String FALLBACK = "unknown";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String requestId = requestId(request);
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /** Current request id, or {@code "unknown"} outside a request. */
    public static String current() {
        String value = MDC.get(MDC_KEY);
        return value == null || value.isBlank() ? FALLBACK : value;
    }

    /**
     * Request id for the given request. Unlike {@link #current()}, this also
     * works on error dispatches, where the MDC of the original dispatch is gone.
     */
    public static String current(HttpServletRequest request) {
        Object value = request.getAttribute(ATTRIBUTE);
        if (value instanceof String requestId && !requestId.isBlank()) {
            return requestId;
        }
        return current();
    }

    private static String requestId(HttpServletRequest request) {
        Object existing = request.getAttribute(ATTRIBUTE);
        if (existing instanceof String value && !value.isBlank()) {
            return value;
        }
        String candidate = request.getHeader(HEADER);
        String requestId = candidate != null && ACCEPTED.matcher(candidate).matches()
                ? candidate
                : UUID.randomUUID().toString();
        request.setAttribute(ATTRIBUTE, requestId);
        return requestId;
    }
}
