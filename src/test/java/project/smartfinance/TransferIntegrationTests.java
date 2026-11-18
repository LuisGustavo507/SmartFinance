package project.smartfinance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
class TransferIntegrationTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void transfersChangeBothBalancesButNeverMonthlyIncomeOrExpenses()
            throws Exception {
        Cookie csrf = csrf();
        int ownerId = register(csrf, "transfer-owner@example.test");
        register(csrf, "transfer-other@example.test");
        Cookie owner = login(csrf, "transfer-owner@example.test");
        Cookie other = login(csrf, "transfer-other@example.test");
        int source = wallet(csrf, owner, "Origem", "100.00");
        int destination = wallet(csrf, owner, "Destino", "5.00");
        int incomeCategory = category(csrf, owner);

        mvc.perform(transfer(csrf, owner, source, destination, "150.00",
                        "2026-01-01T12:00:00+02:00"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownerUserId").value(ownerId))
                .andExpect(jsonPath("$.amount").value(150.00))
                .andExpect(jsonPath("$.transferredAt").value("2026-01-01T10:00:00Z"));
        // This is still January in UTC despite being February in the supplied offset.
        mvc.perform(transfer(csrf, owner, source, destination, "10.00",
                        "2026-02-01T00:30:00+02:00"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/entries")
                        .cookie(csrf, owner).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"walletId":%d,"categoryId":%d,"description":"Receita",
                                 "amount":20.00,"occurredOn":"2026-01-02"}
                                """.formatted(source, incomeCategory)))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/wallets/{id}", source).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimatedBalance").value(-40.00));
        mvc.perform(get("/api/wallets/{id}", destination).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimatedBalance").value(165.00));
        mvc.perform(get("/api/transfers?month=2026-01").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/transfers?month=2026-02").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/transfers?month=2026-01").cookie(other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/transfers?month=2026-01"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/monthly-summary?month=2026-01").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.realizedIncome").value(20.00))
                .andExpect(jsonPath("$.realizedExpenses").value(0))
                .andExpect(jsonPath("$.realizedResult").value(20.00));
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM transferencia WHERE owner_user_id = ?",
                Integer.class, ownerId)).isEqualTo(2);
    }

    @Test
    void rejectsInvalidWalletsDatesAmountsAndMissingCsrf() throws Exception {
        Cookie csrf = csrf();
        int ownerId = register(csrf, "transfer-validation@example.test");
        register(csrf, "transfer-foreign@example.test");
        Cookie owner = login(csrf, "transfer-validation@example.test");
        Cookie foreign = login(csrf, "transfer-foreign@example.test");
        int source = wallet(csrf, owner, "Carteira A", "10.00");
        int destination = wallet(csrf, owner, "Carteira B", "0.00");
        int otherWallet = wallet(csrf, foreign, "Carteira externa", "0.00");

        mvc.perform(transfer(csrf, owner, source, source, "1", "2026-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest());
        mvc.perform(transfer(csrf, owner, source, otherWallet, "1",
                        "2026-01-01T00:00:00Z"))
                .andExpect(status().isNotFound());
        mvc.perform(transfer(csrf, owner, source, destination, "1",
                        "2025-12-31T23:59:59Z"))
                .andExpect(status().isBadRequest());
        mvc.perform(transfer(csrf, owner, source, destination, "1",
                        "2099-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest());
        mvc.perform(transfer(csrf, owner, source, destination, "0",
                        "2026-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest());
        mvc.perform(transfer(csrf, owner, source, destination, "0.001",
                        "2026-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/transfers").cookie(owner)
                        .contentType("application/json")
                        .content("""
                                {"sourceWalletId":%d,"destinationWalletId":%d,
                                 "amount":1,"transferredAt":"2026-01-01T00:00:00Z"}
                                """.formatted(source, destination)))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM transferencia WHERE owner_user_id = ?",
                Integer.class, ownerId)).isZero();

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO transferencia (owner_user_id, conta_origem_id, conta_destino_id,
                    valor, transferida_em, criada_em)
                VALUES (?, ?, ?, ?, ?, ?)
                """, ownerId, source, otherWallet, new BigDecimal("1.00"),
                OffsetDateTime.parse("2026-01-01T12:00:00Z"), OffsetDateTime.now()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private MockHttpServletRequestBuilder transfer(Cookie csrf, Cookie access,
            int source, int destination, String amount, String transferredAt) {
        return post("/api/transfers")
                .cookie(csrf, access).header("X-XSRF-TOKEN", csrf.getValue())
                .contentType("application/json")
                .content("""
                        {"sourceWalletId":%d,"destinationWalletId":%d,
                         "amount":%s,"description":"  Transferência de teste  ",
                         "transferredAt":"%s","ownerUserId":999999}
                        """.formatted(source, destination, amount, transferredAt));
    }

    private int wallet(Cookie csrf, Cookie access, String name, String balance)
            throws Exception {
        return id(mvc.perform(post("/api/wallets")
                        .cookie(csrf, access).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"name":"%s","initialBalance":%s,
                                 "initialBalanceDate":"2026-01-01"}
                                """.formatted(name, balance)))
                .andExpect(status().isCreated()).andReturn());
    }

    private int category(Cookie csrf, Cookie access) throws Exception {
        return id(mvc.perform(post("/api/categories")
                        .cookie(csrf, access).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"name":"Salário transferências","kind":"RECEITA"}
                                """))
                .andExpect(status().isCreated()).andReturn());
    }

    private Cookie csrf() throws Exception {
        Cookie token = mvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isNoContent()).andReturn()
                .getResponse().getCookie("XSRF-TOKEN");
        assertThat(token).isNotNull();
        return token;
    }

    private int register(Cookie csrf, String email) throws Exception {
        return id(mvc.perform(post("/api/auth/register")
                        .cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"name":"Teste","email":"%s","password":"senha-de-teste-segura"}
                                """.formatted(email)))
                .andExpect(status().isCreated()).andReturn());
    }

    private int id(MvcResult response) throws Exception {
        return Integer.parseInt(com.jayway.jsonpath.JsonPath.read(
                response.getResponse().getContentAsString(), "$.id").toString());
    }

    private Cookie login(Cookie csrf, String email) throws Exception {
        MvcResult response = mvc.perform(post("/api/auth/login")
                        .cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"email":"%s","password":"senha-de-teste-segura"}
                                """.formatted(email)))
                .andExpect(status().isNoContent()).andReturn();
        String header = response.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(value -> value.startsWith("SF_ACCESS="))
                .findFirst().orElseThrow();
        return new Cookie("SF_ACCESS", header.substring("SF_ACCESS=".length(),
                header.indexOf(';')));
    }
}
