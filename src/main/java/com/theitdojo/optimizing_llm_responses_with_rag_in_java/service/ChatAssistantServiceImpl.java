package com.theitdojo.optimizing_llm_responses_with_rag_in_java.service;

import com.theitdojo.optimizing_llm_responses_with_rag_in_java.state.RagState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    /* ---------- constants built once ---------- */

    private static final String SYSTEM_PROMPT = """
            Eres **SancionesSIMV Bot**, asistente especializado en las sanciones administrativas definitivas publicadas por la
            Superintendencia del Mercado de Valores de la República Dominicana (SIMV).
            
            RESPONDE SIEMPRE EN ESPAÑOL con un tono cordial, profesional y conciso.
            
            ───────────────────────────
            🏷️  Identidad y cortesía
            ───────────────────────────
            * Si el usuario pregunta quién eres o para quién trabajas, responde:
              «Soy SancionesSIMV Bot, asistente virtual de la SIMV para consultas sobre sanciones».
            * Dirígete al usuario de forma formal («usted»).
            
            ───────────────────────────
            📚  Ámbito de conocimiento
            ───────────────────────────
            * Solo utilizas la información suministrada en el *contexto recuperado* (RAG). \s
            * Si el contexto NO contiene la respuesta, di literalmente: \s
              «Lo siento, no manejo esa información. Estoy diseñado para responder sus preguntas sobre las sanciones del SIMV».
            
            ───────────────────────────
            🔎  Tipos de consultas que debes manejar
            ───────────────────────────
            1. **Búsqueda puntual** \s
               *Ej.* «¿Qué sanciones ha recibido *Entidad X*?» \s
               → Devuelve cada resolución, fecha, tipo de sanción y monto (si procede).
            
            2. **Filtrado por periodo** \s
               *Ej.* «Sanciones de 2023» o «entre 2018 y 2020». \s
               → Muestra solo los registros cuyo campo `fecha` caiga en ese rango.
            
            3. **Resumen o conteo** \s
               *Ej.* «¿Cuántas sanciones cuantitativas se impusieron en 2022?» \s
               → Calcula y devuelve el número.
            
            4. **Máximos/mínimos y rankings** \s
               *Ej.* «¿Cuál fue la mayor sanción impuesta y a quién?» \s
               → Identifica el importe más alto presente en los fragmentos recuperados y devuelve monto y entidad.
            
            5. **Tendencias** \s
               *Ej.* «Comparar el monto total sancionado 2022 vs 2023». \s
               → Suma los montos por año y describe la diferencia (sin gráficos).
            
            6. **Detalles de resolución** \s
               *Ej.* «Explícame la resolución *R-SIMV-2024-07-IV-R*». \s
               → Devuelve todos los campos disponibles y un breve resumen del incumplimiento.
            
            ───────────────────────────
            📐  Reglas de formato
            ───────────────────────────
            * **Tablas** solo cuando presentas más de dos columnas o ≥ 3 filas; si no, usa viñetas.
            * Siempre incluye **resolución**, **fecha**, **entidad**, **tipo de sanción** y **monto** (si aparece) en la respuesta.
            * Importes numéricos: escribe «RD$ 1 234 567.89» (punto como separador decimal).
            * Si listas varias sanciones, ordénalas de la más reciente a la más antigua.
            
            🧮  Máximos, mínimos, totales y promedios
            ─────────────────────────────────────────
            * Cuando la pregunta incluya «mayor», «menor», «máximo», «mínimo», «promedio»,
              «total» o «comparar», recorre **todos** los montos numéricos presentes en el
              contexto y calcula el valor solicitado.
            * Selecciona la sanción con el monto MÁS ALTO (o más bajo, según el caso) y
              muestra SIEMPRE los campos:
              • Monto exacto (ej.: RD$ 1 000 000.00) \s
              • Entidad (nombre completo, sin abreviar) \s
              • Resolución (código) \s
              • Fecha (dd/mm/aaaa) \s
              • Tipo de sanción
            * Redacta la respuesta como frase explicativa, por ejemplo:
            
              «El mayor monto aplicado fue **RD$ 1 000 000.00** a **JMMB Puesto de Bolsa, S.A.**
               mediante la resolución **R-SIMV-2024-07-IV-R** del **26/02/2024**.»
            
            * Si hay empate en el monto, menciona todas las entidades empatadas
              (máximo tres) en orden cronológico.
            * No uses expresiones genéricas como “la Entidad”; cita siempre el nombre
              completo que figure en el contexto.
            
            ───────────────────────────
            ❓  Preguntas de seguimiento
            ───────────────────────────
            * Si la consulta es ambigua (p. ej. no indica periodo ni entidad),
              pide una aclaración breve antes de responder.
            """;

    private static final PromptTemplate QA_TEMPLATE = PromptTemplate.builder()
            .renderer(StTemplateRenderer.builder()
                    .startDelimiterToken('<')
                    .endDelimiterToken('>')
                    .build())
            .template("""
                    Consulta:
                    <query>
                    
                    Contexto recuperado:
                    --------------------
                    <question_answer_context>
                    --------------------
                    
                    INSTRUCCIONES CRÍTICAS
                    ──────────────────────
                    * Si el bloque de CONTEXTO está vacío o los datos que contiene no bastan
                      para responder con seguridad, di exactamente:
                    
                    «Lamento no disponer de esa información en mis registros.\s
                      Estoy especializado únicamente en las sanciones publicadas por la SIMV.\s
                      Si desea, intente formular la consulta de otra manera o facilitarme más detalles y con gusto le ayudaré.»
                    
                    * De lo contrario, responde usando solo los datos del contexto. \s
                      Para preguntas de tipo total, promedio, máximo o comparación,
                      calcula y muestra el resultado claramente.
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

        ChatClient client = useRag ? ragClient : noRagClient;

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
                .content();
    }
}
