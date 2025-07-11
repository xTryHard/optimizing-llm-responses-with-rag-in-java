package com.theitdojo.optimizing_llm_responses_with_rag_in_java.ingest;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
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
import org.springframework.stereotype.Component;

import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

@Component
@ConditionalOnProperty(
        name = "app.ingest.strategy",
        havingValue = "csv"
)
public class CSVIngestionService implements CommandLineRunner {

    private final VectorStore vectorStore;
    private final Logger log = LoggerFactory.getLogger(getClass());

    @Value("classpath:/simv/sanciones.csv")
    private Resource sancionesCsv;

    public CSVIngestionService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public void run(String... args) throws Exception {
        List<Document> rows = new ArrayList<>();

        try (Reader reader = new InputStreamReader(sancionesCsv.getInputStream());
             CSVReader csv = new CSVReaderBuilder(reader).withSkipLines(1).build()) {

            String[] line;
            while ((line = csv.readNext()) != null) {
                // line[0] → combined "RESOLUCIÓN Y FECHA"
                String rawResFecha = line[0];

                // Split into RESOLUCIÓN code and FECHA
                String[] parts = rawResFecha.split("\\s*\\n\\s*", 2);
                String resolucion = parts[0].trim();
                String fecha      = parts.length > 1 ? parts[1].trim() : "";

                // Build the content block with separate fields
                String content = """
                    RESOLUCIÓN: %s
                    FECHA: %s
                    ENTIDAD: %s
                    INCUMPLIMIENTO: %s
                    TIPO DE SANCIÓN: %s
                    """.formatted(
                        resolucion,
                        fecha,
                        line[1],
                        line[2],
                        line[3]
                );

                // Create Document with separate metadata for code and date
                rows.add(
                        Document.builder()
                                .text(content)
                                .metadata("resolucion", resolucion)
                                .metadata("fecha", fecha)
                                .metadata("entidad", line[1])
                                .build()
                );
            }
        }

        log.info("Loaded {} rows from CSV", rows.size());

        // Configure the TokenTextSplitter:
        // - target ~508 tokens per chunk
        // - skip chunks under 50 tokens
        // - only embed chunks ≥100 chars
        // - max 100 chunks per document
        // - keep separators for context
        TextSplitter splitter = new TokenTextSplitter(
                504,    // chunkSize (tokens)
                100,    // minChunkSizeChars
                50,     // minChunkLengthToEmbed (tokens)
                100,    // maxNumChunks
                true    // keepSeparator
        );

        // Split each row into chunks and add to vector store
        List<Document> chunks = splitter.apply(rows);
        vectorStore.add(chunks);

        log.info("Vector store loaded with {} chunks from CSV", chunks.size());
    }
}
