package com.miaotong.doc.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MentionDTO {

    private Long id;
    private Long commentId;
    private Long mentionedUserId;
    private String mentionedUserName;
}
