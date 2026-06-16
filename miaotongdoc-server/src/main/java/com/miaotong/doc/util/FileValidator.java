package com.miaotong.doc.util;

import com.miaotong.doc.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class FileValidator {

    private static final Map<String, byte[]> MAGIC_NUMBERS = Map.of(
            "docx", new byte[]{0x50, 0x4B, 0x03, 0x04},
            "xlsx", new byte[]{0x50, 0x4B, 0x03, 0x04},
            "pptx", new byte[]{0x50, 0x4B, 0x03, 0x04},
            "pdf",  new byte[]{0x25, 0x50, 0x44, 0x46}  // %PDF
    );

    /** 不需要 ZIP 内容扫描的格式 */
    private static final Set<String> NON_ZIP_FORMATS = Set.of("md", "pdf");

    public void validate(MultipartFile file, String expectedExt) {
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.endsWith("." + expectedExt)) {
            throw new BusinessException("文件扩展名不匹配: " + expectedExt);
        }

        // Markdown 文件：文本格式，只检查大小
        if ("md".equals(expectedExt)) {
            if (file.getSize() > 10 * 1024 * 1024) {
                throw new BusinessException("Markdown 文件大小超过 10MB 限制");
            }
            return;
        }

        // 魔数校验
        byte[] header = new byte[4];
        try (InputStream is = file.getInputStream()) {
            if (is.read(header) != 4 || !matchesMagic(header, expectedExt)) {
                throw new BusinessException("文件内容与声明类型不匹配");
            }
        } catch (IOException e) {
            throw new BusinessException("读取文件失败");
        }

        if (file.getSize() > 200 * 1024 * 1024) {
            throw new BusinessException("文件大小超过 200MB 限制");
        }

        // PDF 文件：跳过 ZIP 内容扫描
        if (NON_ZIP_FORMATS.contains(expectedExt)) {
            return;
        }

        // OOXML 文件：ZIP 内容扫描
        try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName().toLowerCase();
                if (name.contains("vba") || name.endsWith(".vbs") || name.endsWith(".exe")) {
                    throw new BusinessException("文档包含不允许的内嵌文件: " + name);
                }
                if (entry.getSize() > 100 * 1024 * 1024) {
                    throw new BusinessException("文档内嵌文件过大");
                }
            }
        } catch (IOException e) {
            throw new BusinessException("扫描文档失败");
        }
    }

    private boolean matchesMagic(byte[] header, String ext) {
        byte[] expected = MAGIC_NUMBERS.get(ext);
        if (expected == null) return false;
        for (int i = 0; i < expected.length; i++) {
            if (header[i] != expected[i]) return false;
        }
        return true;
    }
}
