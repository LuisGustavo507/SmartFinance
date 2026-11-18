package project.smartfinance.api;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.converter.HttpMessageNotReadableException;

import project.smartfinance.infrastructure.web.RequestIdFilter;

/**
 * Translates framework-level failures into the same JSON problem contract
 * handled by {@code ApiErrorController}. Domain exceptions keep their
 * controller-local handlers, which take precedence over this advice.
 */
@RestControllerAdvice
class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> invalidBody(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(),
                    error.getDefaultMessage() == null ? "Valor inválido." : error.getDefaultMessage());
        }
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "validation_failed",
                "Confira os dados informados.");
        problem.setProperty("errors", errors);
        return respond(problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> unreadableBody() {
        return respond(problem(HttpStatus.BAD_REQUEST, "malformed_body",
                "O corpo da requisição não pôde ser lido."));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ProblemDetail> missingParameter(MissingServletRequestParameterException exception) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "missing_parameter",
                "Parâmetro obrigatório ausente.");
        problem.setProperty("parameter", exception.getParameterName());
        return respond(problem);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProblemDetail> invalidParameter(MethodArgumentTypeMismatchException exception) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "invalid_parameter",
                "Parâmetro com valor inválido.");
        problem.setProperty("parameter", exception.getName());
        return respond(problem);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ProblemDetail> invalidMethodArgument() {
        return respond(problem(HttpStatus.BAD_REQUEST, "validation_failed",
                "Confira os dados informados."));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> constraintViolation() {
        return respond(problem(HttpStatus.BAD_REQUEST, "validation_failed",
                "Confira os dados informados."));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ProblemDetail> notFound() {
        return respond(problem(HttpStatus.NOT_FOUND, "not_found",
                "O recurso solicitado não existe."));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ProblemDetail> methodNotAllowed() {
        return respond(problem(HttpStatus.METHOD_NOT_ALLOWED, "method_not_allowed",
                "O método HTTP não é permitido para este recurso."));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ProblemDetail> unsupportedMediaType() {
        return respond(problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "unsupported_media_type", "Envie o corpo como application/json."));
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> unauthenticated() {
        return respond(problem(HttpStatus.UNAUTHORIZED, "unauthorized",
                "Autenticação necessária para acessar este recurso."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> forbidden() {
        return respond(problem(HttpStatus.FORBIDDEN, "forbidden",
                "Você não tem permissão para acessar este recurso."));
    }

    @ExceptionHandler(ErrorResponseException.class)
    ResponseEntity<ProblemDetail> errorResponse(ErrorResponseException exception) {
        ProblemDetail problem = exception.getBody();
        problem.setProperty("requestId", RequestIdFilter.current());
        return ResponseEntity.status(exception.getStatusCode())
                .body(problem);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception exception) {
        log.error("Unhandled exception", exception);
        return respond(problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error",
                "Não foi possível concluir a operação. Tente novamente mais tarde."));
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setTitle(title(status));
        problem.setDetail(detail);
        problem.setProperty("code", code);
        problem.setProperty("requestId", RequestIdFilter.current());
        if (RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes attributes) {
            problem.setInstance(URI.create(attributes.getRequest().getRequestURI()));
        }
        return problem;
    }

    private static ResponseEntity<ProblemDetail> respond(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus()).body(problem);
    }

    private static String title(HttpStatus status) {
        return switch (status.value()) {
            case 400 -> "Requisição inválida";
            case 401 -> "Não autenticado";
            case 403 -> "Acesso negado";
            case 404 -> "Não encontrado";
            case 405 -> "Método não permitido";
            case 415 -> "Tipo de conteúdo não suportado";
            default -> status.is5xxServerError() ? "Erro interno" : "Erro";
        };
    }
}
