package project.smartfinance.application.card;

public class CardInactiveException extends RuntimeException {

    public CardInactiveException() {
        super("O cartão está inativo.");
    }
}
