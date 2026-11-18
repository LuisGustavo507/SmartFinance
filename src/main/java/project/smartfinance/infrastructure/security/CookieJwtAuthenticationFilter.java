package project.smartfinance.infrastructure.security;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;
import project.smartfinance.application.user.UserAccountStore;
import project.smartfinance.application.user.UserCredentials;

class CookieJwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtDecoder decoder;
    private final UserAccountStore users;

    CookieJwtAuthenticationFilter(JwtDecoder decoder, UserAccountStore users) {
        this.decoder = decoder;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String accessToken = cookieValue(request, "SF_ACCESS");
        if (accessToken != null) {
            try {
                int userId = Integer.parseInt(decoder.decode(accessToken).getSubject());
                users.findById(userId)
                        .filter(UserCredentials::active)
                        .ifPresent(user -> SecurityContextHolder.getContext().setAuthentication(
                                new UsernamePasswordAuthenticationToken(
                                        user.publicProfile(), null, List.of())));
            } catch (JwtException | NumberFormatException ignored) {
                // No authenticated principal: protected routes return 401.
            }
        }
        chain.doFilter(request, response);
    }

    static String cookieValue(HttpServletRequest request, String name) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (name.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
