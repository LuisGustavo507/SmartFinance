package project.smartfinance.application.card;

public class CardReferenceNotFoundException extends RuntimeException {

    public CardReferenceNotFoundException() {
        super("Referência de cartão não encontrada.");
    }
}
