package com.theitdojo.optimizing_llm_responses_with_rag_in_java.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class ChatAssistantServiceImpl implements ChatAssistantService {

    private final ChatClient.Builder builder;

    public ChatAssistantServiceImpl(ChatClient.Builder chatClientBuilder) {
        this.builder = chatClientBuilder;
    }

    @Override
    public Flux<String> streamChatResponse(String prompt, String conversationId) {
        ChatMemory chatMemory = MessageWindowChatMemory
                .builder()
                .maxMessages(10) // Default is 20, changing to 10 for testing purposes
                .build();

        final String SYSTEM_PROMPT = """
                Take a deep breath and work on this step by step.
                You are a friendly chatbot that answers question about the Dominican Republic finance system, stock markets, banking, economy.
                Act as an economist in your answers.
                Answer in spanish always!"
                /nothink)
                """;

        ChatClient chatClient = builder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultSystem(SYSTEM_PROMPT)
                .build();

        return chatClient.
                prompt()
                .user(prompt)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content();
    }

}