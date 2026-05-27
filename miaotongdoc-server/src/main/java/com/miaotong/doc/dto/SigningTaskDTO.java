package com.miaotong.doc.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SigningTaskDTO {

    private Long id;
    private Long documentId;
    private String documentTitle;
    private String title;
    private String description;
    private Long createdBy;
    private String creatorName;
    private String status;
    private Integer requiredCount;
    private Integer completedCount;
    private LocalDateTime deadline;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private List<SigningRecordDTO> records;
}
