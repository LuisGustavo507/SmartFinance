package project.smartfinance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(properties = {
        "smartfinance.security.rate-limit.enabled=true",
        "smartfinance.security.rate-limit.register=2",
        "smartfinance.security.rate-limit.trust-real-ip=true",
        "smartfinance.security.rate-limit.trust-forwarded-for=false"
})
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
class RailwayRateLimitIntegrationTests {

    @Autowired
    MockMvc mvc;

    @Test
    void forwardedForSpoofingDoesNotResetClientQuota() throws Exception {
        Cookie csrf = csrf();
        mvc.perform(register(csrf).header("X-Real-IP", "198.51.100.50")
                        .header("X-Forwarded-For", "192.0.2.1, 198.51.100.50"))
                .andExpect(status().isBadRequest());
        mvc.perform(register(csrf).header("X-Real-IP", "198.51.100.50")
                        .header("X-Forwarded-For", "192.0.2.2, 198.51.100.50"))
                .andExpect(status().isBadRequest());
        mvc.perform(register(csrf).header("X-Real-IP", "198.51.100.50")
                        .header("X-Forwarded-For", "192.0.2.3, 198.51.100.50"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("too_many_requests"));

        mvc.perform(register(csrf).header("X-Real-IP", "198.51.100.51"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingTrustedHeaderUsesOneSharedQuotaInsteadOfSpoofedForwardedIp()
            throws Exception {
        Cookie csrf = csrf();
        mvc.perform(register(csrf).header("X-Forwarded-For", "192.0.2.10"))
                .andExpect(status().isBadRequest());
        mvc.perform(register(csrf).header("X-Forwarded-For", "192.0.2.11"))
                .andExpect(status().isBadRequest());
        mvc.perform(register(csrf).header("X-Forwarded-For", "192.0.2.12"))
                .andExpect(status().isTooManyRequests());
    }

    private Cookie csrf() throws Exception {
        Cookie cookie = mvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isNoContent())
                .andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        return cookie;
    }

    private MockHttpServletRequestBuilder register(Cookie csrf) {
        return post("/api/auth/register")
                .cookie(csrf)
                .header("X-XSRF-TOKEN", csrf.getValue())
                .contentType("application/json")
                .content("{}");
    }
}
