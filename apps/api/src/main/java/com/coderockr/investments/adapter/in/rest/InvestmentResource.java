package com.coderockr.investments.adapter.in.rest;

import com.coderockr.investments.application.ConfirmWithdrawalUseCase;
import com.coderockr.investments.application.CreateInvestmentUseCase;
import com.coderockr.investments.application.GetInvestmentUseCase;
import com.coderockr.investments.application.InvalidRequest;
import com.coderockr.investments.application.InvestmentNotFound;
import com.coderockr.investments.application.ListInvestmentsUseCase;
import com.coderockr.investments.application.PreviewWithdrawalUseCase;
import com.coderockr.investments.domain.WithdrawalQuote;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;

@Path("/api/v1/investments")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class InvestmentResource {
    @Inject
    CreateInvestmentUseCase createInvestment;

    @Inject
    GetInvestmentUseCase getInvestment;

    @Inject
    ListInvestmentsUseCase listInvestments;

    @Inject
    PreviewWithdrawalUseCase previewWithdrawal;

    @Inject
    ConfirmWithdrawalUseCase confirmWithdrawal;

    @GET
    public Uni<InvestmentPageResponse> list(
            @QueryParam("ownerName") String ownerName,
            @Parameter(
                    name = "createdFrom",
                    in = ParameterIn.QUERY,
                    description = "Limite mínimo inclusivo de created_on; deve ser um CivilDate finito.",
                    schema = @Schema(
                            type = SchemaType.STRING,
                            format = CivilDateParser.OPENAPI_FORMAT,
                            minLength = 10,
                            maxLength = 14,
                            pattern = CivilDateParser.OPENAPI_PATTERN))
            @QueryParam("createdFrom") String rawCreatedFrom,
            @Parameter(
                    name = "createdTo",
                    in = ParameterIn.QUERY,
                    description = "Limite máximo inclusivo de created_on; deve ser um CivilDate finito igual ou posterior a createdFrom.",
                    schema = @Schema(
                            type = SchemaType.STRING,
                            format = CivilDateParser.OPENAPI_FORMAT,
                            minLength = 10,
                            maxLength = 14,
                            pattern = CivilDateParser.OPENAPI_PATTERN))
            @QueryParam("createdTo") String rawCreatedTo,
            @QueryParam("limit") @DefaultValue("20") String rawLimit,
            @QueryParam("cursor") String cursor) {
        LocalDate createdFrom = parseOptionalDate(rawCreatedFrom);
        LocalDate createdTo = parseOptionalDate(rawCreatedTo);
        return listInvestments.execute(ownerName, createdFrom, createdTo, parseLimit(rawLimit), cursor)
                .map(InvestmentPageResponse::from);
    }

    @POST
    public Uni<Response> create(
            @Parameter(
                    name = "Idempotency-Key",
                    in = ParameterIn.HEADER,
                    required = true,
                    description = "Chave não vazia usada para recuperar a mesma criação.",
                    schema = @Schema(
                            type = SchemaType.STRING,
                            minLength = 8,
                            maxLength = 100,
                            pattern = ApiSchemaPatterns.NON_BLANK_WITHOUT_NUL))
            @HeaderParam("Idempotency-Key") String idempotencyKey,
            @NotNull @Valid CreateInvestmentRequest request,
            @Context UriInfo uriInfo) {
        requireBody(request);
        return createInvestment.execute(
                        request.ownerName(),
                        CivilDateParser.parse(request.createdOn()),
                        request.initialAmount(),
                        idempotencyKey)
                .map(view -> {
                    URI location = uriInfo.getAbsolutePathBuilder().path(view.id().toString()).build();
                    return Response.created(location).entity(InvestmentResponse.from(view)).build();
                });
    }

    @GET
    @Path("/{investmentId}")
    public Uni<InvestmentResponse> get(@PathParam("investmentId") String rawId) {
        return getInvestment.execute(parseId(rawId)).map(InvestmentResponse::from);
    }

    @POST
    @Path("/{investmentId}/withdrawal-previews")
    public Uni<WithdrawalResultResponse> preview(
            @PathParam("investmentId") String rawId,
            @NotNull @Valid WithdrawalDateRequest request) {
        requireBody(request);
        return previewWithdrawal.execute(parseId(rawId), CivilDateParser.parse(request.withdrawnOn()))
                .map(WithdrawalResultResponse::from);
    }

    @POST
    @Path("/{investmentId}/withdrawals")
    public Uni<Response> withdraw(
            @PathParam("investmentId") String rawId,
            @Parameter(
                    name = "Idempotency-Key",
                    in = ParameterIn.HEADER,
                    required = true,
                    description = "Chave não vazia usada para recuperar o mesmo resgate.",
                    schema = @Schema(
                            type = SchemaType.STRING,
                            minLength = 8,
                            maxLength = 100,
                            pattern = ApiSchemaPatterns.NON_BLANK_WITHOUT_NUL))
            @HeaderParam("Idempotency-Key") String idempotencyKey,
            @NotNull @Valid WithdrawalDateRequest request) {
        requireBody(request);
        return confirmWithdrawal.execute(
                        parseId(rawId), CivilDateParser.parse(request.withdrawnOn()), idempotencyKey)
                .map(quote -> Response.ok(WithdrawalResultResponse.from(quote)).build());
    }

    private UUID parseId(String rawId) {
        try {
            if (rawId == null || !rawId.matches("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")) {
                throw new IllegalArgumentException("UUID malformado");
            }
            return UUID.fromString(rawId);
        } catch (IllegalArgumentException exception) {
            throw new InvestmentNotFound();
        }
    }

    private int parseLimit(String rawLimit) {
        try {
            return Integer.parseInt(rawLimit);
        } catch (NumberFormatException exception) {
            throw new InvalidRequest("O limite da página deve ser um número inteiro.");
        }
    }

    private LocalDate parseOptionalDate(String value) {
        return value == null ? null : CivilDateParser.parse(value);
    }

    private void requireBody(Object request) {
        if (request == null) {
            throw new InvalidRequest("O corpo da requisição é obrigatório.");
        }
    }
}
