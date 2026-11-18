package project.smartfinance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest(properties = {
        "smartfinance.security.rate-limit.enabled=true",
        "smartfinance.security.rate-limit.login-failures=3",
        "smartfinance.security.rate-limit.register=2",
        "smartfinance.security.rate-limit.refresh=100"
})
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
class RateLimitIntegrationTests {

    @Autowired
    MockMvc mvc;

    @Test
    void blocksLoginOnlyAfterConfiguredFailuresPerClient() throws Exception {
        Cookie csrf = csrf();
        register(csrf, "limit@example.test", "198.51.100.20");

        for (int attempt = 0; attempt < 3; attempt++) {
            login(csrf, "limit@example.test", "senha-incorreta", "198.51.100.10")
                    .andExpect(status().isUnauthorized());
        }

        mvc.perform(loginRequest(csrf, "limit@example.test", "senha-de-teste-segura",
                        "198.51.100.10")
                        .header("X-Request-Id", "rate-limit-test"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().string("X-Request-Id", "rate-limit-test"))
                .andExpect(jsonPath("$.code").value("too_many_requests"))
                .andExpect(jsonPath("$.requestId").value("rate-limit-test"));

        // Another client is not affected by the first client's failures.
        login(csrf, "limit@example.test", "senha-de-teste-segura", "198.51.100.11")
                .andExpect(status().isNoContent());
    }

    @Test
    void successfulLoginDoesNotSpendFailureQuota() throws Exception {
        Cookie csrf = csrf();
        register(csrf, "quota@example.test", "198.51.100.30");

        login(csrf, "quota@example.test", "senha-incorreta", "198.51.100.31")
                .andExpect(status().isUnauthorized());
        login(csrf, "quota@example.test", "senha-de-teste-segura", "198.51.100.31")
                .andExpect(status().isNoContent());
        login(csrf, "quota@example.test", "senha-incorreta", "198.51.100.31")
                .andExpect(status().isUnauthorized());
        login(csrf, "quota@example.test", "senha-incorreta", "198.51.100.31")
                .andExpect(status().isUnauthorized());
        login(csrf, "quota@example.test", "senha-de-teste-segura", "198.51.100.31")
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void limitsRegistrationAttemptsPerClient() throws Exception {
        Cookie csrf = csrf();
        register(csrf, "register-one@example.test", "198.51.100.40");
        register(csrf, "register-two@example.test", "198.51.100.40");

        mvc.perform(registerRequest(csrf, "register-three@example.test", "198.51.100.40"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("too_many_requests"));

        register(csrf, "register-four@example.test", "198.51.100.41");
    }

    private Cookie csrf() throws Exception {
        Cookie csrf = mvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isNoContent())
                .andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrf).isNotNull();
        return csrf;
    }

    private void register(Cookie csrf, String email, String clientIp) throws Exception {
        mvc.perform(registerRequest(csrf, email, clientIp))
                .andExpect(status().isCreated());
    }

    private org.springframework.test.web.servlet.ResultActions login(Cookie csrf, String email,
            String password, String clientIp) throws Exception {
        return mvc.perform(loginRequest(csrf, email, password, clientIp));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder loginRequest(
            Cookie csrf, String email, String password, String clientIp) {
        return post("/api/auth/login")
                .with(client(clientIp))
                .cookie(csrf)
                .header("X-XSRF-TOKEN", csrf.getValue())
                .contentType("application/json")
                .content("""
                        {"email":"%s","password":"%s"}
                        """.formatted(email, password));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder registerRequest(
            Cookie csrf, String email, String clientIp) {
        return post("/api/auth/register")
                .with(client(clientIp))
                .cookie(csrf)
                .header("X-XSRF-TOKEN", csrf.getValue())
                .contentType("application/json")
                .content("""
                        {"name":"Teste","email":"%s","password":"senha-de-teste-segura"}
                        """.formatted(email));
    }

    private static RequestPostProcessor client(String clientIp) {
        return request -> {
            request.setRemoteAddr(clientIp);
            return request;
        };
    }
}
