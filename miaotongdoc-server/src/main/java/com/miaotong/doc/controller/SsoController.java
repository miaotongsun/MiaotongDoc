package com.miaotong.doc.controller;

import com.miaotong.doc.dto.SsoProviderDTO;
import com.miaotong.doc.dto.SsoLoginResult;
import com.miaotong.doc.entity.User;
import com.miaotong.doc.service.SsoService;
import com.miaotong.doc.service.AuditService;
import com.miaotong.doc.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sso")
@RequiredArgsConstructor
public class SsoController {

    private final SsoService ssoService;
    private final JwtUtil jwtUtil;
    private final AuditService auditService;

    @Value("${sso.success-redirect:/home.html}")
    private String successRedirect;

    @GetMapping("/providers")
    public ResponseEntity<List<SsoProviderDTO>> getProviders() {
        return ResponseEntity.ok(ssoService.getProviders());
    }

    @GetMapping("/callback")
    public void ssoCallback(OAuth2AuthenticationToken authentication,
                            HttpServletResponse response) throws IOException {
        OAuth2User oauth2User = authentication.getPrincipal();
        Map<String, Object> claims = oauth2User.getAttributes();

        SsoLoginResult result = ssoService.findOrCreateUser(claims);

        String token = jwtUtil.generateToken(
                result.getUser().getId(),
                result.getUser().getEmployeeId(),
                result.getUser().getUsername(),
                result.getUser().getRole()
        );

        String redirectUrl = successRedirect
                + "#token=" + token
                + "&userId=" + result.getUser().getId()
                + "&name=" + URLEncoder.encode(result.getUser().getRealName(), StandardCharsets.UTF_8)
                + "&employeeId=" + result.getUser().getEmployeeId();

        response.sendRedirect(redirectUrl);

        auditService.log(result.getUser().getId(), "SSO_LOGIN", "USER",
                result.getUser().getId(), Map.of("provider", result.getProviderId(), "isNew", result.isNewUser()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> ssoLogout(HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        String authHeader = httpRequest.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.badRequest().body(Map.of("error", "缺少认证信息"));
        }
        String token = authHeader.substring(7);
        String jti = jwtUtil.getJti(token);
        Instant expiresAt = jwtUtil.getExpiration(token).toInstant();

        String logoutUrl = ssoService.logout(userId, jti, expiresAt);
        return ResponseEntity.ok(Map.of("logoutUrl", logoutUrl));
    }
}
