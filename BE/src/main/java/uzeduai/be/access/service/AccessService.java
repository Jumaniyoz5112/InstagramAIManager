package uzeduai.be.access.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uzeduai.be.access.dto.PageAccessResponse;
import uzeduai.be.access.dto.UpdateUserPagesRequest;
import uzeduai.be.access.dto.UserAccessResponse;
import uzeduai.be.access.entity.AppPage;
import uzeduai.be.access.entity.UserPageAccess;
import uzeduai.be.access.repository.AppPageRepository;
import uzeduai.be.access.repository.UserPageAccessRepository;
import uzeduai.be.common.enums.PlatformRoleCode;
import uzeduai.be.common.exception.ApiException;
import uzeduai.be.common.i18n.MessageKeys;
import uzeduai.be.security.user.CustomUserDetails;
import uzeduai.be.user.entity.User;
import uzeduai.be.user.repository.UserRepository;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AccessService {

    private final AppPageRepository appPageRepository;
    private final UserPageAccessRepository userPageAccessRepository;
    private final UserRepository userRepository;

    public AccessService(
            AppPageRepository appPageRepository,
            UserPageAccessRepository userPageAccessRepository,
            UserRepository userRepository
    ) {
        this.appPageRepository = appPageRepository;
        this.userPageAccessRepository = userPageAccessRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<PageAccessResponse> listCatalog() {
        return appPageRepository.findByEnabledTrueOrderBySortOrderAsc().stream()
                .map(this::toPageResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PageAccessResponse> resolvePages(User user) {
        if (isPlatformAdmin(user)) {
            return appPageRepository.findByEnabledTrueOrderBySortOrderAsc().stream()
                    .map(this::toPageResponse)
                    .toList();
        }

        return userPageAccessRepository.findByUserId(user.getId()).stream()
                .map(UserPageAccess::getPage)
                .filter(AppPage::isEnabled)
                .filter(page -> !page.isAdminOnly())
                .sorted(Comparator.comparingInt(AppPage::getSortOrder))
                .map(this::toPageResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public void requirePage(CustomUserDetails principal, String pageCode) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, MessageKeys.AUTH_USER_NOT_FOUND));
        boolean allowed = resolvePages(user).stream().anyMatch(page -> page.code().equals(pageCode));
        if (!allowed) {
            throw new ApiException(HttpStatus.FORBIDDEN, MessageKeys.ACCESS_PAGE_FORBIDDEN);
        }
    }

    @Transactional
    public void grantDefaultPages(User user) {
        if (isPlatformAdmin(user)) {
            return;
        }
        List<AppPage> defaults = appPageRepository.findByEnabledTrueAndDefaultForNewUsersTrueOrderBySortOrderAsc();
        for (AppPage page : defaults) {
            if (page.isAdminOnly()) {
                continue;
            }
            UserPageAccess access = new UserPageAccess();
            access.setUser(user);
            access.setPage(page);
            userPageAccessRepository.save(access);
        }
    }

    @Transactional(readOnly = true)
    public List<UserAccessResponse> listUsers() {
        return userRepository.findAll().stream()
                .sorted(Comparator.comparing(User::getEmail, String.CASE_INSENSITIVE_ORDER))
                .map(this::toUserAccess)
                .toList();
    }

    @Transactional
    public UserAccessResponse updateUserPages(UUID targetUserId, UpdateUserPagesRequest request, CustomUserDetails admin) {
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MessageKeys.ACCESS_USER_NOT_FOUND));

        if (isPlatformAdmin(target)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, MessageKeys.ACCESS_ADMIN_IMMUTABLE);
        }

        User granter = userRepository.findById(admin.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, MessageKeys.AUTH_USER_NOT_FOUND));

        Set<String> requested = request.pageCodes() == null
                ? Set.of()
                : request.pageCodes().stream()
                .map(code -> code.trim().toUpperCase())
                .filter(code -> !code.isBlank())
                .collect(Collectors.toCollection(HashSet::new));

        List<AppPage> grantable = appPageRepository.findByEnabledTrueOrderBySortOrderAsc().stream()
                .filter(page -> !page.isAdminOnly())
                .toList();

        Set<String> grantableCodes = grantable.stream().map(AppPage::getCode).collect(Collectors.toSet());
        for (String code : requested) {
            if (!grantableCodes.contains(code)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, MessageKeys.ACCESS_INVALID_PAGE, code);
            }
        }

        userPageAccessRepository.deleteByUserId(target.getId());
        userPageAccessRepository.flush();

        for (AppPage page : grantable) {
            if (requested.contains(page.getCode())) {
                UserPageAccess access = new UserPageAccess();
                access.setUser(target);
                access.setPage(page);
                access.setGrantedBy(granter);
                userPageAccessRepository.save(access);
            }
        }

        return toUserAccess(target);
    }

    private UserAccessResponse toUserAccess(User user) {
        boolean platformAdmin = isPlatformAdmin(user);
        List<String> pageCodes = platformAdmin
                ? appPageRepository.findByEnabledTrueOrderBySortOrderAsc().stream().map(AppPage::getCode).toList()
                : userPageAccessRepository.findByUserId(user.getId()).stream()
                .map(access -> access.getPage().getCode())
                .sorted()
                .toList();

        return new UserAccessResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                platformAdmin,
                user.isEnabled(),
                pageCodes
        );
    }

    private PageAccessResponse toPageResponse(AppPage page) {
        return new PageAccessResponse(
                page.getCode(),
                page.getName(),
                page.getDescription(),
                page.getPath(),
                page.getSortOrder(),
                page.isAdminOnly()
        );
    }

    private boolean isPlatformAdmin(User user) {
        return user.getRoles() != null && user.getRoles().stream()
                .anyMatch(role -> PlatformRoleCode.PLATFORM_ADMIN.name().equals(role.getCode()));
    }
}
