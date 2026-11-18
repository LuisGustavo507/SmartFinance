package project.smartfinance.api.auth;

import java.time.Duration;
import java.time.Clock;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import project.smartfinance.application.auth.InvalidSessionException;
import project.smartfinance.application.auth.SessionService;
import project.smartfinance.application.auth.SessionService.Tokens;
import project.smartfinance.application.user.RegisteredUser;

@RestController
@RequestMapping("/api/auth")
class SessionController {

    private final SessionService sessions;
    private final Clock clock;
    private final boolean secureCookies;
    private final Duration accessTtl;

    SessionController(SessionService sessions, Clock clock,
            @Value("${smartfinance.security.cookie-secure:true}") boolean secureCookies,
            @Value("${smartfinance.security.access-ttl:PT15M}") Duration accessTtl) {
        this.sessions = sessions;
        this.clock = clock;
        this.secureCookies = secureCookies;
        this.accessTtl = accessTtl;
    }

    @PostMapping("/login")
    ResponseEntity<Void> login(@Valid @RequestBody LoginRequest request) {
        return withTokens(sessions.login(request.email(), request.password()));
    }

    @PostMapping("/refresh")
    ResponseEntity<Void> refresh(HttpServletRequest request) {
        return withTokens(sessions.refresh(cookie(request, "SF_REFRESH")));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request) {
        sessions.logout(cookie(request, "SF_REFRESH"));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie("SF_ACCESS", "", "/api", Duration.ZERO),
                        cookie("SF_REFRESH", "", "/api/auth", Duration.ZERO))
                .build();
    }

    @GetMapping("/me")
    RegisteredUser currentUser(Authentication authentication) {
        return (RegisteredUser) authentication.getPrincipal();
    }

    @ExceptionHandler(InvalidSessionException.class)
    ResponseEntity<Void> invalidSession() {
        return ResponseEntity.status(401).build();
    }

    private ResponseEntity<Void> withTokens(Tokens tokens) {
        Duration remaining = Duration.between(clock.instant(), tokens.refreshExpiresAt());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE,
                        cookie("SF_ACCESS", tokens.access(), "/api", accessTtl),
                        cookie("SF_REFRESH", tokens.refresh(), "/api/auth", remaining))
                .build();
    }

    private String cookie(String name, String value, String path, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secureCookies)
                .sameSite("Lax")
                .path(path)
                .maxAge(maxAge)
                .build()
                .toString();
    }

    private String cookie(HttpServletRequest request, String name) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (name.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }
}
