package com.theitdojo.optimizing_llm_responses_with_rag_in_java.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "ingestion_history")
public class IngestionHistory {
  @Id
  private String sourceId;

  @Column(nullable = false, updatable = false)
  private Instant ingestedAt = Instant.now();

  protected IngestionHistory() {}
  public IngestionHistory(String sourceId) { this.sourceId = sourceId; }

  public String getSourceId()   { return sourceId; }
  public Instant getIngestedAt() { return ingestedAt; }
}
