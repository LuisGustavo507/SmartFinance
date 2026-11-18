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
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
class WalletIntegrationTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void createsAccountsWithTypeAndInstitutionFollowingTheModel() throws Exception {
        Cookie csrf = csrf();
        int ownerId = register(csrf, "wallet-type@example.test");
        Cookie access = login(csrf, "wallet-type@example.test");

        mvc.perform(get("/api/institutions").cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(8))
                .andExpect(jsonPath("$[?(@.code == 'OUTRA')].name").value("Outra instituição"));

        mvc.perform(post("/api/wallets").cookie(csrf, access)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"name":"Conta corrente","type":"CONTA_CORRENTE",
                                 "institutionName":"Banco do Brasil","initialBalance":100,
                                 "initialBalanceDate":"2026-01-01"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("CONTA_CORRENTE"))
                .andExpect(jsonPath("$.institutionName").value("Banco do Brasil"));
        mvc.perform(post("/api/wallets").cookie(csrf, access)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"name":"Conta de farmácia","type":"CONTA_PAGAMENTO",
                                 "institutionName":"Farmácia Popular","initialBalance":20,
                                 "initialBalanceDate":"2026-01-01"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("CONTA_PAGAMENTO"))
                .andExpect(jsonPath("$.institutionName").value("Farmácia Popular"));
        mvc.perform(post("/api/wallets").cookie(csrf, access)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"name":"Dinheiro","initialBalance":5,
                                 "initialBalanceDate":"2026-01-01"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("CARTEIRA_DINHEIRO"))
                .andExpect(jsonPath("$.institutionName").isEmpty());

        mvc.perform(post("/api/wallets").cookie(csrf, access)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"name":"Sem instituição","type":"POUVANCA",
                                 "initialBalance":10,"initialBalanceDate":"2026-01-01"}
                                """))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/wallets").cookie(csrf, access)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"name":"Tipo inválido","type":"CARTAO",
                                 "institutionName":"Banco Teste","initialBalance":10,
                                 "initialBalanceDate":"2026-01-01"}
                                """))
                .andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM conta_financeira
                WHERE owner_user_id = ? AND tipo = 'CARTEIRA_DINHEIRO'
                    AND instituicao_id IS NULL AND instituicao_nome_outro IS NULL
                """, Integer.class, ownerId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM conta_financeira c
                JOIN instituicao_financeira i ON i.id = c.instituicao_id
                WHERE c.owner_user_id = ? AND i.codigo = 'BANCO_DO_BRASIL'
                """, Integer.class, ownerId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM conta_financeira c
                JOIN instituicao_financeira i ON i.id = c.instituicao_id
                WHERE c.owner_user_id = ? AND i.codigo = 'OUTRA'
                    AND c.instituicao_nome_outro = 'Farmácia Popular'
                """, Integer.class, ownerId)).isEqualTo(1);

        mvc.perform(get("/api/wallets").cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void userCanCreateAndReadOnlyOwnWallets() throws Exception {
        Cookie aliceCsrf = csrf();
        Cookie bobCsrf = csrf();
        int aliceId = register(aliceCsrf, "wallet-alice@example.test");
        register(bobCsrf, "wallet-bob@example.test");
        Cookie aliceAccess = login(aliceCsrf, "wallet-alice@example.test");
        Cookie bobAccess = login(bobCsrf, "wallet-bob@example.test");

        MvcResult created = mvc.perform(post("/api/wallets")
                        .cookie(aliceCsrf, aliceAccess)
                        .header("X-XSRF-TOKEN", aliceCsrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"name":"Dinheiro","initialBalance":25.50,
                                 "initialBalanceDate":"2026-01-01","ownerUserId":999999}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownerUserId").value(aliceId))
                .andExpect(jsonPath("$.initialBalance").value(25.50))
                .andReturn();
        int walletId = Integer.parseInt(com.jayway.jsonpath.JsonPath.read(
                created.getResponse().getContentAsString(), "$.id").toString());

        mvc.perform(get("/api/wallets").cookie(aliceAccess))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/wallets").cookie(bobAccess))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/wallets/{id}", walletId).cookie(bobAccess))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/wallets/{id}", walletId).cookie(aliceAccess))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Dinheiro"));
        mvc.perform(get("/api/wallets")).andExpect(status().isUnauthorized());

        mvc.perform(post("/api/wallets")
                        .cookie(aliceCsrf, aliceAccess)
                        .header("X-XSRF-TOKEN", aliceCsrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"name":"Dinheiro","initialBalance":0,
                                 "initialBalanceDate":"2026-01-01"}
                                """))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/wallets")
                        .cookie(bobCsrf, bobAccess)
                        .header("X-XSRF-TOKEN", bobCsrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"name":"Dinheiro","initialBalance":0,
                                 "initialBalanceDate":"2026-01-01"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void walletCreationNeedsCsrfAndValidMoney() throws Exception {
        Cookie csrf = csrf();
        register(csrf, "wallet-validation@example.test");
        Cookie access = login(csrf, "wallet-validation@example.test");

        mvc.perform(post("/api/wallets").cookie(access)
                        .contentType("application/json")
                        .content("""
                                {"name":"Dinheiro","initialBalance":0,
                                 "initialBalanceDate":"2026-01-01"}
                                """))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/wallets").cookie(csrf, access)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"name":"Dinheiro","initialBalance":0.001,
                                 "initialBalanceDate":"2026-01-01"}
                                """))
                .andExpect(status().isBadRequest());
    }

    private Cookie csrf() throws Exception {
        Cookie token = mvc.perform(get("/api/auth/csrf"))
                .andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(token).isNotNull();
        return token;
    }

    private int register(Cookie csrf, String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/register")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"name":"Teste","email":"%s","password":"senha-de-teste-segura"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        return Integer.parseInt(com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.id").toString());
    }

    private Cookie login(Cookie csrf, String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"email":"%s","password":"senha-de-teste-segura"}
                                """.formatted(email)))
                .andExpect(status().isNoContent())
                .andReturn();
        String header = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(value -> value.startsWith("SF_ACCESS="))
                .findFirst().orElseThrow();
        return new Cookie("SF_ACCESS", header.substring("SF_ACCESS=".length(),
                header.indexOf(';')));
    }
}
