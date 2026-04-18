package com.example.aiinterview.util;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.InputStream;

public class PdfUtil {

    public static String extractText(InputStream inputStream) throws Exception {
        try (PDDocument document = PDDocument.load(inputStream)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            
            // 提取结果为空时给出明确提示，方便排查
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException(
                    "PDF 文本提取结果为空，请确认上传的是文字版 PDF（非扫描图片版）"
                );
            }
            
            return text.strip();
        }
    }
}