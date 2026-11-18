package project.smartfinance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import jakarta.servlet.http.Cookie;
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
import project.smartfinance.application.card.CardStatementProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
class CardStatementPaymentIntegrationTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    CardStatementProcessor processor;

    @Test
    void reversalCancelsOpenInstallmentsWithoutCreditAndIsIdempotent() throws Exception {
        Cookie csrf = csrf();
        register(csrf, "reverse-open@example.test");
        register(csrf, "reverse-open-other@example.test");
        Cookie owner = login(csrf, "reverse-open@example.test");
        Cookie other = login(csrf, "reverse-open-other@example.test");
        int category = category(csrf, owner, "Loja", "DESPESA");
        int card = card(csrf, owner, "Cartão Reversível", 28, 5);
        MvcResult purchase = mvc.perform(purchase(csrf, owner, card, category, "Compra",
                        "30.00", LocalDate.now(ZoneOffset.UTC), 2))
                .andExpect(status().isCreated()).andReturn();
        int id = intValue(purchase, "$.id");
        String month = value(purchase, "$.installments[0].statementReferenceMonth");

        mvc.perform(post("/api/card-purchases/{id}/reversal", id).cookie(other, csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/card-purchases/{id}/reversal", id).cookie(owner, csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADA"))
                .andExpect(jsonPath("$.installments[0].status").value("CANCELADA"))
                .andExpect(jsonPath("$.installments[1].status").value("CANCELADA"));
        mvc.perform(post("/api/card-purchases/{id}/reversal", id).cookie(owner, csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADA"));
        mvc.perform(get("/api/card-credits").param("month",
                        java.time.YearMonth.now(ZoneOffset.UTC).toString()).cookie(owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/monthly-summary").param("month", month).cookie(owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.plannedExpenses").value(0));
    }

    @Test
    void reversalKeepsClosedExpensesAndAppliesCreditAtNextClosing() throws Exception {
        Cookie csrf = csrf();
        register(csrf, "reverse-mixed@example.test");
        Cookie owner = login(csrf, "reverse-mixed@example.test");
        int wallet = wallet(csrf, owner);
        int category = category(csrf, owner, "Loja", "DESPESA");
        int card = card(csrf, owner, "Cartão Com Crédito", 28, 5);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        MvcResult purchase = mvc.perform(purchase(csrf, owner, card, category, "Compra",
                        "40.00", today, 2))
                .andExpect(status().isCreated()).andReturn();
        int id = intValue(purchase, "$.id");
        int oldStatement = intValue(purchase, "$.installments[0].statementId");
        String oldMonth = value(purchase, "$.installments[0].statementReferenceMonth");
        jdbc.update("""
                UPDATE fatura_cartao SET data_fechamento = ?, data_vencimento = ?
                WHERE id = ?
                """, today.minusDays(5), today.plusDays(5), oldStatement);
        processor.processDue();

        mvc.perform(post("/api/card-purchases/{id}/reversal", id).cookie(owner, csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ESTORNADA"))
                .andExpect(jsonPath("$.installments[0].status").value("ESTORNADA"))
                .andExpect(jsonPath("$.installments[1].status").value("CANCELADA"));
        mvc.perform(get("/api/monthly-summary").param("month", oldMonth).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.realizedExpenses").value(20))
                .andExpect(jsonPath("$.necessityTotals[0].realizedExpenses").value(20));
        mvc.perform(get("/api/card-credits").param("month",
                        java.time.YearMonth.now(ZoneOffset.UTC).toString()).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].originalAmount").value(20))
                .andExpect(jsonPath("$[0].availableAmount").value(20));
        mvc.perform(get("/api/monthly-summary").param("month",
                        java.time.YearMonth.now(ZoneOffset.UTC).toString()).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reversalCreditsGenerated").value(20))
                .andExpect(jsonPath("$.reversalCreditsApplied").value(0));

        MvcResult newPurchase = mvc.perform(purchase(csrf, owner, card, category,
                        "Compra nova", "30.00", today.plusMonths(1), 1))
                .andExpect(status().isCreated()).andReturn();
        int nextStatement = intValue(newPurchase, "$.installments[0].statementId");
        mvc.perform(get("/api/card-statements/{id}", nextStatement).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount").value(30))
                .andExpect(jsonPath("$.projectedCredits").value(20))
                .andExpect(jsonPath("$.appliedCredits").value(0));
        // Advance the stored credit's effective timestamp to simulate a later
        // closing without relying on wall-clock sleeps or a month-long test.
        jdbc.update("UPDATE credito_estorno SET criado_em = ? WHERE compra_cartao_id = ?",
                today.minusDays(2).atStartOfDay().atOffset(ZoneOffset.UTC), id);
        jdbc.update("""
                UPDATE fatura_cartao SET data_fechamento = ?, data_vencimento = ?
                WHERE id = ?
                """, today.minusDays(1), today.plusDays(10), nextStatement);
        processor.processDue();
        processor.processDue();
        mvc.perform(get("/api/card-statements/{id}", nextStatement).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount").value(30))
                .andExpect(jsonPath("$.appliedCredits").value(20))
                .andExpect(jsonPath("$.closedAmount").value(10));
        mvc.perform(get("/api/card-credits").param("month",
                        java.time.YearMonth.now(ZoneOffset.UTC).toString()).cookie(owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].availableAmount").value(0));
        mvc.perform(get("/api/monthly-summary").param("month",
                        value(newPurchase, "$.installments[0].statementReferenceMonth")).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.realizedExpenses").value(30))
                .andExpect(jsonPath("$.reversalCreditsApplied").value(20));
        mvc.perform(payment(csrf, owner, nextStatement, wallet, null, today))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.amount").value(10));
    }

    @Test
    void olderCreditsAreConsumedFirstAndCarryIntoLaterStatements() throws Exception {
        Cookie csrf = csrf();
        register(csrf, "reverse-order@example.test");
        Cookie owner = login(csrf, "reverse-order@example.test");
        int category = category(csrf, owner, "Loja", "DESPESA");
        int card = card(csrf, owner, "Cartão FIFO", 28, 5);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        MvcResult first = mvc.perform(purchase(csrf, owner, card, category,
                        "Primeira", "20.00", today, 1))
                .andExpect(status().isCreated()).andReturn();
        MvcResult second = mvc.perform(purchase(csrf, owner, card, category,
                        "Segunda", "10.00", today, 1))
                .andExpect(status().isCreated()).andReturn();
        int original = intValue(first, "$.installments[0].statementId");
        jdbc.update("""
                UPDATE fatura_cartao SET data_fechamento = ?, data_vencimento = ?
                WHERE id = ?
                """, today.minusDays(5), today.plusDays(5), original);
        processor.processDue();
        int firstPurchase = intValue(first, "$.id");
        int secondPurchase = intValue(second, "$.id");
        mvc.perform(post("/api/card-purchases/{id}/reversal", firstPurchase)
                        .cookie(owner, csrf).header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/card-purchases/{id}/reversal", secondPurchase)
                        .cookie(owner, csrf).header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isOk());
        jdbc.update("""
                UPDATE credito_estorno SET criado_em = ? WHERE compra_cartao_id = ?
                """, today.minusDays(3).atStartOfDay().atOffset(ZoneOffset.UTC), firstPurchase);
        jdbc.update("""
                UPDATE credito_estorno SET criado_em = ? WHERE compra_cartao_id = ?
                """, today.minusDays(2).atStartOfDay().atOffset(ZoneOffset.UTC), secondPurchase);

        MvcResult third = mvc.perform(purchase(csrf, owner, card, category,
                        "Terceira", "15.00", today.plusMonths(1), 1))
                .andExpect(status().isCreated()).andReturn();
        MvcResult fourth = mvc.perform(purchase(csrf, owner, card, category,
                        "Quarta", "20.00", today.plusMonths(2), 1))
                .andExpect(status().isCreated()).andReturn();
        int thirdStatement = intValue(third, "$.installments[0].statementId");
        int fourthStatement = intValue(fourth, "$.installments[0].statementId");
        mvc.perform(get("/api/card-statements/{id}", thirdStatement).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectedCredits").value(15));
        mvc.perform(get("/api/card-statements/{id}", fourthStatement).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectedCredits").value(15));
        jdbc.update("UPDATE fatura_cartao SET data_fechamento = ?, data_vencimento = ? WHERE id = ?",
                today.minusDays(2), today.plusDays(5), thirdStatement);
        jdbc.update("UPDATE fatura_cartao SET data_fechamento = ?, data_vencimento = ? WHERE id = ?",
                today.minusDays(1), today.plusDays(6), fourthStatement);
        processor.processDue();
        processor.processDue();

        mvc.perform(get("/api/card-statements/{id}", thirdStatement).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedCredits").value(15))
                .andExpect(jsonPath("$.closedAmount").value(0))
                .andExpect(jsonPath("$.status").value("PAGA"));
        mvc.perform(get("/api/card-statements/{id}", fourthStatement).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedCredits").value(15))
                .andExpect(jsonPath("$.closedAmount").value(5));
        mvc.perform(get("/api/card-credits").param("month",
                        java.time.YearMonth.from(today).toString()).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].availableAmount").value(0))
                .andExpect(jsonPath("$[1].availableAmount").value(0));
        Number firstApplied = jdbc.queryForObject("""
                SELECT SUM(a.valor) FROM aplicacao_credito a
                JOIN credito_estorno c ON c.id = a.credito_estorno_id
                WHERE c.compra_cartao_id = ?
                """, BigDecimal.class, firstPurchase);
        assertThat(firstApplied.doubleValue()).isEqualTo(20.0);
    }

    @Test
    void closesTheStatementAndPaysItInFull() throws Exception {
        Cookie csrf = csrf();
        register(csrf, "pay-owner@example.test");
        Cookie owner = login(csrf, "pay-owner@example.test");
        int walletId = wallet(csrf, owner);
        int categoryId = category(csrf, owner, "Mercado", "DESPESA");
        int cardId = card(csrf, owner, "Cartão Pago", 28, 5);

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        MvcResult purchase = mvc.perform(purchase(csrf, owner, cardId, categoryId, "Compras",
                        "75.00", today, 1))
                .andExpect(status().isCreated()).andReturn();
        int statementId = intValue(purchase, "$.installments[0].statementId");
        String month = value(purchase, "$.installments[0].statementReferenceMonth");

        jdbc.update("""
                UPDATE fatura_cartao SET data_fechamento = ?, data_vencimento = ?
                WHERE id = ?
                """, today.minusDays(5), today.plusDays(5), statementId);
        processor.processDue();

        mvc.perform(get("/api/card-statements/{id}", statementId).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FECHADA"))
                .andExpect(jsonPath("$.closedAmount").value(75.00))
                .andExpect(jsonPath("$.paidAmount").value(0));

        mvc.perform(payment(csrf, owner, statementId, walletId, null, today))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(75.00))
                .andExpect(jsonPath("$.walletId").value(walletId))
                .andExpect(jsonPath("$.statementId").value(statementId));

        mvc.perform(get("/api/card-statements/{id}", statementId).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAGA"))
                .andExpect(jsonPath("$.paidAmount").value(75.00))
                .andExpect(jsonPath("$.paidOn").value(today.toString()));

        mvc.perform(get("/api/wallets/{id}", walletId).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimatedBalance").value(25.00));

        mvc.perform(get("/api/monthly-summary").param("month", month).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.realizedExpenses").value(75.00))
                .andExpect(jsonPath("$.plannedExpenses").value(0));

        mvc.perform(get("/api/entries").param("month", month).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void marksUnpaidStatementsAsOverdueAndStillPaysThem() throws Exception {
        Cookie csrf = csrf();
        register(csrf, "overdue-owner@example.test");
        Cookie owner = login(csrf, "overdue-owner@example.test");
        int walletId = wallet(csrf, owner);
        int categoryId = category(csrf, owner, "Mercado", "DESPESA");
        int cardId = card(csrf, owner, "Cartão Atrasado", 28, 5);

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        MvcResult purchase = mvc.perform(purchase(csrf, owner, cardId, categoryId, "Compras",
                        "40.00", today, 1))
                .andExpect(status().isCreated()).andReturn();
        int statementId = intValue(purchase, "$.installments[0].statementId");

        jdbc.update("""
                UPDATE fatura_cartao SET data_fechamento = ?, data_vencimento = ?
                WHERE id = ?
                """, today.minusDays(20), today.minusDays(2), statementId);
        processor.processDue();

        mvc.perform(get("/api/card-statements/{id}", statementId).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ATRASADA"));

        mvc.perform(payment(csrf, owner, statementId, walletId, null, today))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/card-statements/{id}", statementId).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAGA"));
    }

    @Test
    void closesStatementsWithoutChargesAsPaid() throws Exception {
        Cookie csrf = csrf();
        register(csrf, "empty-owner@example.test");
        Cookie owner = login(csrf, "empty-owner@example.test");
        int cardId = card(csrf, owner, "Cartão Sem Compras", 28, 5);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        jdbc.update("""
                INSERT INTO fatura_cartao
                    (owner_user_id, cartao_id, mes_referencia, data_fechamento,
                     data_vencimento, status, criado_em)
                SELECT c.owner_user_id, c.id, ?, ?, ?, 'ABERTA', now()
                FROM cartao_credito c WHERE c.id = ?
                """, today.minusDays(40).withDayOfMonth(1), today.minusDays(30),
                today.minusDays(20), cardId);
        processor.processDue();

        mvc.perform(get("/api/card-statements").param("month",
                        today.minusDays(40).withDayOfMonth(1).toString().substring(0, 7))
                        .cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("PAGA"))
                .andExpect(jsonPath("$[0].closedAmount").value(0))
                .andExpect(jsonPath("$[0].paidAmount").value(0));
    }

    @Test
    void refusesPaymentsOutsideTheRules() throws Exception {
        Cookie csrf = csrf();
        register(csrf, "pay-rules@example.test");
        register(csrf, "pay-rules-other@example.test");
        Cookie owner = login(csrf, "pay-rules@example.test");
        Cookie other = login(csrf, "pay-rules-other@example.test");
        int walletId = wallet(csrf, owner);
        int categoryId = category(csrf, owner, "Mercado", "DESPESA");
        int cardId = card(csrf, owner, "Cartão Regras", 28, 5);

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        MvcResult purchase = mvc.perform(purchase(csrf, owner, cardId, categoryId, "Compras",
                        "75.00", today, 1))
                .andExpect(status().isCreated()).andReturn();
        int statementId = intValue(purchase, "$.installments[0].statementId");

        mvc.perform(payment(csrf, owner, statementId, walletId, null, today))
                .andExpect(status().isConflict());
        mvc.perform(payment(csrf, owner, 999, walletId, null, today))
                .andExpect(status().isNotFound());

        jdbc.update("""
                UPDATE fatura_cartao SET data_fechamento = ?, data_vencimento = ?
                WHERE id = ?
                """, today.minusDays(5), today.plusDays(5), statementId);
        processor.processDue();

        mvc.perform(payment(csrf, owner, statementId, walletId, "10.00", today))
                .andExpect(status().isBadRequest());
        mvc.perform(payment(csrf, owner, statementId, walletId, null, today.minusDays(10)))
                .andExpect(status().isBadRequest());
        mvc.perform(payment(csrf, other, statementId, walletId, null, today))
                .andExpect(status().isNotFound());
        mvc.perform(payment(csrf, owner, statementId, 999, null, today))
                .andExpect(status().isNotFound());
        mvc.perform(payment(csrf, owner, statementId, walletId, null, today.plusDays(2)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void correctsTheEntityOfAClosedPurchaseWithoutChangingAmounts() throws Exception {
        Cookie csrf = csrf();
        register(csrf, "card-entity-link@example.test");
        register(csrf, "card-entity-link-other@example.test");
        Cookie owner = login(csrf, "card-entity-link@example.test");
        Cookie other = login(csrf, "card-entity-link-other@example.test");
        int category = category(csrf, owner, "Loja", "DESPESA");
        int card = card(csrf, owner, "Cartão Vínculo", 28, 5);
        int first = entity(csrf, owner, "Fornecedor A");
        int second = entity(csrf, owner, "Fornecedor B");
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        MvcResult purchase = mvc.perform(purchase(csrf, owner, card, category, "Compra vinculada",
                        "50.00", today, 1))
                .andExpect(status().isCreated()).andReturn();
        int purchaseId = intValue(purchase, "$.id");
        int statementId = intValue(purchase, "$.installments[0].statementId");
        jdbc.update("""
                UPDATE fatura_cartao SET data_fechamento = ?, data_vencimento = ?
                WHERE id = ?
                """, today.minusDays(5), today.plusDays(5), statementId);
        processor.processDue();

        mvc.perform(put("/api/card-purchases/{id}/entity", purchaseId).cookie(csrf, owner)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("{\"entityId\":%d}".formatted(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entityId").value(first))
                .andExpect(jsonPath("$.installments[0].entityName").value("Fornecedor A"))
                .andExpect(jsonPath("$.totalAmount").value(50.00));
        mvc.perform(get("/api/card-statements/{id}", statementId).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FECHADA"))
                .andExpect(jsonPath("$.closedAmount").value(50.00))
                .andExpect(jsonPath("$.installments[0].entityName").value("Fornecedor A"));

        mvc.perform(put("/api/card-purchases/{id}/entity", purchaseId).cookie(csrf, other)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("{\"entityId\":%d}".formatted(second)))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/card-purchases/{id}/entity", purchaseId).cookie(csrf, owner)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("{\"entityId\":%d}".formatted(second)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.installments[0].entityName").value("Fornecedor B"));
        mvc.perform(get("/api/card-statements/{id}", statementId).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closedAmount").value(50.00))
                .andExpect(jsonPath("$.installments[0].entityName").value("Fornecedor B"));
    }

    private int entity(Cookie csrf, Cookie access, String name) throws Exception {
        return id(mvc.perform(post("/api/entities")
                        .cookie(csrf, access).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("{\"name\":\"%s\"}".formatted(name)))
                .andExpect(status().isCreated()).andReturn());
    }

    private MockHttpServletRequestBuilder payment(Cookie csrf, Cookie access, int statementId,
            int walletId, String amount, LocalDate paidOn) {
        return post("/api/card-statements/{id}/payments", statementId)
                .cookie(csrf, access).header("X-XSRF-TOKEN", csrf.getValue())
                .contentType("application/json")
                .content("""
                        {"walletId":%d,"amount":%s,"paidOn":"%sT00:00:00Z"}
                        """.formatted(walletId, amount == null ? "null" : amount, paidOn));
    }

    private MockHttpServletRequestBuilder purchase(Cookie csrf, Cookie access, int cardId,
            int categoryId, String description, String amount, LocalDate purchasedOn,
            int installments) {
        return post("/api/card-purchases")
                .cookie(csrf, access).header("X-XSRF-TOKEN", csrf.getValue())
                .contentType("application/json")
                .content("""
                        {"cardId":%d,"categoryId":%d,"description":"%s","amount":%s,
                         "purchasedOn":"%s","installments":%d,"necessity":"NECESSARIA"}
                        """.formatted(cardId, categoryId, description, amount, purchasedOn,
                        installments));
    }

    private int card(Cookie csrf, Cookie access, String name, int closingDay, int dueDay)
            throws Exception {
        return id(mvc.perform(post("/api/cards")
                        .cookie(csrf, access).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"name":"%s","institutionName":"Banco Teste",
                                 "closingDay":%d,"dueDay":%d}
                                """.formatted(name, closingDay, dueDay)))
                .andExpect(status().isCreated()).andReturn());
    }

    private int wallet(Cookie csrf, Cookie access) throws Exception {
        return id(mvc.perform(post("/api/wallets")
                        .cookie(csrf, access).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"name":"Dinheiro do cartão","initialBalance":100.00,
                                 "initialBalanceDate":"2020-01-01"}
                                """))
                .andExpect(status().isCreated()).andReturn());
    }

    private int category(Cookie csrf, Cookie access, String name, String kind)
            throws Exception {
        return id(mvc.perform(post("/api/categories")
                        .cookie(csrf, access).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"name":"%s","kind":"%s"}
                                """.formatted(name, kind)))
                .andExpect(status().isCreated()).andReturn());
    }

    private Cookie csrf() throws Exception {
        Cookie token = mvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isNoContent()).andReturn()
                .getResponse().getCookie("XSRF-TOKEN");
        assertThat(token).isNotNull();
        return token;
    }

    private void register(Cookie csrf, String email) throws Exception {
        mvc.perform(post("/api/auth/register")
                        .cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"name":"Teste","email":"%s","password":"senha-de-teste-segura"}
                                """.formatted(email)))
                .andExpect(status().isCreated());
    }

    private int id(MvcResult response) throws Exception {
        return Integer.parseInt(com.jayway.jsonpath.JsonPath.read(
                response.getResponse().getContentAsString(), "$.id").toString());
    }

    private String value(MvcResult response, String path) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(
                response.getResponse().getContentAsString(), path).toString();
    }

    private int intValue(MvcResult response, String path) throws Exception {
        return Integer.parseInt(value(response, path));
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

    @SuppressWarnings("unused")
    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
