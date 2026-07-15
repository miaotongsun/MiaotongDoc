-- V26__add_pdf_ocr_data.sql
-- 存储 PDF 识别后的坐标数据，用于在 PDF 原图位置叠加文字层（用户可框选复制）

ALTER TABLE mt_document ADD COLUMN IF NOT EXISTS pdf_ocr_data JSONB DEFAULT '{}';

COMMENT ON COLUMN mt_document.pdf_ocr_data IS
  'PDF OCR 坐标数据：{"1": {"dpi": 200, "width": 1728, "height": 2400, "regions": [{"text": "...", "bbox": [x, y, w, h], "confidence": 0.98}, ...]}}}';

CREATE INDEX IF NOT EXISTS idx_mt_document_pdf_recognized_true
  ON mt_document(pdf_recognized) WHERE pdf_recognized = TRUE;