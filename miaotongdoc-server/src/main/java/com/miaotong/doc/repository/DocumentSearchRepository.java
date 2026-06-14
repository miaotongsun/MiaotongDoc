package com.miaotong.doc.repository;

import com.miaotong.doc.entity.DocumentIndex;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentSearchRepository extends ElasticsearchRepository<DocumentIndex, String> {

    List<DocumentIndex> findByDocumentId(Long documentId);

    // 全文搜索：标题和内容
    List<DocumentIndex> searchByTitleOrContent(String title, String content);
}
