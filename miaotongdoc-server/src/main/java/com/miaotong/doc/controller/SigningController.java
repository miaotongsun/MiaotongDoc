package com.miaotong.doc.controller;

import com.miaotong.doc.dto.CreateSigningTaskRequest;
import com.miaotong.doc.dto.SigningTaskDTO;
import com.miaotong.doc.dto.SigningRecordDTO;
import com.miaotong.doc.entity.SigningTask;
import com.miaotong.doc.entity.SigningRecord;
import com.miaotong.doc.entity.User;
import com.miaotong.doc.entity.Document;
import com.miaotong.doc.service.SigningService;
import com.miaotong.doc.repository.UserRepository;
import com.miaotong.doc.repository.DocumentRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/signing")
@RequiredArgsConstructor
public class SigningController {

    private final SigningService signingService;
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;

    @PostMapping("/create")
    public ResponseEntity<SigningTaskDTO> createTask(
            @Valid @RequestBody CreateSigningTaskRequest request,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        SigningTask task = signingService.createTask(request, userId);
        return ResponseEntity.ok(toTaskDTO(task));
    }

    @GetMapping("/tasks")
    public ResponseEntity<Page<SigningTaskDTO>> getTasks(
            @RequestParam(defaultValue = "initiated") String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        Page<SigningTask> tasks;
        if ("todo".equals(type)) {
            tasks = signingService.getTodoTasks(userId, PageRequest.of(page, size));
        } else {
            tasks = signingService.getInitiatedTasks(userId, PageRequest.of(page, size));
        }
        return ResponseEntity.ok(tasks.map(this::toTaskDTO));
    }

    @GetMapping("/tasks/by-document/{docId}")
    public ResponseEntity<SigningTaskDTO> getTaskByDocument(@PathVariable Long docId) {
        List<SigningTask> tasks = signingService.getActiveTasksByDocumentId(docId);
        if (tasks.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        SigningTask task = tasks.get(0);
        SigningTaskDTO dto = toTaskDTO(task);
        List<SigningRecord> records = signingService.getTaskRecords(task.getId());
        dto.setRecords(records.stream().map(this::toRecordDTO).collect(Collectors.toList()));
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/tasks/{id}")
    public ResponseEntity<SigningTaskDTO> getTask(@PathVariable Long id) {
        SigningTask task = signingService.getTask(id);
        SigningTaskDTO dto = toTaskDTO(task);
        List<SigningRecord> records = signingService.getTaskRecords(id);
        dto.setRecords(records.stream().map(this::toRecordDTO).collect(Collectors.toList()));
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/confirm")
    public ResponseEntity<Map<String, String>> confirmSign(
            @RequestBody Map<String, Long> request,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        signingService.confirmSign(request.get("taskId"), userId, httpRequest);
        return ResponseEntity.ok(Map.of("message", "签署成功"));
    }

    @PostMapping("/reject")
    public ResponseEntity<Map<String, String>> rejectSign(
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        Long taskId = Long.parseLong(request.get("taskId").toString());
        String remark = (String) request.get("remark");
        signingService.rejectSign(taskId, userId, remark);
        return ResponseEntity.ok(Map.of("message", "已拒绝签署"));
    }

    @PutMapping("/tasks/{id}/cancel")
    public ResponseEntity<Map<String, String>> cancelTask(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        signingService.cancelTask(id, userId);
        return ResponseEntity.ok(Map.of("message", "签署已取消"));
    }

    private SigningTaskDTO toTaskDTO(SigningTask task) {
        SigningTaskDTO dto = new SigningTaskDTO();
        dto.setId(task.getId());
        dto.setDocumentId(task.getDocumentId());
        dto.setTitle(task.getTitle());
        dto.setDescription(task.getDescription());
        dto.setCreatedBy(task.getCreatedBy());
        dto.setStatus(task.getStatus());
        dto.setRequiredCount(task.getRequiredCount());
        dto.setCompletedCount(task.getCompletedCount());
        dto.setDeadline(task.getDeadline());
        dto.setCompletedAt(task.getCompletedAt());
        dto.setCreatedAt(task.getCreatedAt());

        if (task.getDocumentId() != null) {
            documentRepository.findById(task.getDocumentId()).ifPresent(doc ->
                dto.setDocumentTitle(doc.getTitle())
            );
        }
        if (task.getCreatedBy() != null) {
            userRepository.findById(task.getCreatedBy()).ifPresent(user ->
                dto.setCreatorName(user.getRealName())
            );
        }

        return dto;
    }

    private SigningRecordDTO toRecordDTO(SigningRecord record) {
        SigningRecordDTO dto = new SigningRecordDTO();
        dto.setId(record.getId());
        dto.setTaskId(record.getTaskId());
        dto.setSignerUserId(record.getSignerUserId());
        dto.setSignOrder(record.getSignOrder());
        dto.setStatus(record.getStatus());
        dto.setConfirmedAt(record.getConfirmedAt());
        dto.setIpAddress(record.getIpAddress());
        dto.setDocumentHash(record.getDocumentHash());
        dto.setRemark(record.getRemark());

        if (record.getSignerUserId() != null) {
            userRepository.findById(record.getSignerUserId()).ifPresent(user -> {
                dto.setSignerName(user.getRealName());
                dto.setSignerEmployeeId(user.getEmployeeId());
            });
        }

        return dto;
    }
}
