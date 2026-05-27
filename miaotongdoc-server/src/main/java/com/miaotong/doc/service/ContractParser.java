package com.miaotong.doc.service;

import com.miaotong.doc.entity.Contract;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Component;
import java.io.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;

@Component
public class ContractParser {

    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ofPattern("yyyy年M月d日"),
            DateTimeFormatter.ofPattern("yyyy年MM月dd日"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyy.MM.dd"),
    };

    public Contract parse(byte[] docxContent) throws IOException {
        String fullText = extractText(docxContent);
        Contract contract = new Contract();

        contract.setContractNo(extractContractNo(fullText));
        contract.setPartyA(extractParty(fullText, "甲方"));
        contract.setPartyB(extractParty(fullText, "乙方"));
        contract.setAmount(extractAmount(fullText));
        contract.setSigningDate(extractDate(fullText, "签订日期|签署日期|签约日期|签章日期"));
        contract.setEffectiveDate(extractDate(fullText, "生效日期|生效时间"));
        contract.setExpiryDate(extractDate(fullText, "到期日期|届满日期|终止日期|有效期至|届满之日|截止日期|结束日期|有效期"));
        contract.setContractType(guessContractType(fullText));

        return contract;
    }

    private String extractText(byte[] docxContent) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docxContent))) {
            for (XWPFParagraph para : doc.getParagraphs()) {
                String text = para.getText();
                if (text != null && !text.trim().isEmpty()) {
                    sb.append(text.trim()).append("\n");
                }
            }
            for (XWPFTable table : doc.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        String cellText = cell.getText();
                        if (cellText != null && !cellText.trim().isEmpty()) {
                            sb.append(cellText.trim()).append(" ");
                        }
                    }
                    sb.append("\n");
                }
            }
        }
        return sb.toString();
    }

    private String extractContractNo(String text) {
        // Pattern 1: keyword + colon + value (handles full/half width colon, optional parentheses)
        Pattern p1 = Pattern.compile(
                "(?:合同编号|合同号|协议编号|协议号|Contract\\s*[Nn]o\\.?|编号|序号|No\\.?)" +
                "\\s*(?:[:(\\uff1a(（]\\s*(?:[\\u4e00-\\u9fa5]*?)\\s*[)）]\\s*)?" +
                "[:：]?\\s*([A-Za-z0-9\\-_/]+(?:\\s*[A-Za-z0-9\\-_/]+)*)");
        Matcher m1 = p1.matcher(text);
        if (m1.find()) {
            String val = m1.group(1).trim();
            if (val.length() >= 3) return val;
        }

        // Pattern 2: keyword on one line, value on next line
        Pattern p1b = Pattern.compile(
                "(?:合同编号|合同号|协议编号|协议号|编号|序号)" +
                "\\s*(?:[:(\\uff1a(（]\\s*(?:[\\u4e00-\\u9fa5]*?)\\s*[)）]\\s*)?" +
                "[:：]?\\s*\\n\\s*([A-Za-z0-9\\-_/]+(?:\\s*[A-Za-z0-9\\-_/]+)*)");
        Matcher m1b = p1b.matcher(text);
        if (m1b.find()) {
            String val = m1b.group(1).trim();
            if (val.length() >= 3) return val;
        }

        // Pattern 3: HT/XY prefix patterns (common contract number formats)
        Pattern p2 = Pattern.compile("([A-Z]{2,}[-/][0-9]{4}[-/][A-Za-z0-9]+)");
        Matcher m2 = p2.matcher(text);
        if (m2.find()) return m2.group(1).trim();

        return null;
    }

    private String extractParty(String text, String role) {
        // Normalize text: replace full-width colons with half-width
        String normalized = text.replace('：', ':').replace('（', '(').replace('）', ')');

        // Pattern 1: role (optional parens) : value
        // Handles: 甲方（盖章）：XX公司, 甲方：XX公司, 甲方:XX公司, 甲方（签章）:\nXX公司
        Pattern p1 = Pattern.compile(
                role + "\\s*(?:\\([^)]*\\))?\\s*:\\s*(?:\\n\\s*)?([^\\n]+)",
                Pattern.DOTALL);
        Matcher m1 = p1.matcher(normalized);
        if (m1.find()) {
            String val = m1.group(1).trim();
            val = cleanPartyName(val);
            if (val != null) return val;
        }

        // Pattern 2: role on its own line, value on next line (no colon)
        Pattern p2 = Pattern.compile(role + "(?:\\s*\\([^)]*\\))?\\s*\\n\\s*([^\\n]+)");
        Matcher m2 = p2.matcher(normalized);
        if (m2.find()) {
            String val = m2.group(1).trim();
            val = cleanPartyName(val);
            if (val != null) return val;
        }

        // Pattern 3: role in table-like format with spaces
        Pattern p3 = Pattern.compile(role + "\\s+(?:\\([^)]*\\)\\s+)?([^\\s\\n]{2,})");
        Matcher m3 = p3.matcher(normalized);
        if (m3.find()) {
            String val = m3.group(1).trim();
            val = cleanPartyName(val);
            if (val != null) return val;
        }

        return null;
    }

    private String cleanPartyName(String val) {
        if (val == null || val.isEmpty()) return null;
        // Remove common labels that are not actual names
        val = val.replaceAll("^(名称|姓名|单位名称|公司名称|地址|电话|联系人|签章|盖章|签字)\\s*[:：]?\\s*", "");
        // Remove trailing punctuation and whitespace
        val = val.replaceAll("[：:。.，,；;（(\\s]+$", "");
        // Remove trailing labels
        val = val.replaceAll("\\s*(乙方|丙方|签订日期|签署日期|签约日期|地址|电话|联系人|盖章|签章|签字).*$", "");
        val = val.trim();
        if (val.length() > 200) val = val.substring(0, 200);
        if (val.isEmpty() || val.length() < 2) return null;
        // Skip if value is just a label word
        if (val.matches("^(名称|姓名|单位名称|公司名称|地址|电话|联系人|签章|盖章|签字)$")) return null;
        return val;
    }

    private BigDecimal extractAmount(String text) {
        // Normalize: remove commas from numbers
        String normalized = text.replace(",", "").replace("，", "");

        // Pattern 1: label + 万元 (explicit 万 unit)
        Pattern p1 = Pattern.compile(
                "(?:合同金额|合同总价|合同价款|总金额|总价|金额|价款|合同价格|总费用|报酬|服务费|租金|承揽费)" +
                "\\s*[:：]?\\s*(?:人民币)?\\s*(\\d+\\.?\\d*)\\s*万元");
        Matcher m1 = p1.matcher(normalized);
        if (m1.find()) {
            try {
                return new BigDecimal(m1.group(1)).multiply(new BigDecimal("10000"));
            } catch (NumberFormatException ignored) {}
        }

        // Pattern 2: label + 元
        Pattern p2 = Pattern.compile(
                "(?:合同金额|合同总价|合同价款|总金额|总价|金额|价款|合同价格|总费用|报酬|服务费|租金|承揽费)" +
                "\\s*[:：]?\\s*(?:人民币)?\\s*(\\d+\\.?\\d*)\\s*元");
        Matcher m2 = p2.matcher(normalized);
        if (m2.find()) {
            try {
                return new BigDecimal(m2.group(1));
            } catch (NumberFormatException ignored) {}
        }

        // Pattern 3: label + number (no explicit unit) - check nearby for 万元
        Pattern p3 = Pattern.compile(
                "(?:合同金额|合同总价|合同价款|总金额|总价|金额|价款|合同价格|总费用|报酬|服务费|租金|承揽费)" +
                "\\s*[:：]?\\s*(?:人民币)?\\s*(\\d+\\.?\\d*)");
        Matcher m3 = p3.matcher(normalized);
        if (m3.find()) {
            try {
                BigDecimal val = new BigDecimal(m3.group(1));
                int endPos = m3.end();
                String afterText = normalized.substring(endPos, Math.min(endPos + 100, normalized.length()));
                if (afterText.contains("万元")) {
                    val = val.multiply(new BigDecimal("10000"));
                }
                return val;
            } catch (NumberFormatException ignored) {}
        }

        // Pattern 4: currency symbol prefix
        Pattern p4 = Pattern.compile("(?:¥|￥|RMB|US\\$|\\$)\\s*(\\d+\\.?\\d*)");
        Matcher m4 = p4.matcher(normalized);
        if (m4.find()) {
            try {
                return new BigDecimal(m4.group(1));
            } catch (NumberFormatException ignored) {}
        }

        return null;
    }

    private LocalDate extractDate(String text, String keywords) {
        String dateRegex = "(\\d{4})[年\\-/\\.](\\d{1,2})[月\\-/\\.](\\d{1,2})[日]?";

        // Pattern 1: keyword + colon/space + date
        Pattern keyPattern = Pattern.compile("(" + keywords + ")\\s*[:：]?\\s*.{0,10}(" + dateRegex + ")");
        Matcher m = keyPattern.matcher(text);
        if (m.find()) {
            return parseDateStr(m.group(2));
        }

        // Pattern 2: keyword followed by date-like text within 50 chars
        Pattern fallback = Pattern.compile("(" + keywords + ")\\s*[:：]?\\s*(.{4,50})");
        Matcher fm = fallback.matcher(text);
        if (fm.find()) {
            String candidate = fm.group(2).trim();
            Pattern dp = Pattern.compile(dateRegex);
            Matcher dm = dp.matcher(candidate);
            if (dm.find()) {
                return parseDateStr(dm.group(0));
            }
            for (DateTimeFormatter fmt : DATE_FORMATS) {
                try {
                    return LocalDate.parse(candidate.substring(0, Math.min(candidate.length(), 15)).trim(), fmt);
                } catch (Exception ignored) {}
            }
        }

        return null;
    }

    private LocalDate parseDateStr(String dateStr) {
        if (dateStr == null) return null;
        dateStr = dateStr.trim();
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDate.parse(dateStr, fmt);
            } catch (Exception ignored) {}
        }
        Pattern dp = Pattern.compile("(\\d{4})[年\\-/\\.](\\d{1,2})[月\\-/\\.](\\d{1,2})");
        Matcher dm = dp.matcher(dateStr);
        if (dm.find()) {
            String normalized = dm.group(1) + "-" +
                    String.format("%02d", Integer.parseInt(dm.group(2))) + "-" +
                    String.format("%02d", Integer.parseInt(dm.group(3)));
            try {
                return LocalDate.parse(normalized);
            } catch (Exception ignored) {}
        }
        return null;
    }

    private String guessContractType(String text) {
        Map<String, String[]> keywords = new LinkedHashMap<>();
        keywords.put("purchase", new String[]{"采购", "购买", "购销", "订购"});
        keywords.put("sale", new String[]{"销售", "供货", "供应"});
        keywords.put("lease", new String[]{"租赁", "租用", "出租", "承租"});
        keywords.put("service", new String[]{"服务", "咨询", "技术服务", "运维", "托管"});
        keywords.put("labor", new String[]{"劳动", "雇佣", "用工", "劳务"});
        keywords.put("construction", new String[]{"工程", "施工", "建设", "装修"});
        for (var entry : keywords.entrySet()) {
            for (String kw : entry.getValue()) {
                if (text.contains(kw)) return entry.getKey();
            }
        }
        return "other";
    }
}
