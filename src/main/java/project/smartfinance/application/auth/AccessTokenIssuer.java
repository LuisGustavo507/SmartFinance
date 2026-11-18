package project.smartfinance.application.auth;

import project.smartfinance.application.user.UserCredentials;

public interface AccessTokenIssuer {

    String issue(UserCredentials user);
}
