package project.smartfinance.api.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import project.smartfinance.application.user.DuplicateEmailException;
import project.smartfinance.application.user.InviteRequiredException;
import project.smartfinance.application.user.RegisterUser;
import project.smartfinance.application.user.RegisteredUser;
import project.smartfinance.infrastructure.web.RequestIdFilter;

@RestController
@RequestMapping("/api/auth")
class RegistrationController {

    private final RegisterUser registerUser;

    RegistrationController(RegisterUser registerUser) {
        this.registerUser = registerUser;
    }

    @GetMapping("/csrf")
    ResponseEntity<Void> csrf(CsrfToken csrfToken) {
        csrfToken.getToken();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/register")
    ResponseEntity<RegisteredUser> register(@Valid @RequestBody RegistrationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(registerUser.register(request.name(), request.email(), request.password()));
    }

    @ExceptionHandler({DuplicateEmailException.class, DataIntegrityViolationException.class})
    ResponseEntity<Void> duplicateEmail() {
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    @ExceptionHandler(InviteRequiredException.class)
    ResponseEntity<ProblemDetail> inviteRequired() {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        problem.setTitle("Convite necessário");
        problem.setDetail("Este email não está na lista de convidados do beta.");
        problem.setProperty("code", "invite_required");
        problem.setProperty("requestId", RequestIdFilter.current());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Void> invalidPassword() {
        return ResponseEntity.badRequest().build();
    }

    record RegistrationRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 12) String password) {
    }
}
