package com.miaotong.doc.service;

import com.miaotong.doc.constants.NotificationType;
import com.miaotong.doc.entity.Document;
import com.miaotong.doc.entity.DocumentShare;
import com.miaotong.doc.exception.BusinessException;
import com.miaotong.doc.exception.NotFoundException;
import com.miaotong.doc.repository.DocumentRepository;
import com.miaotong.doc.repository.DocumentShareRepository;
import com.miaotong.doc.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ShareService {

    private final DocumentShareRepository shareRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final ActivityService activityService;

    @Transactional
    public DocumentShare shareDocument(Long documentId, Long userId, Long sharedBy, String permission, String systemRole) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new NotFoundException("文档不存在"));

        // 校验分享者是否有 admin 权限（系统管理员绕过）
        if (!"admin".equals(systemRole)) {
            String callerPerm = getUserPermission(documentId, sharedBy);
            if (!"admin".equals(callerPerm)) {
                throw new BusinessException("只有文档所有者或管理员可以分享");
            }
        }

        if (shareRepository.existsByDocumentIdAndUserId(documentId, userId)) {
            throw new BusinessException("已经共享给该用户");
        }

        DocumentShare share = new DocumentShare();
        share.setDocumentId(documentId);
        share.setUserId(userId);
        share.setSharedBy(sharedBy);
        share.setPermission(permission);

        share = shareRepository.save(share);
        auditService.log(sharedBy, "SHARE", "DOCUMENT", documentId, null);
        activityService.log(sharedBy, documentId, "SHARE", userId);

        String permLabel = permLabel(permission);
        notificationService.notify(sharedBy, userId, documentId,
                NotificationType.SHARE, "邀请您协同编辑文档（" + permLabel + "）");

        return share;
    }

    public List<DocumentShare> getDocumentShares(Long documentId) {
        return shareRepository.findByDocumentId(documentId);
    }

    @Transactional
    public int shareToDepartment(Long documentId, Long departmentId, String permission, Long sharedBy, String systemRole) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new NotFoundException("文档不存在"));

        if (!"admin".equals(systemRole)) {
            String callerPerm = getUserPermission(documentId, sharedBy);
            if (!"admin".equals(callerPerm)) {
                throw new BusinessException("只有文档所有者或管理员可以分享");
            }
        }

        List<Long> userIds = userRepository.findByDepartmentId(departmentId)
                .stream().filter(u -> u.getIsActive() && !u.getId().equals(doc.getOwnerUserId()))
                .map(u -> u.getId()).toList();

        int count = 0;
        for (Long uid : userIds) {
            if (!shareRepository.existsByDocumentIdAndUserId(documentId, uid)) {
                DocumentShare share = new DocumentShare();
                share.setDocumentId(documentId);
                share.setUserId(uid);
                share.setSharedBy(sharedBy);
                share.setPermission(permission);
                shareRepository.save(share);
                count++;
                String permLabel = permLabel(permission);
                notificationService.notify(sharedBy, uid, documentId,
                        NotificationType.SHARE, "邀请您协同编辑文档（" + permLabel + "）");
            }
        }

        auditService.log(sharedBy, "SHARE_DEPARTMENT", "DOCUMENT", documentId, "dept:" + departmentId);
        return count;
    }

    @Transactional
    public void updatePermission(Long shareId, String permission, Long userId, String systemRole) {
        DocumentShare share = shareRepository.findById(shareId)
                .orElseThrow(() -> new NotFoundException("共享记录不存在"));

        // 校验调用者是否有 admin 权限（系统管理员绕过）
        if (!"admin".equals(systemRole)) {
            String callerPerm = getUserPermission(share.getDocumentId(), userId);
            if (!"admin".equals(callerPerm)) {
                throw new BusinessException("只有文档所有者或管理员可以修改共享权限");
            }
        }

        share.setPermission(permission);
        shareRepository.save(share);

        notificationService.notify(userId, share.getUserId(), share.getDocumentId(),
                NotificationType.PERMISSION_CHANGE, "权限已变更为" + permLabel(permission));
    }

    @Transactional
    public void removeShare(Long shareId, Long userId, String systemRole) {
        DocumentShare share = shareRepository.findById(shareId)
                .orElseThrow(() -> new NotFoundException("共享记录不存在"));

        // 校验调用者是否有 admin 权限（系统管理员绕过，被共享者也可自行撤回）
        if (!"admin".equals(systemRole)) {
            String callerPerm = getUserPermission(share.getDocumentId(), userId);
            if (!"admin".equals(callerPerm) && !share.getUserId().equals(userId)) {
                throw new BusinessException("无权操作此共享");
            }
        }

        shareRepository.deleteById(shareId);

        notificationService.notify(userId, share.getUserId(), share.getDocumentId(),
                NotificationType.REVOKE, "已撤回您的文档共享权限");
    }

    /**
     * 获取用户对文档的权限
     * @param documentId 文档ID
     * @param userId 用户ID
     * @param systemRole 系统角色（可选，用于管理员默认权限）
     * @return 权限级别：admin > edit > comment > view，无权限返回 null
     */
    public String getUserPermission(Long documentId, Long userId, String... systemRole) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new NotFoundException("文档不存在"));

        if (doc.getOwnerUserId().equals(userId)) {
            return "admin";
        }

        DocumentShare share = shareRepository.findByDocumentIdAndUserId(documentId, userId)
                .orElse(null);

        if (share != null) {
            return share.getPermission();
        }

        // 管理员无显式授权时，默认只读
        if (systemRole.length > 0 && "admin".equals(systemRole[0])) {
            return "view";
        }

        return null;
    }

    /**
     * 获取用户对文档的权限（兼容旧调用）
     */
    public String getUserPermission(Long documentId, Long userId) {
        return getUserPermission(documentId, userId, (String[]) null);
    }

    /**
     * @提及专用：如果用户没有文档权限，授予查看权限
     * @return true 如果新授予权限，false 如果已有权限
     */
    public boolean grantViewIfAbsent(Long documentId, Long userId, Long grantedBy) {
        if (shareRepository.existsByDocumentIdAndUserId(documentId, userId)) {
            return false;
        }
        DocumentShare share = new DocumentShare();
        share.setDocumentId(documentId);
        share.setUserId(userId);
        share.setSharedBy(grantedBy);
        share.setPermission("view");
        shareRepository.save(share);
        auditService.log(grantedBy, "SHARE", "DOCUMENT", documentId, null);
        activityService.log(grantedBy, documentId, "SHARE", userId);
        return true;
    }

    private String permLabel(String permission) {
        return switch (permission) {
            case "view" -> "只读";
            case "comment" -> "评论";
            case "edit" -> "编辑";
            case "admin" -> "管理";
            default -> permission;
        };
    }
}
