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
# Phase 13.6: 双引擎并存(mobile + server),按 API model 参数选择
_ocr_engines = {}      # key="lang:model" -> PaddleOCR engine
_engine_errors = {}    # key -> error message(加载失败时记录)


def _create_engine(lang: str, model: str):
    """创建指定模型的 OCR 引擎"""
    from paddleocr import PaddleOCR
    if model == 'server':
        det_name = "PP-OCRv5_server_det"
        rec_name = "PP-OCRv5_server_rec"
    else:
        det_name = "PP-OCRv5_mobile_det"
        rec_name = "PP-OCRv5_mobile_rec"
    return PaddleOCR(
        lang=lang,
        text_detection_model_name=det_name,
        text_recognition_model_name=rec_name,
        textline_orientation_model_name="PP-LCNet_x0_25_textline_ori",
        use_textline_orientation=True,  # 文字方向分类
        use_doc_orientation_classify=False,  # PDF 已正向
        use_doc_unwarping=False,
    )


def get_ocr_engine(lang: str = 'ch', model: str = 'mobile'):
    """延迟加载 OCR 引擎(首次调用初始化,后续从缓存取)

    Returns: (engine, error) 二元组。engine 为 None 表示加载失败,error 含原因。
    """
    key = f"{lang}:{model}"
    if key in _ocr_engines:
        return _ocr_engines[key], None
    if key in _engine_errors:
        return None, _engine_errors[key]
    try:
        engine = _create_engine(lang, model)
        _ocr_engines[key] = engine
        logging.info(f"PaddleOCR 引擎加载成功: lang={lang}, model={model}")
        return engine, None
    except Exception as e:
        _engine_errors[key] = str(e)
        logging.error(f"PaddleOCR 引擎加载失败: lang={lang}, model={model}, err={e}", exc_info=True)
        return None, str(e)


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
    """健康检查 - 返回已加载的引擎"""
    loaded = [k.split(':')[1] for k in _ocr_engines.keys()]
    failed = {k.split(':')[1]: v for k, v in _engine_errors.items()}
    return jsonify({
        'status': 'ok',
        'service': 'ocr-paddle',
        'engine': 'PaddleOCR',
        'loaded_engines': loaded,
        'failed_engines': failed,
        'supported_models': ['mobile', 'server'],
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
    """对 PDF 文件进行 PaddleOCR 识别（兼容 Tesseract 接口）

    form 参数:
        file: PDF 文件
        language: ch/en/japan/korean
        model: mobile(默认,轻量)/ server(高精度)
        use_table, use_layout, return_coords
    """
    if 'file' not in request.files:
        return jsonify({'error': 'No file provided'}), 400

    file = request.files['file']
    language = request.form.get('language', 'ch')
    model = request.form.get('model', 'mobile').lower()
    if model not in ('mobile', 'server'):
        model = 'mobile'
    use_table = request.form.get('use_table', 'true').lower() == 'true'
    use_layout = request.form.get('use_layout', 'true').lower() == 'true'
    return_coords = request.form.get('return_coords', 'true').lower() == 'true'

    lang_code = SUPPORTED_LANGUAGES.get(language, 'ch')

    ocr, err = get_ocr_engine(lang_code, model)
    if ocr is None:
        # server 加载失败时降级 mobile(并提示)
        if model == 'server':
            logging.warning(f"server 引擎不可用({err}),降级 mobile")
            ocr, err2 = get_ocr_engine(lang_code, 'mobile')
            if ocr is None:
                return jsonify({'error': f'所有引擎不可用: {err}; {err2}'}), 500
            model = 'mobile'
            degraded = True
        else:
            return jsonify({'error': f'引擎加载失败: {err}'}), 500
    else:
        degraded = False

    try:
        start = time.time()
        pdf_bytes = file.read()
        logger.info(f"PaddleOCR 识别开始: size={len(pdf_bytes)/1024:.1f}KB, lang={language}, model={model}")

        # PDF 转图片（DPI 200 平衡精度和性能）
        images = convert_from_bytes(pdf_bytes, dpi=200)
        logger.info(f"PDF 转图片: {len(images)} 页")

        pages = []
        full_text_parts = []

        for i, image in enumerate(images):
            page_start = time.time()
            logger.info(f"识别第 {i+1}/{len(images)} 页(model={model})...")

            img_array = np.array(image)
            page_results = ocr.predict(img_array)

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
                'tables': [],
                'regions': regions,
                'confidence': avg_conf,
            })
            full_text_parts.append(page_text)

            logger.info(f"第 {i+1} 页完成: {len(page_text)} 字符, 置信度={avg_conf:.2f}, 耗时={time.time()-page_start:.1f}s")

        elapsed = time.time() - start
        result = {
            'status': 'success',
            'engine': 'paddleocr',
            'model': model,
            'degraded': degraded,
            'totalPages': len(images),
            'fullText': '\n\n'.join(full_text_parts),
            'pages': pages,
            'language': language,
            'elapsed': round(elapsed, 1),
            'avgConfidence': round(
                sum(p['confidence'] for p in pages) / len(pages) if pages else 0, 3
            ),
        }

        logger.info(f"PaddleOCR 识别完成: model={model}, {len(images)} 页, 总耗时={elapsed:.1f}s")
        return jsonify(result)

    except Exception as e:
        logger.error(f"PaddleOCR 识别失败: {e}", exc_info=True)
        return jsonify({'error': str(e), 'engine': 'paddleocr', 'model': model}), 500


@app.route('/ocr/image', methods=['POST'])
def ocr_image():
    """对单张图片进行 OCR 识别"""
    if 'file' not in request.files:
        return jsonify({'error': 'No file provided'}), 400

    file = request.files['file']
    language = request.form.get('language', 'ch')
    model = request.form.get('model', 'mobile').lower()
    if model not in ('mobile', 'server'):
        model = 'mobile'
    return_coords = request.form.get('return_coords', 'true').lower() == 'true'

    lang_code = SUPPORTED_LANGUAGES.get(language, 'ch')

    ocr, err = get_ocr_engine(lang_code, model)
    if ocr is None:
        if model == 'server':
            ocr, _ = get_ocr_engine(lang_code, 'mobile')
            model = 'mobile' if ocr else model
        if ocr is None:
            return jsonify({'error': f'引擎加载失败: {err}'}), 500

    try:
        img_bytes = file.read()
        img = Image.open(io.BytesIO(img_bytes))
        img_array = np.array(img)

        results = ocr.predict(img_array)

        if results:
            data = _convert_paddle_result(results[0])
            return jsonify({
                'status': 'success',
                'engine': 'paddleocr',
                'model': model,
                'text': data['text'],
                'regions': data['regions'] if return_coords else [],
                'confidence': data['confidence'],
                'language': language,
            })
        else:
            return jsonify({
                'status': 'success',
                'engine': 'paddleocr',
                'model': model,
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