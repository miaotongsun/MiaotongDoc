package com.miaotong.doc.service;

import com.miaotong.doc.entity.*;
import com.miaotong.doc.exception.BusinessException;
import com.miaotong.doc.exception.NotFoundException;
import com.miaotong.doc.repository.*;
import com.miaotong.doc.dto.SsoLoginResult;
import com.miaotong.doc.dto.SsoProviderDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SsoService {

    private final UserRepository userRepository;
    private final SsoIdentityRepository ssoIdentityRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenBlacklistService tokenBlacklistService;

    @Value("${sso.enabled:false}")
    private boolean ssoEnabled;

    @Value("${sso.provider-name:银行统一认证}")
    private String providerName;

    @Value("${sso.auto-provision.enabled:true}")
    private boolean autoProvisionEnabled;

    @Transactional
    public SsoLoginResult findOrCreateUser(Map<String, Object> claims) {
        String externalId = (String) claims.get("sub");

        Optional<SsoIdentity> existing = ssoIdentityRepository
                .findByProviderIdAndExternalId(providerName, externalId);

        if (existing.isPresent()) {
            SsoIdentity identity = existing.get();
            identity.setLastLoginAt(LocalDateTime.now());
            ssoIdentityRepository.save(identity);
            User user = userRepository.findById(identity.getUserId())
                    .orElseThrow(() -> new NotFoundException("用户不存在"));
            return new SsoLoginResult(user, providerName, false);
        }

        String email = (String) claims.get("email");
        if (email != null) {
            Optional<User> emailUser = userRepository.findByEmail(email);
            if (emailUser.isPresent()) {
                return linkIdentity(emailUser.get(), claims, providerName, externalId);
            }
        }

        if (!autoProvisionEnabled) {
            throw new BusinessException("SSO自动创建用户已关闭，请联系管理员");
        }

        return autoProvisionUser(claims, providerName, externalId);
    }

    private SsoLoginResult linkIdentity(User user, Map<String, Object> claims,
                                         String providerId, String externalId) {
        SsoIdentity identity = new SsoIdentity();
        identity.setUserId(user.getId());
        identity.setProviderId(providerId);
        identity.setExternalId(externalId);
        identity.setExternalEmail((String) claims.get("email"));
        identity.setExternalName((String) claims.get("name"));
        identity.setRawClaims(claims.toString());
        identity.setLastLoginAt(LocalDateTime.now());
        ssoIdentityRepository.save(identity);

        return new SsoLoginResult(user, providerId, false);
    }

    private SsoLoginResult autoProvisionUser(Map<String, Object> claims,
                                              String providerId, String externalId) {
        User user = new User();
        user.setEmployeeId(generateSsoEmployeeId());
        user.setUsername("sso_" + externalId.substring(0, Math.min(8, externalId.length())));
        user.setRealName((String) claims.getOrDefault("name", "SSO用户"));
        user.setEmail((String) claims.get("email"));
        user.setPassword("{SSO}" + passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setSsoOnly(true);
        user.setRole("user");
        user.setIsActive(true);

        String deptClaim = (String) claims.get("department_id");
        if (deptClaim != null) {
            try {
                Long deptId = Long.parseLong(deptClaim);
                var dept = departmentRepository.findById(deptId);
                if (dept.isPresent()) {
                    user.setDepartmentId(dept.get().getId());
                }
            } catch (NumberFormatException ignored) {
            }
        }

        user = userRepository.save(user);

        SsoIdentity identity = new SsoIdentity();
        identity.setUserId(user.getId());
        identity.setProviderId(providerId);
        identity.setExternalId(externalId);
        identity.setExternalEmail((String) claims.get("email"));
        identity.setExternalName((String) claims.get("name"));
        identity.setRawClaims(claims.toString());
        identity.setLastLoginAt(LocalDateTime.now());
        ssoIdentityRepository.save(identity);

        return new SsoLoginResult(user, providerId, true);
    }

    private String generateSsoEmployeeId() {
        Random random = new SecureRandom();
        String employeeId;
        do {
            employeeId = String.format("%08d", 90000001 + random.nextInt(9999999));
        } while (userRepository.existsByEmployeeId(employeeId));
        return employeeId;
    }

    public List<SsoProviderDTO> getProviders() {
        if (!ssoEnabled) return Collections.emptyList();
        SsoProviderDTO provider = new SsoProviderDTO();
        provider.setId(providerName.toLowerCase().replace(" ", "-"));
        provider.setName(providerName);
        provider.setLoginUrl("/api/sso/login");
        return List.of(provider);
    }

    public String logout(Long userId, String currentTokenJti, Instant tokenExpiresAt) {
        tokenBlacklistService.blacklist(currentTokenJti, userId, tokenExpiresAt, "SSO_LOGOUT");
        return "/login.html";
    }
}
