package com.miaotong.doc.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PresenceInfo {

    private Long userId;
    private String userName;
    private String avatarUrl;
    private String color;
    private String joinedAt;
}
