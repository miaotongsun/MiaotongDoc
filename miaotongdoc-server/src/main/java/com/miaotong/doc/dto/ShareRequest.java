package com.miaotong.doc.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ShareRequest {

    @NotNull(message = "文档ID不能为空")
    private Long documentId;

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    private String permission = "view";
}
