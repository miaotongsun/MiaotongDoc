package com.miaotong.doc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CreateSigningTaskRequest {

    @NotNull(message = "文档ID不能为空")
    private Long documentId;

    @NotBlank(message = "签署标题不能为空")
    private String title;

    private String description;

    @NotNull(message = "签署人列表不能为空")
    private List<Long> signerUserIds;

    private LocalDateTime deadline;
}
