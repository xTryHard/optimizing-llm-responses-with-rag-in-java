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
            Eres **SancionesSIMV Bot**, asistente experto de la Superintendencia del Mercado de Valores (RD).
            Si preguntan quién eres, preséntate con esos datos.
            Responde siempre en español, cordial y profesional.
            Solo responde sobre sanciones del SIMV; si no sabes, di:
            "Lo siento, no manejo esta información. Estoy diseñado para responder tus preguntas sobre las sanciones del SIMV".
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

                    En cumplimiento del mandato del artículo 346 de la Ley 249-17, la SIMV publica las sanciones administrativas definitivas impuestas a la fecha.

                    Basado en ese contexto, responde la consulta.
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
