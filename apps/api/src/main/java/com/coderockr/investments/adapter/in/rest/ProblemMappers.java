package com.coderockr.investments.adapter.in.rest;

import com.coderockr.investments.application.InvalidRequest;
import com.coderockr.investments.application.InvestmentNotFound;
import com.coderockr.investments.domain.InvestmentConflict;
import com.coderockr.investments.domain.InvestmentRuleViolation;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.Comparator;
import java.util.List;
import org.jboss.logging.Logger;

public final class ProblemMappers {
    private ProblemMappers() {
    }

    private static Response response(UriInfo uriInfo, int status, String title, String detail, List<Problem.FieldError> errors) {
        Problem problem = new Problem("about:blank", title, status, detail, uriInfo.getRequestUri().getPath(), errors);
        return Response.status(status).type("application/problem+json").entity(problem).build();
    }

    @Provider
    public static class InvalidRequestMapper implements ExceptionMapper<InvalidRequest> {
        @Context
        UriInfo uriInfo;

        @Override
        public Response toResponse(InvalidRequest exception) {
            return response(uriInfo, 400, "Requisição inválida", exception.getMessage(), List.of());
        }
    }

    @Provider
    public static class ValidationMapper implements ExceptionMapper<ConstraintViolationException> {
        @Context
        UriInfo uriInfo;

        @Override
        public Response toResponse(ConstraintViolationException exception) {
            List<Problem.FieldError> errors = exception.getConstraintViolations().stream()
                    .sorted(Comparator.comparing(violation -> violation.getPropertyPath().toString()))
                    .map(ProblemMappers::fieldError)
                    .toList();
            return response(uriInfo, 400, "Validação inválida", "Um ou mais campos precisam de correção.", errors);
        }
    }

    @Provider
    public static class BadRequestMapper implements ExceptionMapper<BadRequestException> {
        @Context
        UriInfo uriInfo;

        @Override
        public Response toResponse(BadRequestException exception) {
            return response(uriInfo, 400, "Requisição inválida", "O corpo ou os parâmetros da requisição são inválidos.", List.of());
        }
    }

    @Provider
    public static class NotFoundMapper implements ExceptionMapper<InvestmentNotFound> {
        @Context
        UriInfo uriInfo;

        @Override
        public Response toResponse(InvestmentNotFound exception) {
            return response(uriInfo, 404, "Investimento não encontrado", exception.getMessage(), List.of());
        }
    }

    @Provider
    public static class JaxRsNotFoundMapper implements ExceptionMapper<NotFoundException> {
        @Context
        UriInfo uriInfo;

        @Override
        public Response toResponse(NotFoundException exception) {
            return response(uriInfo, 404, "Recurso não encontrado", "A rota solicitada não existe.", List.of());
        }
    }

    @Provider
    public static class WebApplicationMapper implements ExceptionMapper<WebApplicationException> {
        @Context
        UriInfo uriInfo;

        @Override
        public Response toResponse(WebApplicationException exception) {
            int status = exception.getResponse().getStatus();
            if (status < 400 || status > 599) {
                status = 500;
            }
            return response(uriInfo, status, "Erro HTTP", "A requisição não pôde ser processada.", List.of());
        }
    }

    @Provider
    public static class ConflictMapper implements ExceptionMapper<InvestmentConflict> {
        @Context
        UriInfo uriInfo;

        @Override
        public Response toResponse(InvestmentConflict exception) {
            return response(uriInfo, 409, "Conflito de investimento", exception.getMessage(), List.of());
        }
    }

    @Provider
    public static class RuleViolationMapper implements ExceptionMapper<InvestmentRuleViolation> {
        @Context
        UriInfo uriInfo;

        @Override
        public Response toResponse(InvestmentRuleViolation exception) {
            return response(uriInfo, 422, "Regra de negócio violada", exception.getMessage(), List.of());
        }
    }

    @Provider
    public static class UnhandledMapper implements ExceptionMapper<Exception> {
        private static final Logger LOG = Logger.getLogger(UnhandledMapper.class);

        @Context
        UriInfo uriInfo;

        @Override
        public Response toResponse(Exception exception) {
            LOG.error("Falha inesperada ao processar uma requisição", exception);
            return response(uriInfo, 500, "Erro interno", "Não foi possível processar a requisição.", List.of());
        }
    }

    private static Problem.FieldError fieldError(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        int dot = path.lastIndexOf('.');
        String field = dot >= 0 ? path.substring(dot + 1) : path;
        return new Problem.FieldError(field, violation.getMessage());
    }
}
