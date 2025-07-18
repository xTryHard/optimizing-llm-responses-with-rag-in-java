# Ejercicio 5: Generación Aumentada por Recuperación (RAG) – Ingesta Inteligente

En el **Ejercicio 4** implementaste una versión *muy básica* de RAG: cargabas **todo** el documento en memoria y lo pegabas tal cual en el prompt. Eso demostró el concepto, pero también dejó claras sus limitaciones de coste y escalabilidad.

En este ejercicio pasarás a la **forma correcta** de hacer RAG:

1. Convertiremos los documentos (CSV, PDF) en *chunks* de tamaño óptimo.
2. Generaremos **embeddings** de cada chunk y los almacenaremos en un **Vector Store** (pgvector).
3. Recuperaremos, en tiempo de consulta, sólo los chunks más relevantes mediante un **Question‑Answer Advisor** con umbral de similitud.
4. Introduciremos un nuevo *system prompt* especializado para la SIMV.

---

## Manos a la obra

### 1· Requisitos previos

**Dependencias Maven - pom.xml**

```xml
<dependency>
  <groupId>org.springframework.ai</groupId>
  <artifactId>spring-ai-starter-vector-store-pgvector</artifactId>
</dependency>
<dependency>
<groupId>org.springframework.ai</groupId>
<artifactId>spring-ai-pdf-document-reader</artifactId>
</dependency>
<dependency>
  <groupId>com.opencsv</groupId>
  <artifactId>opencsv</artifactId>
  <version>5.11.2</version>
</dependency>
```

**application.properties**

```properties
# Embedding model
spring.ai.model.embedding=ollama
spring.ai.ollama.embedding.options.model=mxbai-embed-large
spring.ai.ollama.embedding.options.num-ctx=512

# RAG / VectorStore
spring.ai.vectorstore.pgvector.initialize-schema=true
spring.ai.vectorstore.pgvector.index-type=NONE

spring.ai.vectorstore.pgvector.dimensions=1024
spring.ai.vectorstore.pgvector.remove-existing-vector-store-table=false

# App properties
# Enable or disable the ingestion process on startup
app.ingestion.enabled=true

# Specify a pattern to find all source files to ingest.
# The strategy will be selected automatically based on the file extension (csv, pdf, etc.).
# This example looks for all files inside the classpath:/simv/ directory.
app.ingestion.source-pattern=classpath:simv/*.*
```

> `dimensions` debe coincidir con las dimensiones del modelo de embeddings (mxbai-embed-large → 1024).

---

### 2· Interfaz `IngestionStrategy`

Antes de ingerir distintos formatos, crea esta **interfaz** en el paquete\
`com.theitdojo.optimizing_llm_responses_with_rag_in_java.ingest`. Su cometido es ofrecer un **contrato común** para todas las estrategias de ingesta (CSV, PDF, Markdown, etc.).

- `ingest(Resource)` → transforma el recurso en una lista de `Document` listos para generar **embeddings**.
- `getStrategyName()` → devuelve un nombre corto (p. ej. `"csv"`, `"pdf"`) con el que el *runner* elegirá la estrategia adecuada.

```java
package com.theitdojo.optimizing_llm_responses_with_rag_in_java.ingest;

import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;
import java.util.List;

public interface IngestionStrategy {

   /**
    * Parsea el recurso y lo convierte en una lista de Document.
    */
   List<Document> ingest(Resource resource) throws Exception;

   /**
    * Nombre único de la estrategia — suele coincidir con la extensión del archivo.
    */
   String getStrategyName();
}
```

## 3 · Estrategias de ingesta

### 3.1 CSVIngestionStrategy — implementación de `IngestionStrategy`

Crea la clase `CSVIngestionStrategy` en el mismo paquete.

