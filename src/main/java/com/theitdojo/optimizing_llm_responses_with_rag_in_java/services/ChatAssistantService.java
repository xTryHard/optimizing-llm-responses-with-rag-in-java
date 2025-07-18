package com.theitdojo.optimizing_llm_responses_with_rag_in_java.services;

import java.util.stream.Stream;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;

public class ChatAssistantService implements ChatAssistant {
    private final ChatClient chatClient;

    public ChatAssistantService(ChatClient.Builder builder,
            @Value("classpath:/system-prompt.md") Resource systemPrompt) {
        this.chatClient = builder.defaultSystem(systemPrompt).build();
    }

    @Override
    public String getResponse(String message) {
        return this.chatClient.prompt()
                .user(message)
                .call().content();
    }

    @Override
    public Stream<String> streamResponse(String message) {
        return chatClient.prompt()
                .user(message)
                .stream()
                .content()
                .toStream();
    }

    @Override
    public Stream<String> askQuestionWithContext(String question) {
        return this.streamResponse(question);
    }

}
