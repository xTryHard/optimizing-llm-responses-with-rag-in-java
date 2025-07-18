package com.theitdojo.optimizing_llm_responses_with_rag_in_java.services;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Stream;

@Service
public class ChatAssistantService implements ChatAssistant {
    private final ChatClient chatClient;
    private final String glossaryContext;
    private final PromptTemplate promptTemplate;

    public ChatAssistantService(ChatClient.Builder builder,
                                @Value("classpath:/system-prompt.md") Resource systemPrompt,
                                ChatMemory chatMemory,
                                @Value("classpath:/simv/glosario.txt") Resource glossaryResource,
                                @Value("classpath:/rag-prompt-template.st") Resource ragPromptTemplate) throws IOException {

        this.chatClient = builder
                .defaultSystem(systemPrompt)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();

        this.promptTemplate = new PromptTemplate(ragPromptTemplate);
        this.glossaryContext = glossaryResource.getContentAsString(StandardCharsets.UTF_8);
    }

    @Override
    public String getResponse(String conversationId, String message) {
        return this.chatClient.prompt()
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId))
                .user(message)
                .call()
                .content();
    }

    @Override
    public Stream<String> streamResponse(String conversationId, String message) {
        return chatClient.prompt()
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId))
                .user(message)
                .stream()
                .content()
                .toStream();
    }

    @Override
    public Flux<String> askQuestionWithContext(String conversationId, String question) {
        Prompt prompt = this.promptTemplate.create(Map.of("context", this.glossaryContext, "question", question));

        return chatClient.prompt(prompt)
                .user(question)
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content();
    }
}
