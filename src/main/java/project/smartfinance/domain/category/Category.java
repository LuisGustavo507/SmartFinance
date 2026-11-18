package project.smartfinance.domain.category;

public record Category(int id, int ownerUserId, String name, Kind kind, boolean active) {

    public enum Kind {
        RECEITA, DESPESA
    }
}
