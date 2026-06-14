package com.miaotong.doc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CreateDocumentRequest {

    @NotBlank(message = "文档类型不能为空")
    @Pattern(regexp = "^(word|cell|slide)$", message = "文档类型必须为word/cell/slide")
    private String docType;

    private String title;

    private Long templateId;
}
