package com.miaotong.doc.controller;

import com.miaotong.doc.dto.ActivityDTO;
import com.miaotong.doc.entity.Activity;
import com.miaotong.doc.service.ActivityService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    @GetMapping("/feed")
    public ResponseEntity<Page<ActivityDTO>> getMyActivities(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        Page<Activity> activities = activityService.getUserActivities(userId, PageRequest.of(page, size));
        return ResponseEntity.ok(activities.map(this::toDTO));
    }

    @GetMapping("/document/{docId}")
    public ResponseEntity<Page<ActivityDTO>> getDocumentActivities(
            @PathVariable Long docId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Activity> activities = activityService.getDocumentActivities(docId, PageRequest.of(page, size));
        return ResponseEntity.ok(activities.map(this::toDTO));
    }

    private ActivityDTO toDTO(Activity activity) {
        ActivityDTO dto = new ActivityDTO();
        dto.setId(activity.getId());
        dto.setDocumentId(activity.getDocumentId());
        dto.setDocumentTitle(activity.getDocumentTitle());
        dto.setUserId(activity.getUserId());
        dto.setUserName(activity.getUserName());
        dto.setAction(activity.getAction());
        dto.setTargetUserId(activity.getTargetUserId());
        dto.setDetail(activity.getDetail());
        dto.setCreatedAt(activity.getCreatedAt());
        return dto;
    }
}
