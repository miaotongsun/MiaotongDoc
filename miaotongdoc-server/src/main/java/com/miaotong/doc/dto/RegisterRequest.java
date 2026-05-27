package com.miaotong.doc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "工号不能为空")
    @Pattern(regexp = "^\\d{8}$", message = "工号必须为8位数字")
    private String employeeId;

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度3-50位")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, message = "密码长度不能少于8位")
    private String password;

    @NotBlank(message = "真实姓名不能为空")
    private String realName;

    private String email;
    private String phone;
    private Long departmentId;
    private String position;
}