```java
@Component
public class CSVIngestionStrategy implements IngestionStrategy {
    private static final Logger log = LoggerFactory.getLogger(CSVIngestionStrategy.class);
    private static final String STRATEGY_NAME = "csv";

    @Override
    public List<Document> ingest(Resource resource) throws Exception {
        log.info("Ingestando CSV: {}", resource.getFilename());
        List<Document> docs = new ArrayList<>();

        try (Reader reader = new InputStreamReader(resource.getInputStream());
             CSVReader csv = new CSVReaderBuilder(reader).withSkipLines(1).build()) {

            String[] line;
            while ((line = csv.readNext()) != null) {
                // Campo 0 → "RESOLUCIÓN
FECHA"
                String[] parts = line[0].split("\s*\n\s*", 2);
                String resolucion = parts[0].trim();
                String fecha      = parts.length > 1 ? parts[1].trim() : "";

                String content = """
RESOLUCIÓN: %s
FECHA: %s
ENTIDAD: %s
INCUMPLIMIENTO: %s
TIPO DE SANCIÓN: %s
""".formatted(resolucion, fecha, line[1], line[2], line[3]);

                docs.add(Document.builder()
                        .text(content)
                        .metadata("resolucion", resolucion)
                        .metadata("fecha", fecha)
                        .metadata("entidad", line[1])
                        .metadata("source", resource.getFilename())
                        .build());
            }
        }
        log.info("→ {} documentos creados desde {}", docs.size(), resource.getFilename());
        return docs;
    }

    @Override public String getStrategyName() { return STRATEGY_NAME; }
}
```
Esta estrategia lee el CSV de sanciones ([sanciones.csv](../src/main/resources/simv/sanciones.csv)) con OpenCSV.
•	Salta la cabecera y recorre cada fila.
•	Separa “RESOLUCIÓN” y “FECHA” (columna 0) usando split("\\s*\\n\\s*", 2).
•	Construye un bloque de texto legible para el LLM y crea un Document por fila.
•	Añade metadatos: resolucion, fecha, entidad y source (nombre del archivo).
Resultado: granularidad fina para búsquedas y filtrado posterior.

### 3.2 PDFIngestionStrategy — implementación de `IngestionStrategy`

Crea la clase `PDFIngestionStrategy`.

```java
@Component
public class PDFIngestionStrategy implements IngestionStrategy {
    private static final Logger log = LoggerFactory.getLogger(PDFIngestionStrategy.class);
    private static final String STRATEGY_NAME = "pdf";

    @Override
    public List<Document> ingest(Resource resource) {
        log.info("Ingestando PDF: {}", resource.getFilename());
        PagePdfDocumentReader reader = new PagePdfDocumentReader(resource);
        List<Document> docs = reader.get();           // 1 Document por página
        log.info("→ {} páginas convertidas a Document", docs.size());
        return docs;
    }

    @Override public String getStrategyName() { return STRATEGY_NAME; }
}
```
Esta estrategia usa PagePdfDocumentReader para convertir cada página del PDF en un Document:
- Ideal para documentos largos como el [Decreto 664-12](../src/main/resources/simv/Decreto-No.-664-12.pdf): no necesitas hacer _chunking_ manualmente.
- El lector preserva saltos de línea y columnas mejor que un extractor plano.
- Registra cuántas páginas se convirtieron para verificar que el PDF no está vacío.

Beneficio: páginas separadas → contexto compacto y relevante durante la búsqueda semántica.

## 4 · `DataIngestionRunner`

Crea la clase `DataIngestionRunner` para ejecutar el flujo ETL al arrancar.

```java
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
```
Este runner se ejecuta al iniciar la aplicación y orquesta todo el flujo ETL:
1.	Localiza los archivos que coinciden con app.ingestion.source-pattern.
2.	Elige automáticamente la estrategia correcta (CSVIngestionStrategy, PDFIngestionStrategy, etc.) según la extensión y llama a ingest(...) para convertir los archivos en Document.
3.	Divide esos documentos en chunks con TokenTextSplitter (tamaño 504 tokens, solapamiento 100) para que encajen bien en la ventana del modelo.
4.	Carga los chunks (embeddings) en el VectorStore (pgvector).

## 5 · Similarity Search con *Question‑Answer Advisor*

### 5. 1 En `ChatAssistantService` inyecta el `VectorStore` y añade el QA advisor.

