package com.miaotong.doc.repository;

import com.miaotong.doc.entity.Mention;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MentionRepository extends JpaRepository<Mention, Long> {

    List<Mention> findByCommentId(Long commentId);

    List<Mention> findByMentionedUserId(Long mentionedUserId);

    void deleteByCommentId(Long commentId);
}
