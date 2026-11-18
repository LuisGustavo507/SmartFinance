package project.smartfinance.application.user;

public record UserCredentials(
        int id, String name, String email, String passwordHash, boolean active) {

    public RegisteredUser publicProfile() {
        return new RegisteredUser(id, name, email);
    }
}
