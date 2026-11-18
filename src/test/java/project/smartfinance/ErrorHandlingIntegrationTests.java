package project.smartfinance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
class ErrorHandlingIntegrationTests {

    @Autowired
    MockMvc mvc;

    @Test
    void invalidRegistrationBodyReturnsValidationProblemInsteadOfUnauthorized() throws Exception {
        Cookie csrf = csrf();

        mvc.perform(post("/api/auth/register")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"name":"","email":"nao-e-email","password":"curta"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists())
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist());
    }

    @Test
    void malformedJsonReturnsBadRequestProblem() throws Exception {
        Cookie csrf = csrf();

        mvc.perform(post("/api/auth/register")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("{\"name\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("malformed_body"));
    }

    @Test
    void missingBodyReturnsBadRequestProblem() throws Exception {
        Cookie csrf = csrf();

        mvc.perform(post("/api/auth/register")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("malformed_body"));
    }

    @Test
    void incomingRequestIdIsReusedInResponseAndProblemBody() throws Exception {
        Cookie csrf = csrf();

        mvc.perform(post("/api/auth/register")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .header("X-Request-Id", "trace-2026-11-18.abc")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Request-Id", "trace-2026-11-18.abc"))
                .andExpect(jsonPath("$.requestId").value("trace-2026-11-18.abc"));
    }

    @Test
    void invalidRequestIdIsReplacedByAGeneratedOne() throws Exception {
        MvcResult result = mvc.perform(get("/api/auth/csrf")
                        .header("X-Request-Id", "not valid because of spaces"))
                .andExpect(status().isNoContent())
                .andReturn();

        assertThat(result.getResponse().getHeader("X-Request-Id"))
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    void unknownApiRouteReturnsNotFoundProblemForAuthenticatedUser() throws Exception {
        Cookie csrf = csrf();
        register(csrf, "errors@example.test");
        Cookie access = authCookie(login(csrf, "errors@example.test"), "SF_ACCESS");

        mvc.perform(get("/api/does-not-exist").cookie(access))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"))
                .andExpect(jsonPath("$.instance").value("/api/does-not-exist"));
    }

    @Test
    void errorEndpointIsNotPubliclyReachable() throws Exception {
        mvc.perform(get("/error"))
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
