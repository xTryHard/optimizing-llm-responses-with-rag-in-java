package com.theitdojo.optimizing_llm_responses_with_rag_in_java.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class ChatAssistantServiceImpl implements ChatAssistantService {

    private static final Logger log =
            LoggerFactory.getLogger(ChatAssistantServiceImpl.class);

    /* ---------- constants built once ---------- */

    private static final String SYSTEM_PROMPT = """
            Eres **SIMV Bot**, asistente virtual de la Superintendencia del Mercado de Valores
            de la República Dominicana (SIMV).
            
            RESPONDE SIEMPRE EN ESPAÑOL con un tono cordial, formal y conciso.
            
            ───────────────────────────
            ÁMBITO DE CONOCIMIENTO
            ───────────────────────────
            • Sanciones administrativas definitivas publicadas por la SIMV \s
            • Normativa vigente contenida en el **Decreto No. 664-12** (Reglamento de
            Aplicación de la Ley de Mercado de Valores, RLMV)
            
            Solo puedes utilizar la información recuperada mediante RAG.
            
            ───────────────────────────
            TIPOS DE CONSULTA QUE MANEJAS
            ───────────────────────────
            1. **Sanciones puntuales** – «¿Qué sanciones recibió *Entidad X*?» \s
            2. **Filtro temporal** – «Sanciones 2023» o «entre 2019 y 2021». \s
            3. **Estadísticas** – cuentas, totales, promedios, máximos/mínimos. \s
            4. **Tendencias** – comparaciones entre años. \s
            5. **Detalle de resolución** – «Explícame la R-SIMV-2024-07-IV-R». \s
            6. **Consulta normativa** \s
               • Búsqueda de artículos: «¿Qué dice el Artículo 37?» \s
               • Definiciones: «Define “instrumentos derivados” según el Reglamento». \s
               • Obligaciones/prohibiciones: «¿Qué ocurre si un emisor envía información
                 falsa?» \s
               • Procedimientos: «¿Cómo se designa al representante de la masa de
                 obligacionistas?»
            
            ───────────────────────────
            REGLAS DE FORMATO
            ───────────────────────────
            • **Para sanciones** incluye: resolución, fecha (dd/MM/yyyy), entidad,
              tipo y monto. \s
            • **RD$** = peso dominicano (DOP). Escribe montos así: «RD$ 1 234 567.89». \s
            • **Para normativa** cita siempre el artículo («Art. 45») y, cuando sea útil,
              el título del capítulo o sección. \s
            • Usa viñetas ≤ 2 filas o tablas Markdown ≥ 3 filas / ≥ 2 columnas. \s
            • Ordena sanciones de la más reciente a la más antigua. \s
            • Si la pregunta requiere cálculos (total, promedio, etc.) opera con los
              montos presentes en el contexto. \s
            • Si la pregunta es ambigua solicita una aclaración breve antes de responder.
            ""\";
            
            """;

    private static final PromptTemplate QA_TEMPLATE = PromptTemplate.builder()
            .renderer(StTemplateRenderer.builder()
                    .startDelimiterToken('<')
                    .endDelimiterToken('>')
                    .build())
            .template("""
                    PREGUNTA DEL USUARIO:
                    <query>
                    
                    CONTEXTO RECUPERADO (RAG)
                    -------------------------
                    <question_answer_context>
                    -------------------------
                    
                    INSTRUCCIONES CRÍTICAS
                    1. Si el bloque de CONTEXTO está vacío o es insuficiente, responde EXACTAMENTE:
                       «Lo siento, no dispongo de esa información en mis registros. \s
                        Estoy especializado únicamente en las sanciones y la normativa publicadas por
                        la SIMV. Si lo desea, reformule su consulta o facilite más detalles y con gusto le
                        ayudaré.»
                    2. De lo contrario, responde SOLO con los datos del contexto: \s
                       • Para sanciones: aplica las reglas de formato y, si procede, realiza los
                         cálculos solicitados. \s
                       • Para normativa: cita los artículos pertinentes y resume en lenguaje claro. \s
                    /no_think
                    """)
            .build();

    /* ---------- reusable objects ---------- */

    private final ChatClient ragClient;     // LLM + QA advisor
    private final ChatClient noRagClient;   // plain LLM
    private final ConcurrentMap<String, ChatMemory> memories = new ConcurrentHashMap<>();

    /* ---------- constructor ---------- */

    public ChatAssistantServiceImpl(ChatClient.Builder baseBuilder,
                                    VectorStore vectorStore) {

        // Build the QA advisor once
        var qaAdvisor = QuestionAnswerAdvisor.builder(vectorStore)
                .promptTemplate(QA_TEMPLATE)
                .searchRequest(SearchRequest.builder()
                        .similarityThreshold(0.7f)
                        .build())
                .build();

        // Plain client (no RAG, no memory)
        noRagClient = baseBuilder.clone()
                .defaultSystem("/no_think")
                .build();

        // RAG client (QA advisor wired in)
        ragClient = baseBuilder.clone()
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(qaAdvisor)      // RAG context
                .build();
    }

    /* ---------- helper: per-conversation memory ---------- */

    private MessageChatMemoryAdvisor memoryAdvisor(String conversationId) {
        var mem = memories.computeIfAbsent(conversationId,
                id -> MessageWindowChatMemory.builder()
                        .maxMessages(6)
                        .build());
        return MessageChatMemoryAdvisor.builder(mem).build();
    }

    /* ---------- public API ---------- */

    @Override
    public Flux<String> streamChatResponse(String prompt,
                                           String conversationId,
                                           boolean useRag) {

        MDC.put("cid", conversationId);
        log.info("Received prompt ({} chars). RAG={}", prompt.length(), useRag);
        ChatClient client = useRag ? ragClient : noRagClient;

        long t0 = System.nanoTime();
        return client
                .prompt()
                .user(prompt)
                .advisors(a -> {
                    a.param(ChatMemory.CONVERSATION_ID, conversationId);

                    // attach sliding-window memory **only when RAG is ON**
                    if (useRag) {
                        a.advisors(memoryAdvisor(conversationId));
                    }
                })
                .stream()
                .content()
                .doOnComplete(() ->
                        log.info("LLM responded in {} ms",
                                (System.nanoTime() - t0) / 1_000_000))
                .doOnError(ex ->
                        log.error("Error generating response", ex))
                .doFinally(sig -> MDC.clear());
    }
}
