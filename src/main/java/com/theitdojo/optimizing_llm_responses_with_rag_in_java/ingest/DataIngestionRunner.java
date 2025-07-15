package com.theitdojo.optimizing_llm_responses_with_rag_in_java.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "app.ingestion.enabled", havingValue = "true")
public class DataIngestionRunner implements CommandLineRunner {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final VectorStore vectorStore;
    private final Map<String, IngestionStrategy> strategies;
    private final ResourcePatternResolver resourcePatternResolver;

    @Value("${app.ingestion.source-pattern}")
    private String sourcePattern;

    public DataIngestionRunner(VectorStore vectorStore, List<IngestionStrategy> strategyList, ResourcePatternResolver resourcePatternResolver) {
        this.vectorStore = vectorStore;
        this.strategies = strategyList.stream()
                .collect(Collectors.toMap(IngestionStrategy::getStrategyName, Function.identity()));
        this.resourcePatternResolver = resourcePatternResolver;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("Starting data ingestion process for source pattern: {}", sourcePattern);

        Resource[] resources = findResources();
        if (resources.length == 0) {
            log.warn("No resources found for pattern '{}'. Skipping ingestion.", sourcePattern);
            return;
        }

        // 1. Ingest documents from all found resources using the appropriate strategy
        List<Document> allDocuments = ingestAll(resources);

        if (allDocuments.isEmpty()) {
            log.info("No documents were ingested. Aborting process.");
            return;
        }

        // 2. Split: Common logic for splitting documents into chunks
        log.info("Splitting a total of {} documents into chunks...", allDocuments.size());
        TextSplitter splitter = new TokenTextSplitter(504, 100, 50, 100, true);
        List<Document> chunks = splitter.apply(allDocuments);
        log.info("Created {} chunks.", chunks.size());

        // 3. Load: Common logic for adding chunks to the vector store
        log.info("Adding {} chunks to the vector store...", chunks.size());
        vectorStore.add(chunks);
        log.info("Vector store loaded successfully.");
    }

    private Resource[] findResources() throws IOException {
        return resourcePatternResolver.getResources(sourcePattern);
    }

    private List<Document> ingestAll(Resource[] resources) {
        List<Document> allDocuments = new ArrayList<>();
        for (Resource resource : resources) {
            String filename = resource.getFilename();
            if (filename == null) {
                log.warn("Skipping resource with no filename: {}", resource);
                continue;
            }

            // Determine strategy from file extension
            String fileExtension = getFileExtension(filename);
            IngestionStrategy strategy = strategies.get(fileExtension);

            if (strategy == null) {
                log.warn("No ingestion strategy found for file extension '{}' (from file {}). Skipping.", fileExtension, filename);
                continue;
            }

            try {
                List<Document> documentsFromFile = strategy.ingest(resource);
                allDocuments.addAll(documentsFromFile);
            } catch (Exception e) {
                log.error("Failed to ingest data from resource: {}", filename, e);
            }
        }
        return allDocuments;
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }
}