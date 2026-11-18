package project.smartfinance.application.card;

public class CardPaymentNotAllowedException extends RuntimeException {

    public CardPaymentNotAllowedException() {
        super("A fatura não pode ser paga neste estado.");
    }
}
