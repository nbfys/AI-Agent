package com.example.aiinterview.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class PdfUtilTest {

    @Test
    public void testExtractTextWithValidPdf() throws Exception {
        // 创建一个简单的 PDF 文件内容（包含文本 "Hello World"）
        // 注意：这是一个非常简化的 PDF 结构，实际 PDF 格式更复杂
        byte[] pdfContent = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R >>\nendobj\n4 0 obj\n<< /Length 44 >>\nstream\nBT\n/F1 12 Tf\n100 700 Td\n(Hello World) Tj\nET\nendstream\nendobj\n5 0 obj\n<< /Type /Font /Subtype /Type1 /Name /F1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>\nendobj\nxref\n0 6\n0000000000 65535 f \n0000000009 00000 n \n0000000052 00000 n \n0000000101 00000 n \n0000000175 00000 n \n0000000256 00000 n \ntrailer\n<< /Size 6 /Root 1 0 R >>\n%%EOF".getBytes();
        
        ByteArrayInputStream inputStream = new ByteArrayInputStream(pdfContent);
        String text = PdfUtil.extractText(inputStream);
        
        assertTrue(text.contains("Hello World"), "PDF text should contain 'Hello World'");
    }

    @Test
    public void testExtractTextWithEmptyPdf() throws Exception {
        // 创建一个空的 PDF 文件内容
        byte[] emptyPdfContent = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n2 0 obj\n<< /Type /Pages /Kids [] /Count 0 >>\nendobj\nxref\n0 3\n0000000000 65535 f \n0000000009 00000 n \n0000000052 00000 n \ntrailer\n<< /Size 3 /Root 1 0 R >>\n%%EOF".getBytes();
        
        ByteArrayInputStream inputStream = new ByteArrayInputStream(emptyPdfContent);
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            PdfUtil.extractText(inputStream);
        });
        
        assertTrue(exception.getMessage().contains("PDF 文本提取结果为空"), "Should throw exception for empty PDF");
    }

    @Test
    public void testExtractTextWithNonPdf() throws Exception {
        // 创建一个非 PDF 文件内容
        byte[] nonPdfContent = "This is not a PDF file".getBytes();
        
        ByteArrayInputStream inputStream = new ByteArrayInputStream(nonPdfContent);
        
        Exception exception = assertThrows(Exception.class, () -> {
            PdfUtil.extractText(inputStream);
        });
        
        assertNotNull(exception, "Should throw exception for non-PDF file");
    }
}
