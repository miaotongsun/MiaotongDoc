#!/usr/bin/env python3
"""
PaddleOCR 服务 - 中文扫描件 PDF 文字识别（主力）
兼容现有 Tesseract OCR 接口约定（/ocr/pdf、/ocr/image、/health、/ocr/languages）
基于 PaddleOCR 3.2+ API(paddlepaddle 3.0+ + paddleocr 3.2+,默认 PP-OCRv5 模型)
"""

import io
import logging
import time
from pathlib import Path

import numpy as np
from flask import Flask, request, jsonify
from flask_cors import CORS
from pdf2image import convert_from_bytes
from PIL import Image

# PaddleOCR 3.2+ 延迟导入(避免启动慢)
_ocr_engine = None


def get_ocr_engine(lang: str = 'ch'):
    """延迟加载 OCR 引擎（首次调用时初始化）"""
    global _ocr_engine
    if _ocr_engine is None:
        from paddleocr import PaddleOCR
        _ocr_engine = PaddleOCR(
            lang=lang,
            # Phase 13.4: 改用 mobile 模型,server_det 在容器内推理崩溃(std::exception)
            # mobile 精度略低但稳定,适合容器环境
            text_detection_model_name="PP-OCRv5_mobile_det",
            text_recognition_model_name="PP-OCRv5_mobile_rec",
            textline_orientation_model_name="PP-LCNet_x0_25_textline_ori",
            use_textline_orientation=True,  # 文字方向分类（替代 2.x 的 use_angle_cls）
            use_doc_orientation_classify=False,  # 不做整页方向判断（PDF 已是正向）
            use_doc_unwarping=False,  # 不做文档矫正（PDF 不需要）
        )
        logging.info(f"PaddleOCR 3.2+ 引擎初始化完成(lang={lang}, model=PP-OCRv5_mobile)")
    return _ocr_engine


app = Flask(__name__)
CORS(app)
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(name)s - %(message)s'
)
logger = logging.getLogger(__name__)


SUPPORTED_LANGUAGES = {
    'ch': 'ch',
    'en': 'en',
    'japan': 'japan',
    'korean': 'korean',
    'auto': 'ch',
}


@app.route('/health', methods=['GET'])
def health():
    """健康检查"""
    return jsonify({
        'status': 'ok',
        'service': 'ocr-paddle',
        'engine': 'PaddleOCR',
    })


@app.route('/ocr/languages', methods=['GET'])
def languages():
    """支持的语言列表"""
    return jsonify({
        'languages': [
            {'code': 'ch', 'name': '中文简体', 'recommended': True},
            {'code': 'en', 'name': 'English'},
            {'code': 'japan', 'name': '日本語'},
            {'code': 'korean', 'name': '한국어'},
        ]
    })


def _reconstruct_layout(regions: list) -> str:
    """根据坐标重建段落（同一行合并、相邻行合并为段落）

    规则：
      1. 按 (y 行号, x 列号) 排序（y 容差 15px 视为同一行）
      2. 同行文本用空格拼接
      3. 相邻行之间若 y 距离 > 30px（行间距较大），插入空行分隔段落
    """
    if not regions:
        return ''
    # 行号 = y // 15（容差 15px）
    rows: dict[int, list] = {}
    for r in regions:
        row_key = r['bbox'][1] // 15
        rows.setdefault(row_key, []).append(r)
    # 同行内按 x 排序
    sorted_rows = sorted(rows.values(), key=lambda lst: min(r['bbox'][1] for r in lst))
    paragraphs = []
    prev_y_end = None
    for row in sorted_rows:
        row.sort(key=lambda r: r['bbox'][0])
        line_text = ' '.join(r['text'] for r in row if r['text'])
        if not line_text:
            continue
        row_y_start = min(r['bbox'][1] for r in row)
        row_y_end = max(r['bbox'][1] + r['bbox'][3] for r in row)
        # 行间距大则分段
        if prev_y_end is not None and row_y_start - prev_y_end > 30:
            paragraphs.append('')  # 空行
        paragraphs.append(line_text)
        prev_y_end = row_y_end
    return '\n'.join(paragraphs)


def _convert_paddle_result(page_result: dict, layout: bool = False) -> dict:
    """将 PaddleOCR 3.0 单页结果转为统一结构

    Args:
        layout: True 时按坐标重建段落（保留行/段结构），False 时按检测顺序拼接
    """
    def _safe_list(v):
        if v is None:
            return []
        if hasattr(v, 'tolist'):
            return v.tolist() if hasattr(v, '__len__') else [v]
        return list(v) if v else []

    rec_texts = _safe_list(page_result.get('rec_texts'))
    rec_scores = _safe_list(page_result.get('rec_scores'))
    rec_polys_raw = page_result.get('rec_polys')
    rec_boxes_raw = page_result.get('rec_boxes')

    text_lines = []
    regions = []
    total_conf = 0.0
    count = 0

    for i, text in enumerate(rec_texts):
        if not text:
            continue
        conf = float(rec_scores[i]) if i < len(rec_scores) else 0.0
        total_conf += conf
        count += 1

        # 优先用 rec_boxes（xyxy 格式），降级到 rec_polys（多边形）
        if rec_boxes_raw is not None and i < len(rec_boxes_raw):
            box = rec_boxes_raw[i].tolist() if hasattr(rec_boxes_raw[i], 'tolist') else rec_boxes_raw[i]
            x1, y1, x2, y2 = box[:4]
            x, y, w, h = int(x1), int(y1), int(x2 - x1), int(y2 - y1)
        elif rec_polys_raw is not None and i < len(rec_polys_raw):
            poly = rec_polys_raw[i].tolist() if hasattr(rec_polys_raw[i], 'tolist') else rec_polys_raw[i]
            xs = [p[0] for p in poly]
            ys = [p[1] for p in poly]
            x = int(min(xs))
            y = int(min(ys))
            w = int(max(xs) - x)
            h = int(max(ys) - y)
        else:
            x, y, w, h = 0, 0, 0, 0

        text_lines.append(text)
        regions.append({
            'type': 'text',
            'text': text,
            'bbox': [x, y, w, h],
            'confidence': round(conf, 3),
        })

    avg_conf = total_conf / count if count > 0 else 0.0
    if layout and regions:
        page_text = _reconstruct_layout(regions)
    else:
        page_text = '\n'.join(text_lines)

    return {
        'text': page_text,
        'regions': regions,
        'confidence': round(avg_conf, 3),
        'layout': layout,
    }


