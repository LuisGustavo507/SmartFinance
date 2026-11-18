package project.smartfinance.infrastructure.web;

import java.net.URI;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Replaces the whitelabel error page with the same JSON problem contract used
 * by {@code ApiExceptionHandler}. It runs for errors raised outside the
 * controllers as well, such as security filters and static resources.
 */
@RestController
class ApiErrorController implements ErrorController {

    private static final Logger log = LoggerFactory.getLogger(ApiErrorController.class);

    @RequestMapping("${server.error.path:${error.path:/error}}")
    ResponseEntity<ProblemDetail> error(HttpServletRequest request) {
        int status = status(request);
        String path = (String) request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);

        Object exception = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        if (status >= 500) {
            log.error("Unhandled error status={} path={}", status, path,
                    exception instanceof Throwable throwable ? throwable : null);
        }

        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setTitle(title(status));
        problem.setDetail(detail(status));
        problem.setProperty("code", code(status));
        problem.setProperty("requestId", RequestIdFilter.current(request));
        if (path != null && !path.isBlank()) {
            problem.setInstance(URI.create(path));
        }
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .body(problem);
    }

    private static int status(HttpServletRequest request) {
        Object value = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        return value instanceof Integer status && status >= 400 ? status : 500;
    }

    private static String title(int status) {
        return switch (status) {
            case 400 -> "Requisição inválida";
            case 401 -> "Não autenticado";
            case 403 -> "Acesso negado";
            case 404 -> "Não encontrado";
            case 405 -> "Método não permitido";
            case 406 -> "Formato não aceito";
            case 409 -> "Conflito";
            case 413 -> "Conteúdo muito grande";
            case 415 -> "Tipo de conteúdo não suportado";
            case 422 -> "Dados inválidos";
            case 429 -> "Muitas requisições";
            default -> status >= 500 ? "Erro interno" : "Erro";
        };
    }

    private static String detail(int status) {
        return switch (status) {
            case 401 -> "Autenticação necessária para acessar este recurso.";
            case 403 -> "Você não tem permissão para acessar este recurso.";
            case 404 -> "O recurso solicitado não existe.";
            case 405 -> "O método HTTP não é permitido para este recurso.";
            case 429 -> "Aguarde antes de tentar novamente.";
            default -> status >= 500
                    ? "Não foi possível concluir a operação. Tente novamente mais tarde."
                    : "A requisição não pôde ser processada.";
        };
    }

    private static String code(int status) {
        return switch (status) {
            case 400 -> "bad_request";
            case 401 -> "unauthorized";
            case 403 -> "forbidden";
            case 404 -> "not_found";
            case 405 -> "method_not_allowed";
            case 409 -> "conflict";
            case 429 -> "too_many_requests";
            default -> status >= 500 ? "internal_error" : "http_" + status;
        };
    }
}
