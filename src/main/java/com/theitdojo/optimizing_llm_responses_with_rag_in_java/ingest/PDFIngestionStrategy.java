    package com.theitdojo.optimizing_llm_responses_with_rag_in_java.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * An ingestion strategy for parsing PDF files.
 * It uses Spring AI's PagePdfDocumentReader to create one Document object per page.
 */
@Component
public class PDFIngestionStrategy implements IngestionStrategy {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private static final String STRATEGY_NAME = "pdf";

    @Override
    public List<Document> ingest(Resource resource) {
        log.info("Executing PDF Ingestion Strategy for resource: {}", resource.getFilename());

        PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(resource);
        List<Document> documents = pdfReader.get();

        log.info("Loaded {} pages as documents from PDF file: {}", documents.size(), resource.getFilename());
        return documents;
    }

    @Override
    public String getStrategyName() {
        return STRATEGY_NAME;
    }
}