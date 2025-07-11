package com.theitdojo.optimizing_llm_responses_with_rag_in_java.service;

import com.theitdojo.optimizing_llm_responses_with_rag_in_java.state.RagState;
import reactor.core.publisher.Flux;


public interface ChatAssistantService {
    /**
     * Streams a chat response from the AI assistant.
     *
     * @param prompt User prompt
     * @return A Flux emitting chunks of the AI's response.
     */
    Flux<String> streamChatResponse(String prompt, String conversationId, boolean useRag);
}
