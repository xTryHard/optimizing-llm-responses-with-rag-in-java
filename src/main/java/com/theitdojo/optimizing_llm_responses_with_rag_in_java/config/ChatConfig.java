package com.theitdojo.optimizing_llm_responses_with_rag_in_java.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatConfig {

    /**
     * Spring AI autoconfigura un JdbcChatMemoryRepository porque ve
     * spring-ai-starter-model-chat-memory-repository-jdbc en el classpath y un DataSource configurado.
     * Nosotros solo necesitamos inyectarlo y crear el bean de ChatMemory que lo use.
     */
    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(5) // Limita el historial a los últimos 5 mensajes
                .build();
    }
}
