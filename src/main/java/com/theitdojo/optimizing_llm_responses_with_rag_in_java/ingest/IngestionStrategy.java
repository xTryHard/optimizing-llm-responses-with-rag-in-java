package com.theitdojo.optimizing_llm_responses_with_rag_in_java.ingest;


import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;

import java.util.List;

/**
 * Defines the contract for different ingestion strategies (e.g., CSV, PDF, MD).
 * Each implementation is responsible for parsing a specific file format
 * and converting its content into a list of Document objects.
 */
public interface IngestionStrategy {

    /**
     * Ingests data from the given resource and transforms it into a list of Documents.
     *
     * @param resource The data source to ingest.
     * @return A list of {@link Document} objects.
     * @throws Exception if an error occurs during ingestion.
     */
    List<Document> ingest(Resource resource) throws Exception;

    /**
     * Returns the unique name of the strategy (e.g., "csv", "pdf").
     * This name is used to select the strategy from application properties.
     *
     * @return The strategy name.
     */
    String getStrategyName();
}
