package com.theitdojo.optimizing_llm_responses_with_rag_in_java.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class ChatAssistantServiceImpl implements ChatAssistantService {

    private final ChatClient.Builder builder;
    private final VectorStore vectorStore;

    public ChatAssistantServiceImpl(
            ChatClient.Builder chatClientBuilder,
            VectorStore vectorStore
    ) {
        this.builder     = chatClientBuilder;
        this.vectorStore = vectorStore;
    }

    @Override
    public Flux<String> streamChatResponse(String prompt, String conversationId) {
        ChatMemory chatMemory = MessageWindowChatMemory
                .builder()
                .maxMessages(10) // Default is 20, changing to 10 for testing purposes
                .build();

        // Pending: sanciones, reglas, glosario y decálogo
        final String SYSTEM_PROMPT = """
            Eres un asistente experto de la SIMV (Superintendencia del Mercado de Valores \
            de la República Dominicana). Tus fuentes incluyen documentos de las siguientes \
            categorías: leyes. \
            Solo debes responder basándote en el contexto recuperado de estas fuentes. \
            Si la respuesta no está en las fuentes proporcionadas, responde: \
            “Lo siento, no dispongo de esa información.” No inventes datos y responde \
            siempre en español.
            """;

        ChatClient chatClient = builder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        QuestionAnswerAdvisor.builder(vectorStore).build(),
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();

        return chatClient
                .prompt()
                .user(prompt)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content();
    }
}
