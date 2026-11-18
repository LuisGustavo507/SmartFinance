package project.smartfinance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(properties = "smartfinance.registration.invite-required=true")
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
class InviteIntegrationTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void rejectsRegistrationWithoutInvite() throws Exception {
        Cookie csrf = csrf();

        register(csrf, "sem-convite@example.test")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("invite_required"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM usuario WHERE email = 'sem-convite@example.test'
                """, Integer.class)).isZero();
    }

    @Test
    void acceptsInviteRegardlessOfEmailCaseAndMarksItUsed() throws Exception {
        Cookie csrf = csrf();
        invite("convidado@example.test");

        register(csrf, "Convidado@Example.Test")
                .andExpect(status().isCreated());

        Integer userId = jdbc.queryForObject("""
                SELECT id FROM usuario WHERE email = 'convidado@example.test'
                """, Integer.class);
        assertThat(userId).isNotNull();
        assertThat(jdbc.queryForObject("""
                SELECT usado_por FROM convite_beta WHERE email = 'convidado@example.test'
                """, Integer.class)).isEqualTo(userId);
        assertThat(jdbc.queryForObject("""
                SELECT usado_em IS NOT NULL FROM convite_beta WHERE email = 'convidado@example.test'
                """, Boolean.class)).isTrue();
    }

    @Test
    void inviteForAnotherEmailDoesNotAuthorizeRegistration() throws Exception {
        Cookie csrf = csrf();
        invite("outra-pessoa@example.test");

        register(csrf, "intruso@example.test")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("invite_required"));
    }

    private void invite(String email) {
        jdbc.update("""
                INSERT INTO convite_beta (email, criado_em) VALUES (lower(?), ?)
                """, email, OffsetDateTime.now());
    }

    private Cookie csrf() throws Exception {
        Cookie csrf = mvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isNoContent())
                .andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrf).isNotNull();
        return csrf;
    }

    private org.springframework.test.web.servlet.ResultActions register(Cookie csrf, String email)
            throws Exception {
        MockHttpServletRequestBuilder request = post("/api/auth/register")
                .cookie(csrf)
                .header("X-XSRF-TOKEN", csrf.getValue())
                .contentType("application/json")
                .content("""
                        {"name":"Convidado","email":"%s","password":"senha-de-teste-segura"}
                        """.formatted(email));
        return mvc.perform(request);
    }
}
