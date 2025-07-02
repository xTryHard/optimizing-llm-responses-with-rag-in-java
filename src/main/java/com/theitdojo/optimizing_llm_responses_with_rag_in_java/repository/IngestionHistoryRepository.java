package com.theitdojo.optimizing_llm_responses_with_rag_in_java.repository;

import com.theitdojo.optimizing_llm_responses_with_rag_in_java.models.IngestionHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestionHistoryRepository 
    extends JpaRepository<IngestionHistory, String> {}
