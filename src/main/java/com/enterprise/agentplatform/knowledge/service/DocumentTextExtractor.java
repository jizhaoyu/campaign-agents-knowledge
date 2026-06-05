package com.enterprise.agentplatform.knowledge.service;

import com.enterprise.agentplatform.common.api.ErrorCode;
import com.enterprise.agentplatform.common.exception.BusinessException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentTextExtractor {

    private static final String DOCX_DOCUMENT_ENTRY = "word/document.xml";
    private static final int MAX_DOCX_XML_BYTES = 10 * 1024 * 1024;

    public String extract(MultipartFile file) {
        String lowerName = file.getOriginalFilename() == null
                ? ""
                : file.getOriginalFilename().toLowerCase(java.util.Locale.ROOT);
        try {
            return extractBytes(file.getBytes(), lowerName);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.DOCUMENT_PARSE_FAILED, "文档解析失败");
        }
    }

    public String extract(Path path, String fileName) {
        String lowerName = fileName == null ? "" : fileName.toLowerCase(java.util.Locale.ROOT);
        try {
            return extractBytes(Files.readAllBytes(path), lowerName);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.DOCUMENT_PARSE_FAILED, "文档解析失败");
        }
    }

    private String extractBytes(byte[] bytes, String lowerName) throws IOException {
        if (lowerName.endsWith(".txt") || lowerName.endsWith(".md") || lowerName.endsWith(".markdown")) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        if (lowerName.endsWith(".pdf")) {
            try (PDDocument document = PDDocument.load(bytes)) {
                return new PDFTextStripper().getText(document);
            }
        }
        if (lowerName.endsWith(".docx")) {
            return extractDocx(bytes);
        }
        throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE, "暂不支持该文件类型");
    }

    private String extractDocx(byte[] bytes) throws IOException {
        byte[] documentXml = readDocxDocumentXml(bytes);
        try {
            String text = parseDocxDocumentXml(documentXml).strip();
            if (text.isBlank()) {
                throw new BusinessException(ErrorCode.DOCUMENT_PARSE_FAILED, "DOCX 文档未提取到正文内容");
            }
            return text;
        } catch (XMLStreamException ex) {
            throw new BusinessException(ErrorCode.DOCUMENT_PARSE_FAILED, "DOCX 文档解析失败");
        }
    }

    private byte[] readDocxDocumentXml(byte[] bytes) throws IOException {
        try (ZipInputStream zipInput = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zipInput.getNextEntry()) != null) {
                if (!entry.isDirectory() && DOCX_DOCUMENT_ENTRY.equals(entry.getName())) {
                    return readEntryWithLimit(zipInput);
                }
            }
        }
        throw new BusinessException(ErrorCode.DOCUMENT_PARSE_FAILED, "DOCX 文档缺少正文内容");
    }

    private byte[] readEntryWithLimit(ZipInputStream zipInput) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = zipInput.read(buffer)) != -1) {
            total += read;
            if (total > MAX_DOCX_XML_BYTES) {
                throw new BusinessException(ErrorCode.DOCUMENT_PARSE_FAILED, "DOCX 正文过大，无法解析");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private String parseDocxDocumentXml(byte[] documentXml) throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        setXmlProperty(factory, XMLInputFactory.SUPPORT_DTD, false);
        setXmlProperty(factory, XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setXMLResolver((publicId, systemId, baseUri, namespace) -> new ByteArrayInputStream(new byte[0]));

        StringBuilder text = new StringBuilder();
        boolean insideTextNode = false;
        XMLStreamReader reader = factory.createXMLStreamReader(
                new ByteArrayInputStream(documentXml),
                StandardCharsets.UTF_8.name()
        );
        try {
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String localName = reader.getLocalName();
                    if ("t".equals(localName)) {
                        insideTextNode = true;
                    } else if ("tab".equals(localName)) {
                        text.append('\t');
                    } else if ("br".equals(localName) || "cr".equals(localName)) {
                        appendLineBreak(text);
                    }
                } else if ((event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) && insideTextNode) {
                    text.append(reader.getText());
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    String localName = reader.getLocalName();
                    if ("t".equals(localName)) {
                        insideTextNode = false;
                    } else if ("p".equals(localName)) {
                        appendLineBreak(text);
                    }
                }
            }
        } finally {
            reader.close();
        }
        return text.toString();
    }

    private void appendLineBreak(StringBuilder text) {
        if (text.isEmpty()) {
            return;
        }
        if (text.charAt(text.length() - 1) != '\n') {
            text.append('\n');
        }
    }

    private void setXmlProperty(XMLInputFactory factory, String propertyName, boolean value) {
        if (factory.isPropertySupported(propertyName)) {
            factory.setProperty(propertyName, value);
        }
    }
}
