package project.smartfinance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import project.smartfinance.application.payable.Payables;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
class PayableIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired Payables payables;

    @Test
    void obligationDoesNotMoveBalanceAndPaymentReversalAreCountedOnce() throws Exception {
        Cookie csrf = csrf();
        register(csrf, "payable-flow@example.test");
        Cookie owner = login(csrf, "payable-flow@example.test");
        int walletA = wallet(csrf, owner, "Conta A");
        int walletB = wallet(csrf, owner, "Conta B");
        int category = category(csrf, owner, "Energia", "DESPESA");
        int entity = entity(csrf, owner, "Distribuidora");
        LocalDate due = LocalDate.now(ZoneId.of("America/Sao_Paulo")).minusDays(4);
        String month = due.toString().substring(0, 7);
        int id = id(mvc.perform(mutate(post("/api/payables"), csrf, owner)
                        .content(payable(category, entity, due, "100.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ATRASADA"))
                .andReturn());
        mvc.perform(get("/api/wallets/{id}", walletA).cookie(owner))
                .andExpect(jsonPath("$.estimatedBalance").value(100));
        mvc.perform(get("/api/monthly-summary").param("month", month)
                        .param("entityId", String.valueOf(entity)).cookie(owner))
                .andExpect(jsonPath("$.pendingExpenses").value(100))
                .andExpect(jsonPath("$.realizedExpenses").value(0))
                .andExpect(jsonPath("$.plannedExpenses").value(0))
                .andExpect(jsonPath("$.projectedResult").value(-100))
                .andExpect(jsonPath("$.categoryTotals[0].pendingExpenses").value(100))
                .andExpect(jsonPath("$.necessityTotals[0].pendingExpenses").value(100));
        mvc.perform(get("/api/payables").param("month", due.plusMonths(2).toString().substring(0, 7))
                        .cookie(owner))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].status").value("ATRASADA"));
        mvc.perform(mutate(post("/api/payables/{id}/payments", id), csrf, owner)
                        .content("{\"walletId\":%d,\"paidOn\":\"%s\"}".formatted(
                                walletA, LocalDate.now(ZoneId.of("America/Sao_Paulo")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(100));
        int paymentId = jdbc.queryForObject("""
                SELECT id FROM pagamento_conta_pagar WHERE conta_pagar_id = ?
                """, Integer.class, id);
        mvc.perform(get("/api/wallets/{id}", walletA).cookie(owner))
                .andExpect(jsonPath("$.estimatedBalance").value(0));
        mvc.perform(get("/api/monthly-summary").param("month", month).cookie(owner))
                .andExpect(jsonPath("$.realizedExpenses").value(100))
                .andExpect(jsonPath("$.pendingExpenses").value(0))
                .andExpect(jsonPath("$.projectedResult").value(-100));
        mvc.perform(get("/api/reports/monthly").param("from", month).param("to", month).cookie(owner))
                .andExpect(jsonPath("$[0].realizedExpenses").value(100))
                .andExpect(jsonPath("$[0].pendingExpenses").value(0));
        mvc.perform(get("/api/reports/entity-period").param("from", month).param("to", month)
                        .cookie(owner))
                .andExpect(jsonPath("$.entities[0].realizedExpenses").value(100));
        mvc.perform(mutate(post("/api/payables/{id}/payments", id), csrf, owner)
                        .content("{\"walletId\":%d,\"paidOn\":\"%s\"}".formatted(walletB, due)))
                .andExpect(status().isConflict());
        mvc.perform(mutate(post("/api/payables/{id}/payments/{paymentId}/reverse", id, paymentId),
                        csrf, owner).content("{\"reason\":\"Conta informada por engano\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reversalReason").value("Conta informada por engano"));
        mvc.perform(mutate(post("/api/payables/{id}/payments/{paymentId}/reverse", id, paymentId),
                        csrf, owner).content("{\"reason\":\"Tentar novamente\"}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/wallets/{id}", walletA).cookie(owner))
                .andExpect(jsonPath("$.estimatedBalance").value(100));
        mvc.perform(get("/api/monthly-summary").param("month", month).cookie(owner))
                .andExpect(jsonPath("$.pendingExpenses").value(100))
                .andExpect(jsonPath("$.realizedExpenses").value(0));
        mvc.perform(mutate(put("/api/payables/{id}", id), csrf, owner)
                        .content(payable(category, entity, due, "90.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(90));
        mvc.perform(mutate(post("/api/payables/{id}/payments", id), csrf, owner)
                        .content("{\"walletId\":%d,\"paidOn\":\"%s\"}".formatted(
                                walletB, LocalDate.now(ZoneId.of("America/Sao_Paulo")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(90));
        mvc.perform(get("/api/wallets/{id}", walletB).cookie(owner))
                .andExpect(jsonPath("$.estimatedBalance").value(10));
        mvc.perform(get("/api/payables/{id}/payments", id).cookie(owner))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].reversedAt").isNotEmpty())
                .andExpect(jsonPath("$[1].reversedAt").isEmpty());
        mvc.perform(mutate(post("/api/payables/{id}/cancel", id), csrf, owner))
                .andExpect(status().isConflict());
        int corrected = jdbc.queryForObject("""
                SELECT id FROM pagamento_conta_pagar
                WHERE conta_pagar_id = ? AND estornado_em IS NULL
                """, Integer.class, id);
        mvc.perform(mutate(post("/api/payables/{id}/payments/{paymentId}/reverse", id, corrected),
                        csrf, owner).content("{\"reason\":\"Pagamento também incorreto\"}"))
                .andExpect(status().isOk());
        mvc.perform(mutate(post("/api/payables/{id}/cancel", id), csrf, owner))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/payables/{id}/payments", id).cookie(owner))
                .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/wallets/{id}", walletB).cookie(owner))
                .andExpect(jsonPath("$.estimatedBalance").value(100));
    }

    @Test
    void cancellationValidationIsolationAndExistingEntriesStayUnchanged() throws Exception {
        Cookie csrf = csrf();
        register(csrf, "payable-own@example.test");
        register(csrf, "payable-other@example.test");
        Cookie owner = login(csrf, "payable-own@example.test");
        Cookie other = login(csrf, "payable-other@example.test");
        int walletId = wallet(csrf, owner, "Conta própria");
        int category = category(csrf, owner, "Internet", "DESPESA");
        int income = category(csrf, owner, "Salário", "RECEITA");
        LocalDate due = LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(5);
        String body = payable(category, null, due, "45.00");
        mvc.perform(mutate(post("/api/payables"), csrf, owner)
                        .content(payable(income, null, due, "45.00")))
                .andExpect(status().isNotFound());
        mvc.perform(mutate(post("/api/payables"), csrf, owner)
                        .content(payable(category, null, due, "0.00")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/payables").cookie(owner).contentType("application/json")
                        .content(body))
                .andExpect(status().isForbidden());
        int id = id(mvc.perform(mutate(post("/api/payables"), csrf, owner).content(body))
                .andExpect(status().isCreated()).andReturn());
        mvc.perform(get("/api/payables/{id}", id).cookie(other))
                .andExpect(status().isNotFound());
        mvc.perform(mutate(put("/api/payables/{id}", id), csrf, other).content(body))
                .andExpect(status().isNotFound());
        mvc.perform(mutate(post("/api/payables/{id}/payments", id), csrf, other)
                        .content("{\"walletId\":%d,\"paidOn\":\"%s\"}".formatted(walletId, due)))
                .andExpect(status().isNotFound());
        mvc.perform(mutate(post("/api/payables/{id}/payments", id), csrf, owner)
                        .content("{\"walletId\":%d,\"paidOn\":\"%s\"}".formatted(
                                walletId, due.plusDays(1))))
                .andExpect(status().isBadRequest());
        mvc.perform(mutate(post("/api/payables/{id}/payments", id), csrf, owner)
                        .content("{\"walletId\":%d,\"amount\":40,\"paidOn\":\"%s\"}"
                                .formatted(walletId, LocalDate.now(ZoneId.of("America/Sao_Paulo")))))
                .andExpect(status().isBadRequest());
        mvc.perform(mutate(post("/api/payables/{id}/cancel", id), csrf, owner))
                .andExpect(status().isNoContent());
        mvc.perform(mutate(post("/api/payables/{id}/cancel", id), csrf, owner))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/monthly-summary").param("month", due.toString().substring(0, 7))
                        .cookie(owner))
                .andExpect(jsonPath("$.pendingExpenses").value(0));
        mvc.perform(get("/api/wallets/{id}", walletId).cookie(owner))
                .andExpect(jsonPath("$.estimatedBalance").value(100));
    }

    @Test
    void concurrentPaymentsOnlyCreateOneFinancialOutflow() throws Exception {
        Cookie csrf = csrf();
        int ownerId = register(csrf, "payable-concurrent@example.test");
        Cookie owner = login(csrf, "payable-concurrent@example.test");
        int wallet = wallet(csrf, owner, "Conta concorrente");
        int category = category(csrf, owner, "Aluguel", "DESPESA");
        LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        int id = id(mvc.perform(mutate(post("/api/payables"), csrf, owner)
                        .content(payable(category, null, today, "75.00")))
                .andExpect(status().isCreated()).andReturn());
        try (var executor = Executors.newFixedThreadPool(2)) {
            var ready = new CountDownLatch(2);
            var start = new CountDownLatch(1);
            var first = executor.submit(() -> attemptPay(ownerId, id, wallet, today, ready, start));
            var second = executor.submit(() -> attemptPay(ownerId, id, wallet, today, ready, start));
            ready.await();
            start.countDown();
            assertThat(first.get() + second.get()).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM pagamento_conta_pagar
                WHERE conta_pagar_id = ? AND estornado_em IS NULL
                """, Integer.class, id)).isEqualTo(1);
        mvc.perform(get("/api/wallets/{id}", wallet).cookie(owner))
                .andExpect(jsonPath("$.estimatedBalance").value(25));
    }

    private int attemptPay(int ownerId, int id, int wallet, LocalDate today,
            CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            start.await();
            payables.pay(ownerId, id, wallet, null, today);
            return 1;
        } catch (Payables.Conflict expected) {
            return 0;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(exception);
        }
    }

    private String payable(int category, Integer entity, LocalDate due, String amount) {
        return """
                {"categoryId":%d,"entityId":%s,"description":"Conta de serviço",
                 "necessity":"NECESSARIA","amount":%s,"dueOn":"%s"}
                """.formatted(category, entity == null ? "null" : entity, amount, due);
    }

    private MockHttpServletRequestBuilder mutate(MockHttpServletRequestBuilder request,
            Cookie csrf, Cookie owner) {
        return request.cookie(csrf, owner).header("X-XSRF-TOKEN", csrf.getValue())
                .contentType("application/json");
    }

    private int wallet(Cookie csrf, Cookie owner, String name) throws Exception {
        return id(mvc.perform(mutate(post("/api/wallets"), csrf, owner).content("""
                {"name":"%s","initialBalance":100,"initialBalanceDate":"2026-01-01"}
                """.formatted(name))).andExpect(status().isCreated()).andReturn());
    }

    private int category(Cookie csrf, Cookie owner, String name, String kind) throws Exception {
        return id(mvc.perform(mutate(post("/api/categories"), csrf, owner).content("""
                {"name":"%s","kind":"%s"}
                """.formatted(name, kind))).andExpect(status().isCreated()).andReturn());
    }

    private int entity(Cookie csrf, Cookie owner, String name) throws Exception {
        return id(mvc.perform(mutate(post("/api/entities"), csrf, owner).content("""
                {"name":"%s"}
                """.formatted(name))).andExpect(status().isCreated()).andReturn());
    }

    private Cookie csrf() throws Exception {
        Cookie cookie = mvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isNoContent()).andReturn()
                .getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        return cookie;
    }

    private int register(Cookie csrf, String email) throws Exception {
        return id(mvc.perform(post("/api/auth/register").cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json").content("""
                                {"name":"Teste","email":"%s","password":"senha-de-teste-segura"}
                                """.formatted(email)))
                .andExpect(status().isCreated()).andReturn());
    }

    private Cookie login(Cookie csrf, String email) throws Exception {
        MvcResult response = mvc.perform(post("/api/auth/login").cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json").content("""
                                {"email":"%s","password":"senha-de-teste-segura"}
                                """.formatted(email)))
                .andExpect(status().isNoContent()).andReturn();
        String header = response.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(value -> value.startsWith("SF_ACCESS="))
                .findFirst().orElseThrow();
        return new Cookie("SF_ACCESS", header.substring("SF_ACCESS=".length(),
                header.indexOf(';')));
    }

    private int id(MvcResult response) throws Exception {
        return Integer.parseInt(com.jayway.jsonpath.JsonPath.read(
                response.getResponse().getContentAsString(), "$.id").toString());
    }
}
