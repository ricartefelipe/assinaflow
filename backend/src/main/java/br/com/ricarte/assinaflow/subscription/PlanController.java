package br.com.ricarte.assinaflow.subscription;

import br.com.ricarte.assinaflow.subscription.dto.PlanResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/v1/plans")
@Tag(name = "Planos")
public class PlanController {

    @GetMapping
    @Operation(summary = "Lista planos disponiveis")
    @ApiResponse(responseCode = "200", description = "Catalogo de planos")
    public List<PlanResponse> list() {
        return Arrays.stream(Plan.values())
                .map(plan -> new PlanResponse(plan, plan.getPriceCents(), "BRL"))
                .toList();
    }
}
