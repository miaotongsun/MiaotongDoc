package com.miaotong.doc.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserDTO {

    private Long id;
    private String employeeId;
    private String username;
    private String realName;
    private String email;
    private String phone;
    private String avatarUrl;
    private Long departmentId;
    private String departmentName;
    private String position;
    private String role;
    private Boolean isActive;
    private Boolean ssoOnly;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
}
