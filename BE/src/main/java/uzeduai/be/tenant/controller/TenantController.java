package uzeduai.be.tenant.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uzeduai.be.access.service.AccessService;
import uzeduai.be.common.response.ApiResponse;
import uzeduai.be.security.user.CustomUserDetails;
import uzeduai.be.tenant.dto.CreateTenantRequest;
import uzeduai.be.tenant.dto.TenantSummaryResponse;
import uzeduai.be.tenant.service.TenantService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tenants")
public class TenantController {

    private final TenantService tenantService;
    private final AccessService accessService;

    public TenantController(TenantService tenantService, AccessService accessService) {
        this.tenantService = tenantService;
        this.accessService = accessService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TenantSummaryResponse>>> list(
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        accessService.requirePage(principal, "TENANTS");
        return ResponseEntity.ok(ApiResponse.ok(tenantService.listForUser(principal.getId())));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TenantSummaryResponse>> create(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody CreateTenantRequest request
    ) {
        accessService.requirePage(principal, "TENANTS");
        return ResponseEntity.ok(ApiResponse.ok(tenantService.create(principal.getId(), request)));
    }
}
