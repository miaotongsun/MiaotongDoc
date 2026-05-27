package com.miaotong.doc.dto;

import com.miaotong.doc.entity.User;
import lombok.Data;
import lombok.AllArgsConstructor;

@Data
@AllArgsConstructor
public class SsoLoginResult {

    private User user;
    private String providerId;
    private boolean isNewUser;
}
