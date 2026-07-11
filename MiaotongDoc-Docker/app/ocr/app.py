#!/usr/bin/env python3
"""
OCR 服务 - 使用 Tesseract 进行文字识别
用于扫描件 PDF 的文字提取
"""

import io
import logging
import tempfile
from pathlib import Path

from flask import Flask, request, jsonify
from pdf2image import convert_from_bytes
import pytesseract
from PIL import Image

app = Flask(__name__)
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# 支持的语言
SUPPORTED_LANGUAGES = {
    'zh': 'chi_sim',
    'en': 'eng',
    'ja': 'jpn',
    'ko': 'kor',
    'auto': 'chi_sim+eng'
}


@app.route('/health', methods=['GET'])
def health():
    """健康检查"""
    return jsonify({'status': 'ok', 'service': 'ocr'})


@app.route('/ocr/pdf', methods=['POST'])
def ocr_pdf():
    """
    对 PDF 文件进行 OCR 识别

    请求参数:
    - file: PDF 文件
    - language: 语言 (zh/en/ja/ko/auto, 默认 auto)

    返回:
    - text: 识别出的文字
    - pages: 每页的文字
    """
    if 'file' not in request.files:
        return jsonify({'error': 'No file provided'}), 400

    file = request.files['file']
    language = request.form.get('language', 'auto')

    # 获取 Tesseract 语言代码
    lang_code = SUPPORTED_LANGUAGES.get(language, 'chi_sim+eng')

    try:
        # 读取 PDF 文件
        pdf_bytes = file.read()

        # 将 PDF 转换为图片
        logger.info(f"Converting PDF to images, language: {language}")
        images = convert_from_bytes(pdf_bytes, dpi=200)

        # 对每页进行 OCR
        pages = []
        full_text = []

        for i, image in enumerate(images):
            logger.info(f"Processing page {i+1}/{len(images)}")

            # 进行 OCR 识别
            text = pytesseract.image_to_string(image, lang=lang_code)

            pages.append({
                'pageNum': i + 1,
                'text': text.strip()
            })
            full_text.append(text.strip())

        result = {
            'status': 'success',
            'totalPages': len(images),
            'fullText': '\n\n'.join(full_text),
            'pages': pages,
            'language': language,
            'engine': 'tesseract'
        }

        logger.info(f"OCR completed: {len(images)} pages, {sum(len(p['text']) for p in pages)} chars")
        return jsonify(result)

    except Exception as e:
        logger.error(f"OCR failed: {e}")
        return jsonify({'error': str(e)}), 500


@app.route('/ocr/image', methods=['POST'])
def ocr_image():
    """
    对图片进行 OCR 识别

    请求参数:
    - file: 图片文件
    - language: 语言 (zh/en/ja/ko/auto, 默认 auto)

    返回:
    - text: 识别出的文字
    """
    if 'file' not in request.files:
        return jsonify({'error': 'No file provided'}), 400

    file = request.files['file']
    language = request.form.get('language', 'auto')

    # 获取 Tesseract 语言代码
    lang_code = SUPPORTED_LANGUAGES.get(language, 'chi_sim+eng')

    try:
        # 读取图片
        image_bytes = file.read()
        image = Image.open(io.BytesIO(image_bytes))

        # 进行 OCR 识别
        logger.info(f"Processing image, language: {language}")
        text = pytesseract.image_to_string(image, lang=lang_code)

        result = {
            'status': 'success',
            'text': text.strip(),
            'language': language,
            'engine': 'tesseract'
        }

        logger.info(f"OCR completed: {len(text.strip())} chars")
        return jsonify(result)

    except Exception as e:
        logger.error(f"OCR failed: {e}")
        return jsonify({'error': str(e)}), 500


@app.route('/ocr/languages', methods=['GET'])
def get_languages():
    """获取支持的语言列表"""
    return jsonify({
        'languages': [
            {'code': 'zh', 'name': '中文简体'},
            {'code': 'en', 'name': 'English'},
            {'code': 'ja', 'name': '日本語'},
            {'code': 'ko', 'name': '한국어'},
            {'code': 'auto', 'name': '自动检测'}
        ]
    })


if __name__ == '__main__':
    logger.info("Starting OCR service on port 5002")
    app.run(host='0.0.0.0', port=5002, debug=False)
