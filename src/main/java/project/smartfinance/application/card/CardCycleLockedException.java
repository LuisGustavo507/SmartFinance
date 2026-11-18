package project.smartfinance.application.card;

public class CardCycleLockedException extends RuntimeException {

    public CardCycleLockedException() {
        super("Os dias do cartão não podem mudar depois da primeira compra ou fatura.");
    }
}