```java
@Service
public class ChatAssistantService implements ChatAssistant {
   private final ChatClient chatClient;
   private final String glossaryContext;
   private final PromptTemplate promptTemplate;

   public ChatAssistantService(ChatClient.Builder builder,
                               @Value("classpath:/system-prompt.md") Resource systemPrompt,
                               ChatMemory chatMemory,
                               @Value("classpath:/simv/glosario.txt") Resource glossaryResource,
                               @Value("classpath:/rag-prompt-template.st") Resource ragPromptTemplate,
                               VectorStore vectorStore) throws IOException {

      PromptTemplate qaTemplate = PromptTemplate.builder()
              .renderer(StTemplateRenderer.builder().startDelimiterToken('<').endDelimiterToken('>').build())
              .resource(ragPromptTemplate)
              .build();
      
      QuestionAnswerAdvisor qaAdvisor = QuestionAnswerAdvisor.builder(vectorStore)
              .promptTemplate(qaTemplate)
              .searchRequest(SearchRequest.builder().similarityThreshold(0.7f).topK(4).build())
              .build();
      
      this.chatClient = builder
              .defaultSystem(systemPrompt)
              .defaultAdvisors(
                      MessageChatMemoryAdvisor.builder(chatMemory).build(), 
                      qaAdvisor
              )
              .build();

      this.promptTemplate = new PromptTemplate(ragPromptTemplate);
      this.glossaryContext = glossaryResource.getContentAsString(StandardCharsets.UTF_8);
   }
   // Resto de la clase
}
```

> Ajusta `similarityThreshold` y `topK` según la calidad de tus embeddings.

¿Qué hace cada paso?

`VectorStore` – almacena los embeddings; se inyecta para poder ejecutar la búsqueda semántica (similaritySearch).

`PromptTemplate` – se crea a partir de `rag-prompt-template.st`; define dónde insertar la pregunta (`<query>`) y el contexto recuperado (`<question_answer_context>`).

`QuestionAnswerAdvisor` – combina `VectorStore` + `PromptTemplate` + `SearchRequest`.

### 5. 2 En `ChatAssistantService`, actualiza `askQuestionWithContext`.

```java
    @Override
    public Flux<String> askQuestionWithContext(String conversationId, String question) {
        return chatClient.prompt()
                .user(question)
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content();
    }
```

---

## 6 · Actualiza los archivos de prompts existentes (`system-prompt.md` y `rag-prompt-template.st`)

Asegúrate de que los archivos ya existentes en `src/main/resources/` contengan el contenido que se muestra a continuación.

### 6.1 `system-prompt.md`

```md
# SIMV Bot – System Prompt

Eres **SIMV Bot**, asistente virtual de la Superintendencia del Mercado de Valores  
de la República Dominicana (SIMV).

**RESPONDE SIEMPRE EN ESPAÑOL** con un tono cordial, formal y conciso.

---
## ÁMBITO DE CONOCIMIENTO
- Sanciones administrativas definitivas publicadas por la SIMV
- Normativa vigente contenida en el **Decreto No. 664-12**

Solo puedes utilizar la información recuperada mediante RAG.

---
## TIPOS DE CONSULTA QUE MANEJAS
1. **Sanciones puntuales** – «¿Qué sanciones recibió Entidad X?»
2. **Filtro temporal** – «Sanciones 2023» o «entre 2019 y 2021».  
3. **Estadísticas** – cuentas, totales, promedios, máximos/mínimos.  
4. **Tendencias** – comparaciones entre años.  
5. **Detalle de resolución** – «Explícame la R-SIMV-2024-07-IV-R».  
6. **Consulta normativa**  
   - Búsqueda de artículos: «¿Qué dice el Artículo 37?»  
   - Definiciones: «Define “instrumentos derivados” según el Reglamento».  
   - Obligaciones/prohibiciones: «¿Qué ocurre si un emisor envía información falsa?»  
   - Procedimientos: «¿Cómo se designa al representante de la masa de obligacionistas?»

---
## REGLAS DE FORMATO
- **Para sanciones** incluye: resolución, fecha (dd/MM/yyyy), entidad, tipo y monto.  
- **RD$** = peso dominicano (DOP). Escribe montos así: «RD$ 1 234 567.89».  
- **Para normativa** cita siempre el artículo («Art. 45») y, cuando sea útil, el título del capítulo o sección.  
- Usa viñetas ≤ 2 líneas o tablas Markdown ≥ 3 filas / ≥ 2 columnas.  
- Ordena sanciones de la más reciente a la más antigua.  
- Si la pregunta requiere cálculos, opera con los montos presentes en el contexto.  
- Si la pregunta es ambigua, solicita una aclaración breve antes de responder.
```

