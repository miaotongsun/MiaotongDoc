package com.miaotong.doc.service;

import com.miaotong.doc.dto.CreateCommentRequest;
import com.miaotong.doc.dto.CommentDTO;
import com.miaotong.doc.dto.MentionDTO;
import com.miaotong.doc.entity.Comment;
import com.miaotong.doc.entity.Mention;
import com.miaotong.doc.entity.User;
import com.miaotong.doc.exception.NotFoundException;
import com.miaotong.doc.repository.CommentRepository;
import com.miaotong.doc.repository.MentionRepository;
import com.miaotong.doc.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final MentionRepository mentionRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final ActivityService activityService;

    @Transactional
    public CommentDTO createComment(CreateCommentRequest request, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("用户不存在"));

        Comment comment = new Comment();
        comment.setDocumentId(request.getDocumentId());
        comment.setParentId(request.getParentId());
        comment.setUserId(userId);
        comment.setContent(request.getContent());
        comment.setQuoteText(request.getQuoteText());
        comment.setPageNumber(request.getPageNumber());
        comment.setPosition(request.getPosition());

        comment = commentRepository.save(comment);

        if (request.getMentionUserIds() != null && !request.getMentionUserIds().isEmpty()) {
            for (Long mentionedUserId : request.getMentionUserIds()) {
                Mention mention = new Mention();
                mention.setCommentId(comment.getId());
                mention.setMentionedUserId(mentionedUserId);
                mentionRepository.save(mention);

                notificationService.notify(userId, mentionedUserId, request.getDocumentId(),
                        "MENTION", user.getRealName() + " 在评论中提到了你");
            }
        }

        activityService.log(userId, request.getDocumentId(), "COMMENT", null);

        return toDTO(comment, user);
    }

    public List<CommentDTO> getDocumentComments(Long documentId) {
        List<Comment> rootComments = commentRepository.findByDocumentIdAndParentIdIsNullAndIsDeletedFalseOrderByCreatedAtDesc(documentId);
        return rootComments.stream()
                .map(comment -> {
                    User user = userRepository.findById(comment.getUserId()).orElse(null);
                    CommentDTO dto = toDTO(comment, user);
                    dto.setReplies(getReplies(comment.getId()));
                    dto.setMentions(getMentions(comment.getId()));
                    return dto;
                })
                .collect(Collectors.toList());
    }

    private List<CommentDTO> getReplies(Long parentId) {
        List<Comment> replies = commentRepository.findByParentIdAndIsDeletedFalseOrderByCreatedAtAsc(parentId);
        return replies.stream()
                .map(reply -> {
                    User user = userRepository.findById(reply.getUserId()).orElse(null);
                    return toDTO(reply, user);
                })
                .collect(Collectors.toList());
    }

    private List<MentionDTO> getMentions(Long commentId) {
        List<Mention> mentions = mentionRepository.findByCommentId(commentId);
        return mentions.stream()
                .map(m -> {
                    User user = userRepository.findById(m.getMentionedUserId()).orElse(null);
                    return new MentionDTO(m.getId(), m.getCommentId(), m.getMentionedUserId(),
                            user != null ? user.getRealName() : null);
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public void resolveComment(Long commentId, Long userId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("评论不存在"));
        comment.setIsResolved(true);
        comment.setResolvedBy(userId);
        comment.setResolvedAt(LocalDateTime.now());
        commentRepository.save(comment);

        activityService.log(userId, comment.getDocumentId(), "RESOLVE", null);
    }

    @Transactional
    public void deleteComment(Long commentId, Long userId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("评论不存在"));
        comment.setIsDeleted(true);
        commentRepository.save(comment);
    }

    private CommentDTO toDTO(Comment comment, User user) {
        CommentDTO dto = new CommentDTO();
        dto.setId(comment.getId());
        dto.setDocumentId(comment.getDocumentId());
        dto.setParentId(comment.getParentId());
        dto.setUserId(comment.getUserId());
        dto.setUserName(user != null ? user.getRealName() : null);
        dto.setEmployeeId(user != null ? user.getEmployeeId() : null);
        dto.setContent(comment.getContent());
        dto.setQuoteText(comment.getQuoteText());
        dto.setPageNumber(comment.getPageNumber());
        dto.setPosition(comment.getPosition());
        dto.setIsResolved(comment.getIsResolved());
        dto.setResolvedBy(comment.getResolvedBy());
        dto.setResolvedAt(comment.getResolvedAt());
        dto.setCreatedAt(comment.getCreatedAt());
        return dto;
    }
}
