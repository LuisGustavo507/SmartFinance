package project.smartfinance.application.card;

public class CardStatementClosedException extends RuntimeException {

    public CardStatementClosedException() {
        super("O ciclo da fatura já foi encerrado.");
    }
}
