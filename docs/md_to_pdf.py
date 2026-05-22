import os
import re
import sys

import markdown
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import cm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, PageBreak, Table
from reportlab.lib.enums import TA_LEFT, TA_CENTER


def register_chinese_font():
    try:
        font_path = r"C:\Windows\Fonts\msyh.ttc"
        pdfmetrics.registerFont(TTFont('Chinese', font_path))
        return 'Chinese'
    except Exception as e:
        print(f"注册中文字体失败: {e}, 使用默认字体")
        return 'Helvetica'


def convert_md_to_pdf(md_file_path, pdf_file_path=None):
    if not os.path.exists(md_file_path):
        print(f"错误: 文件不存在: {md_file_path}")
        return False

    if pdf_file_path is None:
        pdf_file_path = os.path.splitext(md_file_path)[0] + ".pdf"

    with open(md_file_path, 'r', encoding='utf-8') as f:
        md_content = f.read()

    font_name = register_chinese_font()

    doc = SimpleDocTemplate(
        pdf_file_path,
        pagesize=A4,
        leftMargin=2*cm,
        rightMargin=2*cm,
        topMargin=2*cm,
        bottomMargin=2*cm
    )

    styles = getSampleStyleSheet()
    story = []

    title_style = ParagraphStyle(
        'Title',
        parent=styles['Title'],
        fontName=font_name,
        fontSize=18,
        spaceAfter=12,
        alignment=TA_CENTER
    )

    h1_style = ParagraphStyle(
        'Heading1',
        parent=styles['Heading1'],
        fontName=font_name,
        fontSize=16,
        spaceBefore=12,
        spaceAfter=6
    )

    h2_style = ParagraphStyle(
        'Heading2',
        parent=styles['Heading2'],
        fontName=font_name,
        fontSize=14,
        spaceBefore=10,
        spaceAfter=4
    )

    h3_style = ParagraphStyle(
        'Heading3',
        parent=styles['Heading3'],
        fontName=font_name,
        fontSize=12,
        spaceBefore=8,
        spaceAfter=4
    )

    body_style = ParagraphStyle(
        'Body',
        parent=styles['BodyText'],
        fontName=font_name,
        fontSize=10,
        spaceBefore=3,
        spaceAfter=3,
        leading=14
    )

    code_style = ParagraphStyle(
        'Code',
        parent=styles['Code'],
        fontName='Courier',
        fontSize=8,
        spaceBefore=3,
        spaceAfter=3,
        leftIndent=10,
        backgroundColor='#f5f5f5'
    )

    md = markdown.Markdown(extensions=['extra', 'tables', 'fenced_code'])
    html = md.convert(md_content)

    lines = html.split('\n')
    in_code_block = False
    code_lines = []

    for line in lines:
        line = line.strip()

        if line.startswith('```'):
            if in_code_block:
                code_text = '<br/>'.join(code_lines)
                story.append(Paragraph(f'<font face="Courier" size="8">{code_text}</font>', code_style))
                code_lines = []
                in_code_block = False
            else:
                in_code_block = True
            continue

        if in_code_block:
            code_lines.append(line.replace('<', '&lt;').replace('>', '&gt;'))
            continue

        if line.startswith('<h1'):
            text = extract_text(line)
            story.append(Paragraph(text, title_style))
        elif line.startswith('<h2'):
            text = extract_text(line)
            story.append(Paragraph(text, h1_style))
        elif line.startswith('<h3'):
            text = extract_text(line)
            story.append(Paragraph(text, h2_style))
        elif line.startswith('<h4') or line.startswith('<h5') or line.startswith('<h6'):
            text = extract_text(line)
            story.append(Paragraph(text, h3_style))
        elif line.startswith('<p>'):
            text = extract_text(line)
            text = process_inline_formatting(text)
            story.append(Paragraph(text, body_style))
        elif line.startswith('<li>'):
            text = extract_text(line)
            text = process_inline_formatting(text)
            story.append(Paragraph(f"• {text}", body_style))
        elif line.startswith('<blockquote>'):
            text = extract_text(line)
            text = process_inline_formatting(text)
            quote_style = ParagraphStyle('Quote', parent=body_style, leftIndent=10, borderPadding=5)
            story.append(Paragraph(text, quote_style))
        elif line.startswith('<table>'):
            pass
        elif line.startswith('</table>'):
            pass
        elif line.startswith('<tr>'):
            pass
        elif line.startswith('<th>') or line.startswith('<td>'):
            text = extract_text(line)
            story.append(Paragraph(text, body_style))
        elif line.startswith('<hr>'):
            story.append(Spacer(1, 0.3*cm))
        elif line == '':
            story.append(Spacer(1, 0.2*cm))
        elif line.startswith('<'):
            text = strip_html(line)
            if text:
                text = process_inline_formatting(text)
                story.append(Paragraph(text, body_style))
        else:
            text = strip_html(line)
            if text:
                text = process_inline_formatting(text)
                story.append(Paragraph(text, body_style))

    doc.build(story)
    print(f"转换成功: {pdf_file_path}")
    return True


def extract_text(line):
    match = re.search(r'>([^<]+)<', line)
    if match:
        return match.group(1).strip()
    return ""


def strip_html(line):
    return re.sub(r'<[^>]+>', '', line).strip()


def process_inline_formatting(text):
    text = text.replace('&nbsp;', ' ')
    text = text.replace('&gt;', '>')
    text = text.replace('&lt;', '<')
    text = text.replace('&amp;', '&')
    text = re.sub(r'\*\*(.+?)\*\*', r'<b>\1</b>', text)
    text = re.sub(r'\*(.+?)\*', r'<i>\1</i>', text)
    text = re.sub(r'`(.+?)`', r'<font face="Courier" size="9">\1</font>', text)
    return text


if __name__ == "__main__":
    md_file = r"d:\develop\vs-code\JavaAgent-CLI-mcq\docs\pseudocode.md"
    convert_md_to_pdf(md_file)
