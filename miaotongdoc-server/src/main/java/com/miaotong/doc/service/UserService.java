package com.miaotong.doc.service;

import com.miaotong.doc.dto.LoginRequest;
import com.miaotong.doc.dto.LoginResponse;
import com.miaotong.doc.dto.RegisterRequest;
import com.miaotong.doc.dto.ChangePasswordRequest;
import com.miaotong.doc.entity.User;
import com.miaotong.doc.exception.BusinessException;
import com.miaotong.doc.exception.NotFoundException;
import com.miaotong.doc.repository.UserRepository;
import com.miaotong.doc.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public LoginResponse login(LoginRequest request, String ip) {
        User user = userRepository.findByUsername(request.getUsername())
                .or(() -> userRepository.findByEmployeeId(request.getUsername()))
                .orElseThrow(() -> new NotFoundException("用户不存在"));

        if (!user.getIsActive()) {
            throw new BusinessException("账号已被禁用");
        }

        if (user.getSsoOnly()) {
            throw new BusinessException("SSO用户请使用企业账号登录");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException("密码错误");
        }

        user.setLastLoginAt(LocalDateTime.now());
        user.setLastLoginIp(ip);
        userRepository.save(user);

        String token = jwtUtil.generateToken(user.getId(), user.getEmployeeId(), user.getUsername(), user.getRole());

        return new LoginResponse(
                token,
                user.getId(),
                user.getEmployeeId(),
                user.getUsername(),
                user.getRealName(),
                user.getRole()
        );
    }

    @Transactional
    public User register(RegisterRequest request) {
        if (userRepository.existsByEmployeeId(request.getEmployeeId())) {
            throw new BusinessException("工号已存在");
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException("用户名已存在");
        }
        if (request.getEmail() != null && userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("邮箱已存在");
        }

        User user = new User();
        user.setEmployeeId(request.getEmployeeId());
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRealName(request.getRealName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setDepartmentId(request.getDepartmentId());
        user.setPosition(request.getPosition());
        user.setRole("user");
        user.setIsActive(true);
        user.setPasswordChangedAt(LocalDateTime.now());

        return userRepository.save(user);
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("用户不存在"));

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException("旧密码错误");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordChangedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("用户不存在"));
    }

    public User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("用户不存在"));
    }

    public java.util.List<User> searchUsers(String keyword) {
        return userRepository.searchByKeyword(keyword);
    }

    public java.util.List<User> getAllActiveUsers() {
        return userRepository.findAllActive();
    }

    @Transactional
    public User adminCreateUser(String employeeId, String username, String password,
                                String realName, String email, String phone,
                                Long departmentId, String position, String role) {
        if (userRepository.existsByEmployeeId(employeeId)) {
            throw new BusinessException("工号已存在");
        }
        if (userRepository.existsByUsername(username)) {
            throw new BusinessException("用户名已存在");
        }
        if (email != null && !email.isEmpty() && userRepository.existsByEmail(email)) {
            throw new BusinessException("邮箱已存在");
        }

        User user = new User();
        user.setEmployeeId(employeeId);
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setRealName(realName);
        user.setEmail(email);
        user.setPhone(phone);
        user.setDepartmentId(departmentId);
        user.setPosition(position);
        user.setRole(role != null ? role : "user");
        user.setIsActive(true);
        user.setPasswordChangedAt(LocalDateTime.now());

        return userRepository.save(user);
    }

    @Transactional
    public User adminUpdateUser(Long id, String realName, String email, String phone,
                                Long departmentId, String position) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("用户不存在"));
        if (realName != null) user.setRealName(realName);
        if (email != null) user.setEmail(email);
        if (phone != null) user.setPhone(phone);
        if (departmentId != null) user.setDepartmentId(departmentId);
        if (position != null) user.setPosition(position);
        return userRepository.save(user);
    }

    @Transactional
    public void adminResetPassword(Long id, String newPassword) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("用户不存在"));
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(LocalDateTime.now());
        userRepository.save(user);
    }
}
