package project.smartfinance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.time.Instant;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
class SessionIntegrationTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JwtEncoder jwtEncoder;

    @Test
    void loginRotatesRefreshAndRevokesSessionOnReplay() throws Exception {
        Cookie csrf = csrf();
        register(csrf, "session@example.test");
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());

        MvcResult login = mvc.perform(post("/api/auth/login")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"email":"SESSION@example.test","password":"senha-de-teste-segura"}
                                """))
                .andExpect(status().isNoContent())
                .andReturn();
        Cookie access = authCookie(login, "SF_ACCESS");
        Cookie refresh = authCookie(login, "SF_REFRESH");
        assertThat(access.getValue()).isNotBlank();
        assertThat(refresh.getValue()).isNotBlank();
        assertThat(login.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .allSatisfy(header -> assertThat(header)
                        .contains("HttpOnly", "Secure", "SameSite=Lax"));

        mvc.perform(get("/api/auth/me").cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("session@example.test"));
        mvc.perform(get("/api/auth/me").cookie(new Cookie("SF_ACCESS", "tampered")))
                .andExpect(status().isUnauthorized());

        MvcResult renewal = mvc.perform(post("/api/auth/refresh")
                        .cookie(csrf, refresh)
                        .header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isNoContent())
                .andReturn();
        Cookie rotated = authCookie(renewal, "SF_REFRESH");
        assertThat(rotated.getValue()).isNotEqualTo(refresh.getValue());

        mvc.perform(post("/api/auth/refresh")
                        .cookie(csrf, refresh)
                        .header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh")
                        .cookie(csrf, rotated)
                        .header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("""
                SELECT s.revoked_at IS NOT NULL
                FROM auth_session s
                JOIN usuario u ON u.id = s.user_id
                WHERE u.email = 'session@example.test'
                """, Boolean.class)).isTrue();
    }

    @Test
    void logoutRevokesRefreshAndInactiveUserLosesAccessImmediately() throws Exception {
        Cookie csrf = csrf();
        register(csrf, "logout@example.test");
        MvcResult login = login(csrf, "logout@example.test");
        Cookie access = authCookie(login, "SF_ACCESS");
        Cookie refresh = authCookie(login, "SF_REFRESH");

        mvc.perform(post("/api/auth/logout")
                        .cookie(csrf, refresh)
                        .header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isNoContent())
                .andExpect(result -> assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                        .allSatisfy(header -> assertThat(header).contains("Max-Age=0")));
        mvc.perform(post("/api/auth/refresh")
                        .cookie(csrf, refresh)
                        .header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isUnauthorized());

        // Logout does not revoke an already issued access JWT before its expiry.
        mvc.perform(get("/api/auth/me").cookie(access)).andExpect(status().isOk());

        login = login(csrf, "logout@example.test");
        access = authCookie(login, "SF_ACCESS");
        refresh = authCookie(login, "SF_REFRESH");
        jdbc.update("UPDATE usuario SET status = 'INATIVO' WHERE email = ?",
                "logout@example.test");
        mvc.perform(get("/api/auth/me").cookie(access)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh")
                        .cookie(csrf, refresh)
                        .header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectsLoginAndRefreshWithCsrf() throws Exception {
        Cookie csrf = csrf();
        register(csrf, "csrf-session@example.test");
        String credentials = """
                {"email":"csrf-session@example.test","password":"senha-de-teste-segura"}
                """;
        mvc.perform(post("/api/auth/login").contentType("application/json").content(credentials))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/login")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"email":"csrf-session@example.test","password":"senha-incorreta"}
                                """))
                .andExpect(status().isUnauthorized());

        Cookie refresh = authCookie(login(csrf, "csrf-session@example.test"), "SF_REFRESH");
        mvc.perform(post("/api/auth/refresh").cookie(refresh))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").cookie(refresh))
                .andExpect(status().isForbidden());
    }

    @Test
    void expiredAccessJwtIsRejected() throws Exception {
        Cookie csrf = csrf();
        register(csrf, "expired@example.test");
        int userId = jdbc.queryForObject(
                "SELECT id FROM usuario WHERE email = 'expired@example.test'", Integer.class);
        Instant past = Instant.now().minusSeconds(180);
        var claims = JwtClaimsSet.builder()
                .issuer("smartfinance")
                .subject(Integer.toString(userId))
                .issuedAt(past.minusSeconds(60))
                .expiresAt(past)
                .build();
        String expired = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        mvc.perform(get("/api/auth/me").cookie(new Cookie("SF_ACCESS", expired)))
                .andExpect(status().isUnauthorized());
    }

    private Cookie csrf() throws Exception {
        Cookie csrf = mvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isNoContent())
                .andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrf).isNotNull();
        return csrf;
    }

    private void register(Cookie csrf, String email) throws Exception {
        mvc.perform(post("/api/auth/register")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"name":"Teste","email":"%s","password":"senha-de-teste-segura"}
                                """.formatted(email)))
                .andExpect(status().isCreated());
    }

    private MvcResult login(Cookie csrf, String email) throws Exception {
        return mvc.perform(post("/api/auth/login")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"email":"%s","password":"senha-de-teste-segura"}
                                """.formatted(email)))
                .andExpect(status().isNoContent())
                .andReturn();
    }

    private Cookie authCookie(MvcResult result, String name) {
        List<String> headers = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        String header = headers.stream()
                .filter(value -> value.startsWith(name + "="))
                .findFirst().orElseThrow();
        return new Cookie(name, header.substring(name.length() + 1, header.indexOf(';')));
    }
}
