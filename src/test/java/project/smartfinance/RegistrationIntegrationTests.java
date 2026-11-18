package project.smartfinance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
class RegistrationIntegrationTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void registersUserWithHashedPasswordAndNormalizesEmail() throws Exception {
        var token = mvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isNoContent())
                .andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(token).isNotNull();

        mvc.perform(post("/api/auth/register")
                        .cookie(token)
                        .header("X-XSRF-TOKEN", token.getValue())
                        .contentType("application/json")
                        .content("""
                                {"name":"Ana","email":"ANA@example.test","password":"senha-bem-grande-e-segura"}
                                """))
                .andExpect(status().isCreated());

        var savedEmail = jdbc.queryForObject(
                "SELECT email FROM usuario WHERE nome = 'Ana'", String.class);
        var savedHash = jdbc.queryForObject(
                "SELECT senha_hash FROM usuario WHERE nome = 'Ana'", String.class);
        assertThat(savedEmail).isEqualTo("ana@example.test");
        assertThat(savedHash).isNotEqualTo("senha-bem-grande-e-segura");
        assertThat(passwordEncoder.matches("senha-bem-grande-e-segura", savedHash)).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT status FROM usuario WHERE nome = 'Ana'", String.class)).isEqualTo("ATIVO");

        mvc.perform(post("/api/auth/register")
                        .cookie(token)
                        .header("X-XSRF-TOKEN", token.getValue())
                        .contentType("application/json")
                        .content("""
                                {"name":"Outro","email":"ana@EXAMPLE.test","password":"outra-senha-bem-grande"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsMissingCsrfInvalidPayloadAndUnauthenticatedRoutes() throws Exception {
        String body = """
                {"name":"Beto","email":"beto@example.test","password":"senha-bem-grande-e-segura"}
                """;
        mvc.perform(post("/api/auth/register").contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM usuario WHERE email = 'beto@example.test'", Integer.class))
                .isZero();

        Cookie token = mvc.perform(get("/api/auth/csrf"))
                .andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(token).isNotNull();
        mvc.perform(post("/api/auth/register")
                        .cookie(token)
                        .header("X-XSRF-TOKEN", token.getValue())
                        .contentType("application/json")
                        .content("""
                                {"name":"Beto","email":"not-an-email","password":"short"}
                                """))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/wallets")).andExpect(status().isUnauthorized());
    }
}
