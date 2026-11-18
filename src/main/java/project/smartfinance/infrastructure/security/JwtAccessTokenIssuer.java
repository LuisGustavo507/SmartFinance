package project.smartfinance.infrastructure.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Component;
import project.smartfinance.application.auth.AccessTokenIssuer;
import project.smartfinance.application.user.UserCredentials;

@Component
class JwtAccessTokenIssuer implements AccessTokenIssuer {

    private final JwtEncoder encoder;
    private final Clock clock;
    private final Duration accessTtl;

    JwtAccessTokenIssuer(JwtEncoder encoder, Clock clock,
            @Value("${smartfinance.security.access-ttl:PT15M}") Duration accessTtl) {
        this.encoder = encoder;
        this.clock = clock;
        this.accessTtl = accessTtl;
    }

    @Override
    public String issue(UserCredentials user) {
        Instant now = clock.instant();
        var claims = JwtClaimsSet.builder()
                .issuer("smartfinance")
                .subject(Integer.toString(user.id()))
                .issuedAt(now)
                .expiresAt(now.plus(accessTtl))
                .build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