### 6.2 `rag-prompt-template.st`

```st
PREGUNTA DEL USUARIO:
<query>

CONTEXTO RECUPERADO (RAG)
-------------------------
<question_answer_context>
-------------------------

INSTRUCCIONES CRÍTICAS
1. Si el bloque de CONTEXTO está vacío o es insuficiente, responde EXACTAMENTE:
   «Lo siento, no dispongo de esa información en mis registros.  
    Estoy especializado únicamente en las sanciones y la normativa publicadas por
    la SIMV. Si lo desea, reformule su consulta o facilite más detalles y con gusto le
    ayudaré.»
2. De lo contrario, responde SOLO con los datos del contexto:  
   • Para sanciones: aplica las reglas de formato y, si procede, realiza los
     cálculos solicitados.  
   • Para normativa: cita los artículos pertinentes y resume en lenguaje claro.  
/no_think
```

Estos archivos se cargan automáticamente gracias a las anotaciones `@Value` que viste en `ChatAssistantService`.

---

## 7 · ¡Hora de probar!

1. **Archivos de prueba** – En `src/main/resources/simv` ya existen:

   - `Decreto-No.-664-12.pdf`
   - `sanciones.csv`
   
   Estos serán procesados automáticamente por `PDFIngestionStrategy` y `CSVIngestionStrategy`.


2. **Arranca la aplicación** – Desde la raíz del proyecto ejecuta:

   ```bash
   ./mvnw spring-boot:run
   ```

3. **Revisa los logs** – Deberías ver mensajes indicando la ingesta y el *chunking* (esto puede tardar un par de minutos - ten paciencia):

4. **Haz preguntas para validar el RAG**

   - **Normativa**: «5 puntos clave para una entidad que desea ser participante del mercado de valores de la República Dominicana».
   - **Sanciones**: «¿Qué sanciones ha recibido la entidad Tivalsa, S.A.?».
   - **Caso negativo**: «¿Qué sanciones ha recibido la entidad Super‑Random, S.A.?» (el bot debe indicar que no posee esa información).

---

## Solución

TODO

---

## Conclusión

### Conceptos de LLM que hemos aplicado

- **Embeddings**: representación semántica en alta dimensión.
- **Vector Store + Búsqueda por Similitud**: recupera sólo los fragmentos relevantes.
- **Token chunking**: controla tamaño de contexto y solapamiento.
- **Similarity Threshold**: evita falsos positivos cuando la similitud es baja.
- **Question‑Answer Advisor**: simplifica el patrón *retrieve‑then‑read*.

### Cómo nos ayudó Spring AI

- `VectorStore`: misma API para pgvector, Redis, Pinecone o Chroma.
- `TokenTextSplitter`: utilitario listo para generar chunks sin pasarte del tamaño de la ventana.
- **Autoconfiguración**: basta un *starter* y una `DataSource` para persistir embeddings.
- **Advisors**: añaden memoria de conversación y RAG al `ChatClient` con una sola línea.

### Próximo ejercicio

En el **Ejercicio 6** construirás un cliente **sin RAG**:  enviará las preguntas directamente al modelo, sin embeddings ni `VectorStore`. De esta forma podrás comparar, con las mismas consultas:

- **Precisión** – ¿qué tan bien responde el modelo a datos "privados" o muy específicos?
- **Latencia y coste** – el tiempo de inferencia y tokens consumidos con y sin RAG.
- **Alucinaciones** – cuántas respuestas incorrectas o inventadas aparecen cuando el modelo no cuenta con contexto externo.

Esta comparación te ayudará a entender el verdadero valor de RAG y cuándo merece la pena integrarlo en tus aplicaciones.

