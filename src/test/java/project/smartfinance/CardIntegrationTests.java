package project.smartfinance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.YearMonth;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
class CardIntegrationTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void editsCardAndLocksCycleDaysAfterTheFirstPurchase() throws Exception {
        Cookie csrf = csrf();
        register(csrf, "card-edit@example.test");
        register(csrf, "card-edit-other@example.test");
        Cookie owner = login(csrf, "card-edit@example.test");
        Cookie other = login(csrf, "card-edit-other@example.test");
        int category = category(csrf, owner, "Mercado", "DESPESA");
        int walletId = wallet(csrf, owner);
        int cardId = id(mvc.perform(card(csrf, owner, "Cartão Editável",
                        "Banco Teste", 28, 5, null, null))
                .andExpect(status().isCreated()).andReturn());
        mvc.perform(put("/api/cards/{id}", cardId).cookie(csrf, owner)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json").content("""
                                {"name":"Cartão Renovado","institutionName":"Outra instituição",
                                 "closingDay":25,"dueDay":10,"creditLimit":1200,
                                 "defaultPaymentWalletId":%d}
                                """.formatted(walletId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closingDay").value(25))
                .andExpect(jsonPath("$.defaultPaymentWalletId").value(walletId));
        mvc.perform(purchase(csrf, owner, cardId, category, "Compra", "10.00",
                        LocalDate.now(ZoneOffset.UTC), 1, null, "NECESSARIA"))
                .andExpect(status().isCreated());
        mvc.perform(put("/api/cards/{id}", cardId).cookie(csrf, owner)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json").content("""
                                {"name":"Cartão Renovado","institutionName":"Outra instituição",
                                 "closingDay":28,"dueDay":5}
                                """))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/cards/{id}", cardId).cookie(csrf, owner)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json").content("""
                                {"name":"Cartão Renomeado","institutionName":"Banco Teste",
                                 "closingDay":25,"dueDay":10,"creditLimit":1500}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cartão Renomeado"))
                .andExpect(jsonPath("$.creditLimit").value(1500));
        mvc.perform(patch("/api/cards/{id}/status", cardId).cookie(csrf, owner)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json").content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(purchase(csrf, owner, cardId, category, "Bloqueada", "10.00",
                        LocalDate.now(ZoneOffset.UTC), 1, null, "NECESSARIA"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/cards/{id}", cardId).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(patch("/api/cards/{id}/status", cardId).cookie(csrf, other)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json").content("{\"active\":true}"))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/cards/{id}/status", cardId).cookie(csrf, owner)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json").content("{\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void registersCardAndKeepsItsRules() throws Exception {
        Cookie csrf = csrf();
        register(csrf, "card-owner@example.test");
        Cookie owner = login(csrf, "card-owner@example.test");
        int walletId = wallet(csrf, owner);

        MvcResult created = mvc.perform(card(csrf, owner, "Cartão Principal", "Banco Teste", 28,
                        5, "5000.00", walletId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Cartão Principal"))
                .andExpect(jsonPath("$.institutionName").value("Banco Teste"))
                .andExpect(jsonPath("$.closingDay").value(28))
                .andExpect(jsonPath("$.dueDay").value(5))
                .andExpect(jsonPath("$.creditLimit").value(5000.00))
                .andExpect(jsonPath("$.defaultPaymentWalletId").value(walletId))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn();
        int cardId = id(created);

        mvc.perform(get("/api/cards").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Cartão Principal"));

        mvc.perform(get("/api/cards/{id}", cardId).cookie(owner))
                .andExpect(status().isOk());
        mvc.perform(get("/api/cards/999").cookie(owner))
                .andExpect(status().isNotFound());

        mvc.perform(card(csrf, owner, "Cartão Principal", "Banco Teste", 28, 5, "1000.00", null))
                .andExpect(status().isConflict());
        mvc.perform(card(csrf, owner, "Outro cartão", "Banco Teste", 0, 5, null, null))
                .andExpect(status().isBadRequest());
        mvc.perform(card(csrf, owner, "Outro cartão", "Banco Teste", 29, 5, null, null))
                .andExpect(status().isBadRequest());
        mvc.perform(card(csrf, owner, "Outro cartão", "Banco Teste", 28, 31, null, null))
                .andExpect(status().isBadRequest());
        mvc.perform(card(csrf, owner, "Outro cartão", "Banco Teste", 28, 5, "0.00", null))
                .andExpect(status().isBadRequest());
        mvc.perform(card(csrf, owner, "", "Banco Teste", 28, 5, null, null))
                .andExpect(status().isBadRequest());
        mvc.perform(card(csrf, owner, "Outro cartão", "", 28, 5, null, null))
                .andExpect(status().isBadRequest());
        mvc.perform(card(csrf, owner, "Outro cartão", "Banco Teste", 28, 5, null, 999))
                .andExpect(status().isNotFound());
    }

    @Test
    void registersPurchaseAndBuildsTheStatementCycle() throws Exception {
        Cookie csrf = csrf();
        register(csrf, "card-purchase@example.test");
        Cookie owner = login(csrf, "card-purchase@example.test");
        int walletId = wallet(csrf, owner);
        int expenseCategory = category(csrf, owner, "Mercado", "DESPESA");
        int cardId = id(mvc.perform(card(csrf, owner, "Cartão Gold", "Banco Teste", 28, 5,
                        null, walletId))
                .andExpect(status().isCreated()).andReturn());

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate purchaseDate = today;
        LocalDate closing = closingOnOrAfter(28, purchaseDate);
        LocalDate due = dueDate(28, 5, closing);
        YearMonth referenceMonth = YearMonth.from(due);

        mvc.perform(purchase(csrf, owner, cardId, expenseCategory, "Compras do mês",
                        "100.00", purchaseDate, 3, null, "NECESSARIA"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalAmount").value(100.00))
                .andExpect(jsonPath("$.installmentCount").value(3))
                .andExpect(jsonPath("$.necessity").value("NECESSARIA"))
                .andExpect(jsonPath("$.status").value("ATIVA"))
                .andExpect(jsonPath("$.purchasedOn").value(purchaseDate.toString()))
                .andExpect(jsonPath("$.installments.length()").value(3))
                .andExpect(jsonPath("$.installments[0].number").value(1))
                .andExpect(jsonPath("$.installments[0].amount").value(33.34))
                .andExpect(jsonPath("$.installments[1].amount").value(33.33))
                .andExpect(jsonPath("$.installments[2].amount").value(33.33))
                .andExpect(jsonPath("$.installments[0].statementDueDate").value(due.toString()))
                .andExpect(jsonPath("$.installments[0].statementReferenceMonth")
                        .value(referenceMonth.toString()));

        mvc.perform(get("/api/card-statements").param("month", referenceMonth.toString())
                        .cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].cardId").value(cardId))
                .andExpect(jsonPath("$[0].referenceMonth").value(referenceMonth.toString()))
                .andExpect(jsonPath("$[0].closingDate").value(closing.toString()))
                .andExpect(jsonPath("$[0].dueDate").value(due.toString()))
                .andExpect(jsonPath("$[0].status").value("ABERTA"))
                .andExpect(jsonPath("$[0].totalAmount").value(33.34))
                .andExpect(jsonPath("$[0].installments.length()").value(1))
                .andExpect(jsonPath("$[0].installments[0].description")
                        .value("Compras do mês"))
                .andExpect(jsonPath("$[0].installments[0].categoryName").value("Mercado"));

        YearMonth nextMonth = referenceMonth.plusMonths(1);
        mvc.perform(get("/api/card-statements").param("month", nextMonth.toString())
                        .cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("PROJETADA"))
                .andExpect(jsonPath("$[0].totalAmount").value(33.33));

        mvc.perform(get("/api/monthly-summary").param("month", referenceMonth.toString())
                        .cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plannedExpenses").value(33.34))
                .andExpect(jsonPath("$.realizedExpenses").value(0))
                .andExpect(jsonPath("$.necessityTotals[0].necessity").value("NECESSARIA"))
                .andExpect(jsonPath("$.necessityTotals[0].plannedExpenses").value(33.34))
                .andExpect(jsonPath("$.categoryTotals.length()").value(1))
                .andExpect(jsonPath("$.categoryTotals[0].categoryName").value("Mercado"))
                .andExpect(jsonPath("$.categoryTotals[0].kind").value("DESPESA"))
                .andExpect(jsonPath("$.categoryTotals[0].plannedExpenses").value(33.34))
                .andExpect(jsonPath("$.categoryTotals[0].realizedExpenses").value(0));
        mvc.perform(get("/api/monthly-summary").param("month", nextMonth.toString())
                        .cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plannedExpenses").value(33.33));

        mvc.perform(get("/api/wallets/{id}", walletId).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimatedBalance").value(100.00));
    }

    @Test
    void acceptsPurchaseWithoutDescription() throws Exception {
        Cookie csrf = csrf();
        register(csrf, "card-optional-description@example.test");
        Cookie owner = login(csrf, "card-optional-description@example.test");
        int category = category(csrf, owner, "Mercado", "DESPESA");
        int cardId = id(mvc.perform(card(csrf, owner, "Cartão sem descrição",
                "Banco Teste", 28, 5, null, null)).andExpect(status().isCreated()).andReturn());
        mvc.perform(post("/api/card-purchases").cookie(csrf, owner)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"cardId":%d,"categoryId":%d,"amount":1,
                                 "purchasedOn":"%s","installments":1,"necessity":"NEUTRA"}
                                """.formatted(cardId, category, LocalDate.now(ZoneOffset.UTC))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value(""));
    }

    @Test
    void placesFuturePurchasesOnProjectedStatements() throws Exception {
        Cookie csrf = csrf();
        register(csrf, "card-future@example.test");
        Cookie owner = login(csrf, "card-future@example.test");
        int expenseCategory = category(csrf, owner, "Mercado", "DESPESA");
        int cardId = id(mvc.perform(card(csrf, owner, "Cartão Future", "Banco Teste", 10, 20,
                        null, null))
                .andExpect(status().isCreated()).andReturn());

        LocalDate purchaseDate = LocalDate.now(ZoneOffset.UTC).plusMonths(2);
        LocalDate closing = closingOnOrAfter(10, purchaseDate);
        LocalDate due = dueDate(10, 20, closing);
        YearMonth referenceMonth = YearMonth.from(due);

        mvc.perform(purchase(csrf, owner, cardId, expenseCategory, "Compra futura", "60.00",
                        purchaseDate, 2, null, "NEUTRA"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.installments[0].amount").value(30.00))
                .andExpect(jsonPath("$.installments[1].amount").value(30.00));

        mvc.perform(get("/api/card-statements").param("month", referenceMonth.toString())
                        .cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("PROJETADA"))
                .andExpect(jsonPath("$[0].closingDate").value(closing.toString()))
                .andExpect(jsonPath("$[0].dueDate").value(due.toString()))
                .andExpect(jsonPath("$[0].totalAmount").value(30.00));

        mvc.perform(get("/api/monthly-summary").param("month", referenceMonth.toString())
                        .cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plannedExpenses").value(30.00));
    }

    @Test
    void isolatesCardsAndPurchasesByOwner() throws Exception {
        Cookie csrf = csrf();
        register(csrf, "card-iso-owner@example.test");
        register(csrf, "card-iso-other@example.test");
        Cookie owner = login(csrf, "card-iso-owner@example.test");
        Cookie other = login(csrf, "card-iso-other@example.test");
        int expenseCategory = category(csrf, owner, "Mercado", "DESPESA");
        int cardId = id(mvc.perform(card(csrf, owner, "Cartão Privado", "Banco Teste", 28, 5,
                        null, null))
                .andExpect(status().isCreated()).andReturn());

        mvc.perform(get("/api/cards").cookie(other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/cards/{id}", cardId).cookie(other))
                .andExpect(status().isNotFound());
        mvc.perform(purchase(csrf, other, cardId, expenseCategory, "Alheia", "10.00",
                        LocalDate.now(ZoneOffset.UTC), 1, null, "NECESSARIA"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/card-statements")
                        .param("month", YearMonth.now(ZoneOffset.UTC).toString())
                        .cookie(other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void rejectsPurchasesOutsideTheRules() throws Exception {
        Cookie csrf = csrf();
        register(csrf, "card-rules@example.test");
        register(csrf, "card-rules-other@example.test");
        Cookie owner = login(csrf, "card-rules@example.test");
        Cookie other = login(csrf, "card-rules-other@example.test");
        int expenseCategory = category(csrf, owner, "Mercado", "DESPESA");
        int incomeCategory = category(csrf, owner, "Salário", "RECEITA");
        int foreignCategory = category(csrf, other, "Alheia", "DESPESA");
        int cardId = id(mvc.perform(card(csrf, owner, "Cartão Regras", "Banco Teste", 28, 5,
                        null, null))
                .andExpect(status().isCreated()).andReturn());
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        mvc.perform(purchase(csrf, owner, cardId, incomeCategory, "Receita", "10.00", today,
                        1, null, "NECESSARIA"))
                .andExpect(status().isBadRequest());
        mvc.perform(purchase(csrf, owner, cardId, foreignCategory, "Categoria alheia", "10.00",
                        today, 1, null, "NECESSARIA"))
                .andExpect(status().isNotFound());
        mvc.perform(purchase(csrf, owner, cardId, expenseCategory, "Valor inválido", "0.00",
                        today, 1, null, "NECESSARIA"))
                .andExpect(status().isBadRequest());
        mvc.perform(purchase(csrf, owner, cardId, expenseCategory, "Parcelas demais", "0.01",
                        today, 3, null, "NECESSARIA"))
                .andExpect(status().isBadRequest());
        mvc.perform(purchase(csrf, owner, cardId, expenseCategory, "Ciclo encerrado", "10.00",
                        today.minusMonths(3), 1, null, "NECESSARIA"))
                .andExpect(status().isConflict());
        mvc.perform(purchase(csrf, owner, cardId, expenseCategory, "Entidade alheia", "10.00",
                        today, 1, 999, "NECESSARIA"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/card-purchases")
                        .cookie(owner)
                        .contentType("application/json")
                        .content("""
                                {"cardId":%d,"categoryId":%d,"description":"Sem CSRF",
                                 "amount":10.00,"purchasedOn":"%s","installments":1,
                                 "necessity":"NECESSARIA"}
                                """.formatted(cardId, expenseCategory, today)))
                .andExpect(status().isForbidden());
        MvcResult existing = mvc.perform(purchase(csrf, owner, cardId, expenseCategory,
                        "Compra preservada", "10.00", today, 1, null, "NECESSARIA"))
                .andExpect(status().isCreated()).andReturn();
        int statementId = com.jayway.jsonpath.JsonPath.read(
                existing.getResponse().getContentAsString(), "$.installments[0].statementId");
        mvc.perform(patch("/api/categories/{id}/status", expenseCategory)
                        .cookie(csrf, owner).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json").content("{\"active\":false}"))
                .andExpect(status().isOk());
        mvc.perform(purchase(csrf, owner, cardId, expenseCategory,
                        "Categoria inativa", "10.00", today, 1, null, "NECESSARIA"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/card-statements/{id}", statementId).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.installments[0].categoryName").value("Mercado"));
    }

    @Test
    void cardReferencesCatalogInstitutionsAndKeepsUnknownNamesAsOther() throws Exception {
        Cookie csrf = csrf();
        register(csrf, "card-institution@example.test");
        Cookie owner = login(csrf, "card-institution@example.test");
        int ownerId = jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?",
                Integer.class, "card-institution@example.test");
        mvc.perform(card(csrf, owner, "Cartão do catálogo", "Banco do Brasil", 28, 5,
                        null, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.institutionName").value("Banco do Brasil"));
        mvc.perform(card(csrf, owner, "Cartão de outra", "Financiadora Nova", 28, 5,
                        null, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.institutionName").value("Financiadora Nova"));
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM cartao_credito c
                JOIN instituicao_financeira i ON i.id = c.instituicao_id
                WHERE c.owner_user_id = ? AND i.codigo = 'BANCO_DO_BRASIL'
                    AND c.instituicao_nome_outro IS NULL
                """, Integer.class, ownerId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM cartao_credito c
                JOIN instituicao_financeira i ON i.id = c.instituicao_id
                WHERE c.owner_user_id = ? AND i.codigo = 'OUTRA'
                    AND c.instituicao_nome_outro = 'Financiadora Nova'
                """, Integer.class, ownerId)).isEqualTo(1);
    }

    @Test
    void filtersTheMonthlySummaryByTheEntityOfThePurchase() throws Exception {
        Cookie csrf = csrf();
        register(csrf, "card-entity-filter@example.test");
        Cookie owner = login(csrf, "card-entity-filter@example.test");
        int category = category(csrf, owner, "Loja filtrada", "DESPESA");
        int cardId = id(mvc.perform(card(csrf, owner, "Cartão Filtro", "Banco Teste", 28, 5,
                        null, null))
                .andExpect(status().isCreated()).andReturn());
        int entity = id(mvc.perform(post("/api/entities")
                        .cookie(csrf, owner).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json").content("{\"name\":\"Loja da fatura\"}"))
                .andExpect(status().isCreated()).andReturn());
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        MvcResult purchase = mvc.perform(purchase(csrf, owner, cardId, category, "Compra filtrada",
                        "40.00", today, 1, entity, "NEUTRA"))
                .andExpect(status().isCreated()).andReturn();
        String month = com.jayway.jsonpath.JsonPath.read(
                purchase.getResponse().getContentAsString(),
                "$.installments[0].statementReferenceMonth").toString();

        mvc.perform(get("/api/monthly-summary").param("month", month)
                        .param("entityId", String.valueOf(entity)).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plannedExpenses").value(40.00))
                .andExpect(jsonPath("$.categoryTotals[0].categoryName").value("Loja filtrada"))
                .andExpect(jsonPath("$.necessityTotals[0].necessity").value("NEUTRA"))
                .andExpect(jsonPath("$.necessityTotals[0].plannedExpenses").value(40.00));
    }

    @Test
    void createsEntityDuringThePurchaseAndRollsItBackWhenThePurchaseFails() throws Exception {
        Cookie csrf = csrf();
        register(csrf, "card-new-entity@example.test");
        Cookie owner = login(csrf, "card-new-entity@example.test");
        int ownerId = jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?",
                Integer.class, "card-new-entity@example.test");
        int expenseCategory = category(csrf, owner, "Loja", "DESPESA");
        int cardId = id(mvc.perform(card(csrf, owner, "Cartão Entidade", "Banco Teste", 28, 5,
                        null, null))
                .andExpect(status().isCreated()).andReturn());
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        mvc.perform(purchase(csrf, owner, cardId, expenseCategory, "Compra com entidade",
                        "30.00", today, 1, null, "NEUTRA", "Loja Nova"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.entityId").isNumber())
                .andExpect(jsonPath("$.installments[0].entityName").value("Loja Nova"));
        mvc.perform(get("/api/entities").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Loja Nova"));

        mvc.perform(purchase(csrf, owner, cardId, expenseCategory, "Ciclo encerrado",
                        "30.00", today.minusMonths(3), 1, null, "NEUTRA", "Loja sem compra"))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM entidade WHERE owner_user_id = ?
                """, Integer.class, ownerId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM compra_cartao WHERE owner_user_id = ?
                """, Integer.class, ownerId)).isEqualTo(1);
    }

    private LocalDate closingOnOrAfter(int closingDay, LocalDate date) {
        YearMonth month = YearMonth.from(date);
        LocalDate candidate = month.atDay(closingDay);
        return candidate.isBefore(date) ? candidate.plusMonths(1) : candidate;
    }

    private LocalDate dueDate(int closingDay, int dueDay, LocalDate closing) {
        YearMonth closingMonth = YearMonth.from(closing);
        return dueDay <= closingDay ? closingMonth.plusMonths(1).atDay(dueDay)
                : closingMonth.atDay(dueDay);
    }

    private MockHttpServletRequestBuilder card(Cookie csrf, Cookie access, String name,
            String institution, int closingDay, int dueDay, String limit,
            Integer walletId) {
        return post("/api/cards")
                .cookie(csrf, access).header("X-XSRF-TOKEN", csrf.getValue())
                .contentType("application/json")
                .content("""
                        {"name":"%s","institutionName":"%s","closingDay":%d,"dueDay":%d,
                         "creditLimit":%s,"defaultPaymentWalletId":%s}
                        """.formatted(name, institution, closingDay, dueDay,
                        limit == null ? "null" : limit,
                        walletId == null ? "null" : walletId));
    }

    private MockHttpServletRequestBuilder purchase(Cookie csrf, Cookie access, int cardId,
            int categoryId, String description, String amount, LocalDate purchasedOn,
            int installments, Integer entityId, String necessity) {
        return purchase(csrf, access, cardId, categoryId, description, amount, purchasedOn,
                installments, entityId, necessity, null);
    }

    private MockHttpServletRequestBuilder purchase(Cookie csrf, Cookie access, int cardId,
            int categoryId, String description, String amount, LocalDate purchasedOn,
            int installments, Integer entityId, String necessity, String newEntityName) {
        return post("/api/card-purchases")
                .cookie(csrf, access).header("X-XSRF-TOKEN", csrf.getValue())
                .contentType("application/json")
                .content("""
                        {"cardId":%d,"categoryId":%d,"description":"%s","amount":%s,
                         "purchasedOn":"%s","installments":%d,"entityId":%s,
                         "necessity":"%s","newEntityName":%s}
                        """.formatted(cardId, categoryId, description, amount, purchasedOn,
                        installments, entityId == null ? "null" : entityId, necessity,
                        newEntityName == null ? "null" : "\"" + newEntityName + "\""));
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
