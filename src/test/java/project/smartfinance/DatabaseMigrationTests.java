package project.smartfinance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
class DatabaseMigrationTests {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void flywayCreatesAccountsAndEnforcesPerOwnerNamesAndAmounts() {
        int firstOwner = insertUser("first@example.test");
        int secondOwner = insertUser("second@example.test");

        jdbc.update("""
                INSERT INTO conta_financeira
                    (owner_user_id, nome, tipo, saldo_inicial, data_saldo_inicial, criada_em)
                VALUES (?, ?, 'CARTEIRA_DINHEIRO', ?, ?, ?)
                """, firstOwner, "Dinheiro", new BigDecimal("120.50"),
                Date.valueOf(LocalDate.of(2026, 1, 1)), OffsetDateTime.now());

        assertThat(jdbc.queryForObject(
                "SELECT saldo_inicial FROM conta_financeira WHERE owner_user_id = ? AND nome = ?",
                BigDecimal.class, firstOwner, "Dinheiro"))
                .isEqualByComparingTo("120.50");

        jdbc.update("""
                INSERT INTO conta_financeira
                    (owner_user_id, nome, tipo, data_saldo_inicial, criada_em)
                VALUES (?, 'Dinheiro', 'CARTEIRA_DINHEIRO', ?, ?)
                """, secondOwner, Date.valueOf(LocalDate.now()), OffsetDateTime.now());

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM conta_financeira WHERE owner_user_id = ?",
                Integer.class, secondOwner)).isEqualTo(1);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO conta_financeira
                    (owner_user_id, nome, tipo, data_saldo_inicial, criada_em)
                VALUES (?, 'Dinheiro', 'CARTEIRA_DINHEIRO', ?, ?)
                """, firstOwner, Date.valueOf(LocalDate.now()), OffsetDateTime.now()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private int insertUser(String email) {
        return jdbc.queryForObject("""
                INSERT INTO usuario (nome, email, senha_hash, status, criado_em)
                VALUES ('Teste', ?, 'not-a-real-hash', 'ATIVO', ?)
                RETURNING id
                """, Integer.class, email, OffsetDateTime.now());
    }

}
