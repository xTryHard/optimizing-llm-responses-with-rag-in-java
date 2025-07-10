package com.theitdojo.optimizing_llm_responses_with_rag_in_java.service;

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

@Service
public class ChatAssistantServiceImpl implements ChatAssistantService {

    private final ChatClient.Builder builder;
    private final VectorStore vectorStore;
    private static final Logger log = LoggerFactory.getLogger(ChatAssistantServiceImpl.class);

    public ChatAssistantServiceImpl(
            ChatClient.Builder chatClientBuilder,
            VectorStore vectorStore
    ) {
        this.builder     = chatClientBuilder;
        this.vectorStore = vectorStore;
    }

    @Override
    public Flux<String> streamChatResponse(String prompt, String conversationId) {
        // 1) Memory: keep last 3 messages
        var chatMemory = MessageWindowChatMemory.builder()
                .maxMessages(3)
                .build();

        // 2) System prompt
        final String SYSTEM_PROMPT = """
            Eres **SancionesSIMV Bot**, asistente experto de la Superintendencia del Mercado de Valores de la República Dominicana.
            Response presentándote con los datos anteriores si te preguntan quién eres.
            Debes ser cordial y amable, manteniendo el respeto adecuado y la profesionalidad.
            Tu objetivo es ayudar al usuario a adquirir información sobre sanciones aplicadas a entidades del mercado de valores.
            Solo debes responder en base al contexto recuperado de esas sanciones.
            Si te consultan sobre información inadecuada o desconocida, responde literalmente:
            "Lo siento, no manejo esta información. Estoy diseñado para responder tus preguntas sobre las sanciones del SIMV".
            Debes responder **SIEMPRE** en español.
            """;

        // 3) Prompt template for QA advisor
        var qaTemplate = PromptTemplate.builder()
                .renderer(
                        StTemplateRenderer.builder()
                                .startDelimiterToken('<')
                                .endDelimiterToken('>')
                                .build()
                )
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

        // 4) QA Advisor with optional filterExpression
        var qaAdvisor = QuestionAnswerAdvisor.builder(vectorStore)
                .promptTemplate(qaTemplate)
                .searchRequest(SearchRequest.builder().similarityThreshold(0.65f).build())
                .build();

        // 5) Build ChatClient
        var chatClient = builder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        qaAdvisor,
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();

        // 6) Stream the response
        return chatClient
                .prompt()
                .user(prompt)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content();
    }
}
