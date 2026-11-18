package project.smartfinance;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import project.smartfinance.infrastructure.persistence.generated.ContaFinanceira;
import project.smartfinance.infrastructure.persistence.generated.Usuario;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
class JpaMappingTests {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void generatedEntitiesPersistAgainstTheFlywaySchema() {
        try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
            var transaction = entityManager.getTransaction();
            transaction.begin();
            try {
                var user = new Usuario();
                user.setNome("Teste");
                user.setEmail("jpa@example.test");
                user.setSenhaHash("not-a-real-hash");
                user.setStatus("ATIVO");
                user.setCriadoEm(OffsetDateTime.now());
                entityManager.persist(user);

                var wallet = new ContaFinanceira();
                wallet.setOwnerUserId(user.getId());
                wallet.setNome("Dinheiro");
                wallet.setTipo("CARTEIRA_DINHEIRO");
                wallet.setSaldoInicial(new BigDecimal("120.50"));
                wallet.setDataSaldoInicial(LocalDate.of(2026, 1, 1));
                wallet.setAtiva(true);
                wallet.setCriadaEm(OffsetDateTime.now());
                entityManager.persist(wallet);
                entityManager.flush();
                entityManager.clear();

                var saved = entityManager.find(ContaFinanceira.class, wallet.getId());
                assertThat(saved.getOwnerUserId()).isEqualTo(user.getId());
                assertThat(saved.getSaldoInicial()).isEqualByComparingTo("120.50");
                assertThat(saved.getDataSaldoInicial()).isEqualTo(LocalDate.of(2026, 1, 1));
            } finally {
                transaction.rollback();
            }
        }
    }
}
