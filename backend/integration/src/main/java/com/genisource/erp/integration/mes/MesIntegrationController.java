package com.genisource.erp.integration.mes;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The door MES knocks on.
 *
 * <p>These endpoints are for a machine, not for a person: MES authenticates with a
 * service token and posts what it knows. They are guarded by their own permissions so
 * an MES service account can be granted exactly these two and nothing a human would
 * use.
 */
@RestController
@RequestMapping("/api/integration/mes")
@Tag(name = "Integration :: MES", description = "What MES reports back to the ERP")
public class MesIntegrationController {

    private final MesInboundService inbound;
    private final MesOutboxService outbox;

    public MesIntegrationController(MesInboundService inbound, MesOutboxService outbox) {
        this.inbound = inbound;
        this.outbox = outbox;
    }

    @PostMapping("/completions")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Goods finished on the shop floor; books them into stock")
    @PreAuthorize("hasAuthority('integration:mes:write')")
    public MesDtos.CompletionResponse complete(
            @Valid @RequestBody MesDtos.CompletionRequest request) {
        return inbound.complete(request);
    }

    @PostMapping("/progress")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "How far the order has got; books nothing")
    @PreAuthorize("hasAuthority('integration:mes:write')")
    public MesDtos.ProgressResponse progress(@Valid @RequestBody MesDtos.ProgressRequest request) {
        return inbound.progress(request);
    }

    @GetMapping("/outbox")
    @Operation(summary = "Messages waiting to be delivered to MES")
    @PreAuthorize("hasAuthority('integration:mes:read')")
    public List<MesDtos.OutboxView> outbox() {
        return inbound.outbox();
    }

    @PostMapping("/outbox/{id}/retry")
    @Operation(summary = "Try one message again now")
    @PreAuthorize("hasAuthority('integration:mes:write')")
    public void retry(@org.springframework.web.bind.annotation.PathVariable Long id) {
        outbox.deliver(id);
    }

    /**
     * A completion the ERP will not accept is a conflict between two systems, not a
     * broken message. It answers 422 with the reason, so MES can put the work order
     * aside for a person instead of retrying it forever.
     */
    @ExceptionHandler(MesUnprocessableException.class)
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public org.springframework.http.ProblemDetail unprocessable(MesUnprocessableException ex) {
        org.springframework.http.ProblemDetail problem =
                org.springframework.http.ProblemDetail.forStatusAndDetail(
                        HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setTitle("Unprocessable Entity");
        problem.setProperty("code", "mes_unprocessable");
        return problem;
    }
}
