package com.miaotong.doc.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CommentDTO {

    private Long id;
    private Long documentId;
    private Long parentId;
    private Long userId;
    private String userName;
    private String employeeId;
    private String content;
    private String quoteText;
    private Integer pageNumber;
    private String position;
    private Boolean isResolved;
    private Long resolvedBy;
    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;
    private List<CommentDTO> replies;
    private List<MentionDTO> mentions;
}
