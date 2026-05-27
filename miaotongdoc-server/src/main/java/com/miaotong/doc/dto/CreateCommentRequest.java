package com.miaotong.doc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
public class CreateCommentRequest {

    @NotNull(message = "文档ID不能为空")
    private Long documentId;

    @NotBlank(message = "评论内容不能为空")
    private String content;

    private Long parentId;
    private String quoteText;
    private Integer pageNumber;
    private String position;
    private List<Long> mentionUserIds;
}
