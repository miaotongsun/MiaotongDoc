package com.miaotong.doc.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SigningRecordDTO {

    private Long id;
    private Long taskId;
    private Long signerUserId;
    private String signerName;
    private String signerEmployeeId;
    private Integer signOrder;
    private String status;
    private LocalDateTime confirmedAt;
    private String ipAddress;
    private String documentHash;
    private String remark;
}