@app.route('/ocr/pdf', methods=['POST'])
def ocr_pdf():
    """对 PDF 文件进行 PaddleOCR 识别（兼容 Tesseract 接口）"""
    if 'file' not in request.files:
        return jsonify({'error': 'No file provided'}), 400

    file = request.files['file']
    language = request.form.get('language', 'ch')
    use_table = request.form.get('use_table', 'true').lower() == 'true'  # 3.0 API 未实现，忽略
    use_layout = request.form.get('use_layout', 'true').lower() == 'true'  # 默认开启版面重建
    return_coords = request.form.get('return_coords', 'true').lower() == 'true'

    lang_code = SUPPORTED_LANGUAGES.get(language, 'ch')

    try:
        start = time.time()
        pdf_bytes = file.read()
        logger.info(f"PaddleOCR 3.0 识别开始: size={len(pdf_bytes)/1024:.1f}KB, lang={language}")

        # PDF 转图片（DPI 200 平衡精度和性能）
        images = convert_from_bytes(pdf_bytes, dpi=200)
        logger.info(f"PDF 转图片: {len(images)} 页")

        ocr = get_ocr_engine(lang_code)

        pages = []
        full_text_parts = []

        for i, image in enumerate(images):
            page_start = time.time()
            logger.info(f"识别第 {i+1}/{len(images)} 页...")

            img_array = np.array(image)
            # PaddleOCR 3.0 用 predict() 替代 2.x 的 ocr()
            page_results = ocr.predict(img_array)

            # page_results 是 list，每个元素是一页的 dict
            page_text = ''
            regions = []
            avg_conf = 0.0

            if page_results:
                page_data = _convert_paddle_result(page_results[0], layout=use_layout)
                page_text = page_data['text']
                if return_coords:
                    regions = page_data['regions']
                avg_conf = page_data['confidence']

            pages.append({
                'pageNum': i + 1,
                'text': page_text,
                'tables': [],  # PaddleOCR 3.0 表格识别 API 复杂，暂不实现
                'regions': regions,
                'confidence': avg_conf,
            })
            full_text_parts.append(page_text)

            logger.info(f"第 {i+1} 页完成: {len(page_text)} 字符, 置信度={avg_conf:.2f}, 耗时={time.time()-page_start:.1f}s")

        elapsed = time.time() - start
        result = {
            'status': 'success',
            'engine': 'paddleocr',
            'totalPages': len(images),
            'fullText': '\n\n'.join(full_text_parts),
            'pages': pages,
            'language': language,
            'elapsed': round(elapsed, 1),
            'avgConfidence': round(
                sum(p['confidence'] for p in pages) / len(pages) if pages else 0, 3
            ),
        }

        logger.info(f"PaddleOCR 识别完成: {len(images)} 页, 总耗时={elapsed:.1f}s, 总字符={len(result['fullText'])}")
        return jsonify(result)

    except Exception as e:
        logger.error(f"PaddleOCR 识别失败: {e}", exc_info=True)
        return jsonify({'error': str(e), 'engine': 'paddleocr'}), 500


@app.route('/ocr/image', methods=['POST'])
def ocr_image():
    """对单张图片进行 OCR 识别"""
    if 'file' not in request.files:
        return jsonify({'error': 'No file provided'}), 400

    file = request.files['file']
    language = request.form.get('language', 'ch')
    return_coords = request.form.get('return_coords', 'true').lower() == 'true'

    lang_code = SUPPORTED_LANGUAGES.get(language, 'ch')

    try:
        img_bytes = file.read()
        img = Image.open(io.BytesIO(img_bytes))
        img_array = np.array(img)

        ocr = get_ocr_engine(lang_code)
        results = ocr.predict(img_array)

        if results:
            data = _convert_paddle_result(results[0])
            return jsonify({
                'status': 'success',
                'engine': 'paddleocr',
                'text': data['text'],
                'regions': data['regions'] if return_coords else [],
                'confidence': data['confidence'],
                'language': language,
            })
        else:
            return jsonify({
                'status': 'success',
                'engine': 'paddleocr',
                'text': '',
                'regions': [],
                'confidence': 0.0,
                'language': language,
            })
    except Exception as e:
        logger.error(f"图片 OCR 失败: {e}", exc_info=True)
        return jsonify({'error': str(e)}), 500


if __name__ == '__main__':
    logger.info("=" * 60)
    logger.info("PaddleOCR 3.0 服务启动中...")
    logger.info("端口: 5003")
    logger.info("接口: /health /ocr/pdf /ocr/image /ocr/languages")
    logger.info("=" * 60)
    app.run(host='0.0.0.0', port=5003, debug=False)