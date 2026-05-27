package com.miaotong.doc.service;

import com.miaotong.doc.entity.Activity;
import com.miaotong.doc.entity.Document;
import com.miaotong.doc.entity.User;
import com.miaotong.doc.repository.ActivityRepository;
import com.miaotong.doc.repository.DocumentRepository;
import com.miaotong.doc.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ActivityService {

    private final ActivityRepository activityRepository;
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;

    public void log(Long userId, Long documentId, String action, Long targetUserId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return;

        Activity activity = new Activity();
        activity.setDocumentId(documentId);
        activity.setUserId(userId);
        activity.setUserName(user.getRealName());
        activity.setAction(action);
        activity.setTargetUserId(targetUserId);

        if (documentId != null) {
            documentRepository.findById(documentId).ifPresent(doc ->
                activity.setDocumentTitle(doc.getTitle())
            );
        }

        activityRepository.save(activity);
    }

    public Page<Activity> getUserActivities(Long userId, Pageable pageable) {
        return activityRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    public Page<Activity> getDocumentActivities(Long documentId, Pageable pageable) {
        return activityRepository.findByDocumentIdOrderByCreatedAtDesc(documentId, pageable);
    }
}
