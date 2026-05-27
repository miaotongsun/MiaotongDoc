package com.miaotong.doc.service;

import com.miaotong.doc.entity.DocumentVersion;
import com.miaotong.doc.exception.NotFoundException;
import com.miaotong.doc.repository.DocumentVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VersionService {

    private final DocumentVersionRepository versionRepository;

    public List<DocumentVersion> getVersionHistory(Long documentId) {
        return versionRepository.findByDocumentIdOrderByVersionNumberDesc(documentId);
    }

    public DocumentVersion getVersion(Long documentId, Integer versionNumber) {
        return versionRepository.findByDocumentIdAndVersionNumber(documentId, versionNumber)
                .orElseThrow(() -> new NotFoundException("版本不存在"));
    }

    public DocumentVersion createVersion(Long documentId, int versionNumber, byte[] content, String hash) {
        DocumentVersion version = new DocumentVersion();
        version.setDocumentId(documentId);
        version.setVersionNumber(versionNumber);
        version.setFilePath("v" + versionNumber);
        version.setFileSize((long) content.length);
        version.setFileHash(hash);
        return versionRepository.save(version);
    }
}
