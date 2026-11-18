package project.smartfinance.domain.counterparty;

public record Counterparty(int id, int ownerUserId, String name, boolean active) {
}
