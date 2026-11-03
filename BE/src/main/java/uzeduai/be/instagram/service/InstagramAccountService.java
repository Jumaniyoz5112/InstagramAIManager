package uzeduai.be.instagram.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uzeduai.be.common.exception.ApiException;
import uzeduai.be.common.i18n.MessageKeys;
import uzeduai.be.instagram.domain.InstagramAccountStatus;
import uzeduai.be.instagram.dto.InstagramAccountResponse;
import uzeduai.be.instagram.entity.InstagramAccount;
import uzeduai.be.instagram.repository.InstagramAccountRepository;
import uzeduai.be.security.user.CustomUserDetails;
import uzeduai.be.tenant.context.TenantMembershipService;

import java.util.List;
import java.util.UUID;

@Service
public class InstagramAccountService {

    private final InstagramAccountRepository accountRepository;
    private final TenantMembershipService tenantMembershipService;

    public InstagramAccountService(
            InstagramAccountRepository accountRepository,
            TenantMembershipService tenantMembershipService
    ) {
        this.accountRepository = accountRepository;
        this.tenantMembershipService = tenantMembershipService;
    }

    @Transactional(readOnly = true)
    public List<InstagramAccountResponse> listForActiveWorkspace(CustomUserDetails principal) {
        requireWorkspace(principal);
        tenantMembershipService.requireMembership(principal.getTenantId(), principal.getId());
        return accountRepository
                .findByTenantIdAndWorkspaceIdOrderByCreatedAtDesc(principal.getTenantId(), principal.getWorkspaceId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public InstagramAccount requireAccountForUser(UUID accountId, CustomUserDetails principal) {
        requireWorkspace(principal);
        tenantMembershipService.requireMembership(principal.getTenantId(), principal.getId());
        InstagramAccount account = accountRepository.findByIdAndTenantId(accountId, principal.getTenantId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MessageKeys.INSTAGRAM_ACCOUNT_NOT_FOUND));
        if (!account.getWorkspaceId().equals(principal.getWorkspaceId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, MessageKeys.WORKSPACE_NOT_MEMBER);
        }
        return account;
    }

    @Transactional
    public InstagramAccountResponse disconnect(UUID accountId, CustomUserDetails principal) {
        InstagramAccount account = requireAccountForUser(accountId, principal);
        account.setStatus(InstagramAccountStatus.DISCONNECTED);
        account.setAccessToken("revoked");
        return toResponse(accountRepository.save(account));
    }

    public InstagramAccountResponse toResponse(InstagramAccount account) {
        return new InstagramAccountResponse(
                account.getId(),
                account.getTenantId(),
                account.getWorkspaceId(),
                account.getIgUserId(),
                account.getUsername(),
                account.getAccountType(),
                account.getStatus(),
                account.getTokenExpiresAt(),
                account.getLastSyncedAt(),
                account.getCreatedAt()
        );
    }

    private void requireWorkspace(CustomUserDetails principal) {
        if (principal.getTenantId() == null || principal.getWorkspaceId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, MessageKeys.INSTAGRAM_WORKSPACE_REQUIRED);
        }
    }
}
