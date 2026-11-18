package project.smartfinance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
class CategoryIntegrationTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void deactivationKeepsHistoryAndBlocksNewEntriesUntilReactivated() throws Exception {
        Cookie csrf = csrf();
        register(csrf, "category-status-owner@example.test");
        register(csrf, "category-status-other@example.test");
        Cookie owner = login(csrf, "category-status-owner@example.test");
        Cookie other = login(csrf, "category-status-other@example.test");
        int category = id(mvc.perform(post("/api/categories").cookie(csrf, owner)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("{\"name\":\"Alimentação\",\"kind\":\"DESPESA\"}"))
                .andExpect(status().isCreated()).andReturn());
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        int wallet = id(mvc.perform(post("/api/wallets").cookie(csrf, owner)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"name":"Dinheiro categorias","initialBalance":100,
                                 "initialBalanceDate":"%s"}
                                """.formatted(today)))
                .andExpect(status().isCreated()).andReturn());
        mvc.perform(entry(csrf, owner, wallet, category, today))
                .andExpect(status().isCreated());

        mvc.perform(changeStatus(csrf, other, category, false))
                .andExpect(status().isNotFound());
        mvc.perform(changeStatus(csrf, owner, 999999, false))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/categories/{id}/status", category)
                        .cookie(owner).contentType("application/json")
                        .content("{\"active\":false}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/categories/{id}/status", category)
                        .cookie(csrf, owner).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(changeStatus(csrf, owner, category, false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(changeStatus(csrf, owner, category, false))
                .andExpect(status().isOk());
        mvc.perform(get("/api/categories").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].active").value(false));
        mvc.perform(get("/api/entries").param("month", today.toString().substring(0, 7))
                        .cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].categoryId").value(category));
        mvc.perform(get("/api/monthly-summary")
                        .param("month", today.toString().substring(0, 7)).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.realizedExpenses").value(10));
        mvc.perform(entry(csrf, owner, wallet, category, today))
                .andExpect(status().isNotFound());

        mvc.perform(changeStatus(csrf, owner, category, true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
        mvc.perform(entry(csrf, owner, wallet, category, today))
                .andExpect(status().isCreated());
    }

    @Test
    void renameUsedCategoryButOnlyChangeKindOrDeleteWhenUnused() throws Exception {
        Cookie csrf = csrf();
        register(csrf, "category-edits-owner@example.test");
        register(csrf, "category-edits-other@example.test");
        Cookie owner = login(csrf, "category-edits-owner@example.test");
        Cookie other = login(csrf, "category-edits-other@example.test");
        int used = id(mvc.perform(post("/api/categories").cookie(csrf, owner)
                .header("X-XSRF-TOKEN", csrf.getValue())
                .contentType("application/json")
                .content("{\"name\":\"Mercado\",\"kind\":\"DESPESA\"}"))
                .andExpect(status().isCreated()).andReturn());
        int unused = id(mvc.perform(post("/api/categories").cookie(csrf, owner)
                .header("X-XSRF-TOKEN", csrf.getValue())
                .contentType("application/json")
                .content("{\"name\":\"Livre\",\"kind\":\"DESPESA\"}"))
                .andExpect(status().isCreated()).andReturn());
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        int wallet = id(mvc.perform(post("/api/wallets").cookie(csrf, owner)
                .header("X-XSRF-TOKEN", csrf.getValue())
                .contentType("application/json")
                .content("""
                        {"name":"Dinheiro edição categoria","initialBalance":0,
                         "initialBalanceDate":"%s"}
                        """.formatted(today)))
                .andExpect(status().isCreated()).andReturn());
        mvc.perform(entry(csrf, owner, wallet, used, today)).andExpect(status().isCreated());

        mvc.perform(put("/api/categories/{id}", used).cookie(csrf, other)
                .header("X-XSRF-TOKEN", csrf.getValue())
                .contentType("application/json")
                .content("{\"name\":\"Invadida\",\"kind\":\"DESPESA\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/categories/{id}", used).cookie(owner)
                .contentType("application/json")
                .content("{\"name\":\"Sem CSRF\",\"kind\":\"DESPESA\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/categories/{id}", used).cookie(csrf, owner)
                .header("X-XSRF-TOKEN", csrf.getValue())
                .contentType("application/json")
                .content("{\"name\":\"Mercado renomeado\",\"kind\":\"DESPESA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Mercado renomeado"));
        mvc.perform(put("/api/categories/{id}", used).cookie(csrf, owner)
                .header("X-XSRF-TOKEN", csrf.getValue())
                .contentType("application/json")
                .content("{\"name\":\"Mercado renomeado\",\"kind\":\"RECEITA\"}"))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/categories/{id}", used).cookie(csrf, owner)
                .header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/categories/{id}", unused).cookie(csrf, owner)
                .header("X-XSRF-TOKEN", csrf.getValue())
                .contentType("application/json")
                .content("{\"name\":\"Livre\",\"kind\":\"RECEITA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("RECEITA"));
        mvc.perform(delete("/api/categories/{id}", unused).cookie(csrf, other)
                .header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/categories/{id}", unused).cookie(csrf, owner)
                .header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/categories").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Mercado renomeado"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder changeStatus(
            Cookie csrf, Cookie owner, int category, boolean active) {
        return patch("/api/categories/{id}/status", category)
                .cookie(csrf, owner).header("X-XSRF-TOKEN", csrf.getValue())
                .contentType("application/json")
                .content("{\"active\":%s}".formatted(active));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder entry(
            Cookie csrf, Cookie owner, int wallet, int category, LocalDate date) {
        return post("/api/entries")
                .cookie(csrf, owner).header("X-XSRF-TOKEN", csrf.getValue())
                .contentType("application/json")
                .content("""
                        {"walletId":%d,"categoryId":%d,"description":"Despesa com categoria",
                         "amount":10,"occurredOn":"%s","necessity":"NECESSARIA"}
                        """.formatted(wallet, category, date));
    }

    private int id(MvcResult response) throws Exception {
        return Integer.parseInt(com.jayway.jsonpath.JsonPath.read(
                response.getResponse().getContentAsString(), "$.id").toString());
    }

    @Test
    void categoriesAreScopedToUserAndKindWithUniqueNames() throws Exception {
        Cookie csrf = csrf();
        int aliceId = register(csrf, "category-alice@example.test");
        register(csrf, "category-bob@example.test");
        Cookie alice = login(csrf, "category-alice@example.test");
        Cookie bob = login(csrf, "category-bob@example.test");

        mvc.perform(post("/api/categories").cookie(csrf, alice)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"name":"  Mercado  ","kind":"DESPESA","ownerUserId":99999}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Mercado"))
                .andExpect(jsonPath("$.kind").value("DESPESA"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.ownerUserId").value(aliceId));
        mvc.perform(post("/api/categories").cookie(csrf, alice)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"name":"Mercado","kind":"DESPESA"}
                                """))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/categories").cookie(csrf, alice)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"name":"Mercado","kind":"RECEITA"}
                                """))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/categories").cookie(csrf, bob)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"name":"Mercado","kind":"DESPESA"}
                                """))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/categories").cookie(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].kind").value("DESPESA"));
        mvc.perform(get("/api/categories").cookie(bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/categories")).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM categoria
                WHERE owner_user_id = ? AND nome = 'Mercado'
                """, Integer.class, aliceId)).isEqualTo(2);
    }

    @Test
    void validatesInputCsrfAndDatabaseConstraints() throws Exception {
        Cookie csrf = csrf();
        int ownerId = register(csrf, "category-validation@example.test");
        Cookie access = login(csrf, "category-validation@example.test");
        String body = """
                {"name":"Alimentação","kind":"DESPESA"}
                """;
        mvc.perform(post("/api/categories").cookie(access)
                        .contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        for (String invalid : new String[] {
                "{\"name\":\" \",\"kind\":\"DESPESA\"}",
                "{\"name\":\"Mercado\",\"kind\":\"OUTRA\"}",
                "{\"name\":\"Mercado\"}"
        }) {
            mvc.perform(post("/api/categories").cookie(csrf, access)
                            .header("X-XSRF-TOKEN", csrf.getValue())
                            .contentType("application/json").content(invalid))
                    .andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM categoria WHERE owner_user_id = ?",
                Integer.class, ownerId)).isZero();

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO categoria (owner_user_id, nome, natureza, criada_em)
                VALUES (?, 'Inválida', 'OUTRA', ?)
                """, ownerId, OffsetDateTime.now()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Cookie csrf() throws Exception {
        Cookie token = mvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isNoContent())
                .andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(token).isNotNull();
        return token;
    }

    private int register(Cookie csrf, String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/register")
                        .cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"name":"Teste","email":"%s","password":"senha-de-teste-segura"}
                                """.formatted(email)))
                .andExpect(status().isCreated()).andReturn();
        return Integer.parseInt(com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.id").toString());
    }

    private Cookie login(Cookie csrf, String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"email":"%s","password":"senha-de-teste-segura"}
                                """.formatted(email)))
                .andExpect(status().isNoContent()).andReturn();
        String header = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(value -> value.startsWith("SF_ACCESS="))
                .findFirst().orElseThrow();
        return new Cookie("SF_ACCESS", header.substring("SF_ACCESS=".length(),
                header.indexOf(';')));
    }
}
