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
            // 2026-08-09 增强:支持斜杠无前导 0、英文月名等
            DateTimeFormatter.ofPattern("yyyy/M/d"),
            DateTimeFormatter.ofPattern("yyyy-M-d"),
            DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMMM yyyy", java.util.Locale.ENGLISH),
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

        // Pattern 4 (2026-08-09): 备案号/签约编号 fallback
        Pattern p3 = Pattern.compile(
                "(?:备案号|签约编号|订单编号|项目编号|文号|档案号)" +
                "\\s*[:：]?\\s*([A-Za-z0-9\\-_/]+(?:\\s*[A-Za-z0-9\\-_/]+)*)");
        Matcher m3 = p3.matcher(text);
        if (m3.find()) {
            String val = m3.group(1).trim();
            if (val.length() >= 3) return val;
        }

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
                "(?:合同金额|合同总价|合同价款|总金额|总价|金额|价款|合同价格|总费用|报酬|服务费|租金|承揽费|总价金额|合同总价款)" +
                "\\s*[:：]?\\s*(?:人民币)?\\s*(\\d+\\.?\\d*)\\s*万元");
        Matcher m1 = p1.matcher(normalized);
        if (m1.find()) {
            try {
                return new BigDecimal(m1.group(1)).multiply(new BigDecimal("10000"));
            } catch (NumberFormatException ignored) {}
        }

        // Pattern 2: label + 元
        Pattern p2 = Pattern.compile(
                "(?:合同金额|合同总价|合同价款|总金额|总价|金额|价款|合同价格|总费用|报酬|服务费|租金|承揽费|总价金额|合同总价款)" +
                "\\s*[:：]?\\s*(?:人民币)?\\s*(\\d+\\.?\\d*)\\s*元");
        Matcher m2 = p2.matcher(normalized);
        if (m2.find()) {
            try {
                return new BigDecimal(m2.group(1));
            } catch (NumberFormatException ignored) {}
        }

        // Pattern 3: label + number (no explicit unit) - check nearby for 万元
        Pattern p3 = Pattern.compile(
                "(?:合同金额|合同总价|合同价款|总金额|总价|金额|价款|合同价格|总费用|报酬|服务费|租金|承揽费|总价金额|合同总价款)" +
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

        // Pattern 5 (2026-08-09): Chinese uppercase amount (壹/贰/叁/肆/伍/陆/柒/捌/玖/拾/佰/仟/万/亿)
        // 匹配 "人民币壹拾万元整" / "壹佰万元整" 等
        Pattern p5 = Pattern.compile(
                "(?:合同金额|合同总价|合同价款|总金额|总价|金额|价款|合同价格|总费用|报酬|服务费|租金|承揽费)?\\s*[:：]?" +
                "(?:人民币)?\\s*([壹贰叁肆伍陆柒捌玖拾佰仟万亿圆角分整]+)");
        Matcher m5 = p5.matcher(text);
        if (m5.find()) {
            BigDecimal val = parseChineseAmount(m5.group(1));
            if (val != null && val.compareTo(BigDecimal.ZERO) > 0) return val;
        }

        return null;
    }

    /**
     * 将中文大写金额转换为 BigDecimal(支持 壹贰叁肆伍陆柒捌玖 拾佰仟万亿圆角分整)
     */
    private BigDecimal parseChineseAmount(String chinese) {
        if (chinese == null || chinese.isEmpty()) return null;
        // 只保留数字字符和单位
        String s = chinese.replaceAll("[^壹贰叁肆伍陆柒捌玖拾佰仟万亿圆角分]", "");
        if (s.isEmpty()) return null;

        // 万 亿 段位处理
        long result = 0;
        long currentSection = 0;  // 万/亿 段
        long currentNumber = 0;   // 当前数字
        boolean hasValue = false;

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '壹': currentNumber = currentNumber * 10 + 1; hasValue = true; break;
                case '贰': currentNumber = currentNumber * 10 + 2; hasValue = true; break;
                case '叁': currentNumber = currentNumber * 10 + 3; hasValue = true; break;
                case '肆': currentNumber = currentNumber * 10 + 4; hasValue = true; break;
                case '伍': currentNumber = currentNumber * 10 + 5; hasValue = true; break;
                case '陆': currentNumber = currentNumber * 10 + 6; hasValue = true; break;
                case '柒': currentNumber = currentNumber * 10 + 7; hasValue = true; break;
                case '捌': currentNumber = currentNumber * 10 + 8; hasValue = true; break;
                case '玖': currentNumber = currentNumber * 10 + 9; hasValue = true; break;
                case '拾':
                    if (currentNumber == 0) currentNumber = 10;
                    else currentNumber = currentNumber * 10;
                    hasValue = true;
                    break;
                case '佰': currentNumber *= 100; hasValue = true; break;
                case '仟': currentNumber *= 1000; hasValue = true; break;
                case '万':
                    if (currentNumber == 0) currentSection += 1_0000;
                    else currentSection += currentNumber * 1_0000;
                    currentNumber = 0;
                    hasValue = true;
                    break;
                case '亿':
                    if (currentNumber == 0) result += 1_0000_0000L;
                    else result += currentNumber * 1_0000_0000L;
                    currentNumber = 0;
                    currentSection = 0;
                    hasValue = true;
                    break;
                case '圆': case '元':
                    currentSection += currentNumber;
                    currentNumber = 0;
                    break;
                case '角':
                    currentNumber = currentNumber * 10;
                    currentSection += currentNumber;
                    currentNumber = 0;
                    break;
                case '分':
                    currentNumber *= 10;
                    currentSection += currentNumber;
                    currentNumber = 0;
                    break;
                default: break;
            }
        }
        currentSection += currentNumber;
        result += currentSection;
        return hasValue ? BigDecimal.valueOf(result) : null;
    }

    private LocalDate extractDate(String text, String keywords) {
        // 2026-08-09 增强:支持 /、.、年-月-日、英文月名等多种格式
        String dateRegex = "(?:(\\d{4})[年\\-/\\.](\\d{1,2})[月\\-/\\.](\\d{1,2})日?)" +
                "|(?:(\\d{1,2})\\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+(\\d{4}))" +
                "|(?:(\\d{4})[年](\\d{1,2})[月](\\d{1,2}))";

        // Pattern 1: keyword + colon/space + date
        Pattern keyPattern = Pattern.compile("(" + keywords + ")\\s*[:：]?\\s*.{0,10}(" + dateRegex + ")");
        Matcher m = keyPattern.matcher(text);
        if (m.find()) {
            for (int i = 1; i <= m.groupCount(); i++) {
                String g = m.group(i);
                if (g != null && !g.isEmpty() && g.matches(".*\\d.*")) {
                    LocalDate parsed = parseDateStr(g);
                    if (parsed != null) return parsed;
                }
            }
        }

        // Pattern 2: keyword followed by date-like text within 50 chars
        Pattern fallback = Pattern.compile("(" + keywords + ")\\s*[:：]?\\s*(.{4,50})");
        Matcher fm = fallback.matcher(text);
        if (fm.find()) {
            String candidate = fm.group(2).trim();
            // 优先尝试标准格式
            LocalDate parsed = parseDateStr(candidate);
            if (parsed != null) return parsed;
            // 退化到正则匹配
            Pattern dp = Pattern.compile("\\d{4}[年\\-/\\.]\\d{1,2}[月\\-/\\.]\\d{1,2}日?");
            Matcher dm = dp.matcher(candidate);
            if (dm.find()) {
                return parseDateStr(dm.group(0));
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
        // 2026-08-09 增强:支持以任意分隔符连接的数字日期
        Pattern dp = Pattern.compile("(\\d{4})[年\\-/\\.\\s](\\d{1,2})[月\\-/\\.\\s](\\d{1,2})");
        Matcher dm = dp.matcher(dateStr);
        if (dm.find()) {
            String normalized = dm.group(1) + "-" +
                    String.format("%02d", Integer.parseInt(dm.group(2))) + "-" +
                    String.format("%02d", Integer.parseInt(dm.group(3)));
            try {
                return LocalDate.parse(normalized);
            } catch (Exception ignored) {}
        }
        // 兼容 "2026年8月8日" 这种没"-"连接的
        Pattern dp2 = Pattern.compile("(\\d{4})\\D+(\\d{1,2})\\D+(\\d{1,2})");
        Matcher dm2 = dp2.matcher(dateStr);
        if (dm2.find()) {
            String normalized = dm2.group(1) + "-" +
                    String.format("%02d", Integer.parseInt(dm2.group(2))) + "-" +
                    String.format("%02d", Integer.parseInt(dm2.group(3)));
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
