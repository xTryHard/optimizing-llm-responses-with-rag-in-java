package com.theitdojo.optimizing_llm_responses_with_rag_in_java.ingest;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.theitdojo.optimizing_llm_responses_with_rag_in_java.models.IngestionHistory;
import com.theitdojo.optimizing_llm_responses_with_rag_in_java.repository.IngestionHistoryRepository;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class PdfIngestionRunner implements CommandLineRunner {

    private static final int CHUNK_SIZE = 500;
    private static final int OVERLAP    = 50;

    private static final Pattern SIMV_PATH =
            Pattern.compile(".*/simv/([^/]+)/([^/]+\\.pdf)$");

    @Autowired private VectorStore vectorStore;
    @Autowired private IngestionHistoryRepository historyRepo;
    @Autowired private ApplicationContext applicationContext;

    @Override
    public void run(String... args) throws Exception {
        Resource[] pdfs = applicationContext.getResources("classpath:/simv/**/*.pdf");
        for (Resource pdf : pdfs) {
            String uri = pdf.getURI().toString();
            Matcher m = SIMV_PATH.matcher(uri);
            if (!m.find()) continue;

            String category = m.group(1);
            String filename = m.group(2);
            String sourceId = category + "/" + filename;
            if (historyRepo.existsById(sourceId)) continue;

            System.out.printf("→ Ingesting %s (%s)%n", sourceId, uri);

            // 1) Read pages
            List<Document> pageDocs = new PagePdfDocumentReader(pdf).get();

            // 2) Sliding-window chunking
            List<Document> chunks = new ArrayList<>();
            for (Document pageDoc : pageDocs) {
                String content = pageDoc.getText();
                if (content == null || content.isBlank()) {
                    // no text on this page, skip it
                    continue;
                }
                String[] words = content.split("\\s+");

                int step = CHUNK_SIZE - OVERLAP;
                for (int start = 0; start < words.length; start += step) {
                    int end = Math.min(words.length, start + CHUNK_SIZE);
                    StringBuilder sb = new StringBuilder();
                    for (int i = start; i < end; i++) {
                        sb.append(words[i]).append(" ");
                    }
                    String chunkText = sb.toString().trim();

                    Document chunk = Document.builder()
                            .text(chunkText)
                            .metadata("source_type",     "PDF")
                            .metadata("source_category", category)
                            .metadata("source_id",       sourceId)
                            .metadata("chunk_start",     String.valueOf(start))
                            .metadata("chunk_end",       String.valueOf(end))
                            .build();

                    chunks.add(chunk);
                    if (end == words.length) break;
                }
            }

            // 3) Persist to vector store
            vectorStore.add(chunks);
            historyRepo.save(new IngestionHistory(sourceId));
            System.out.printf("→ Ingested %s (%d chunks)%n", sourceId, chunks.size());
        }
    }
}
