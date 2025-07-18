package com.theitdojo.optimizing_llm_responses_with_rag_in_java.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.theitdojo.optimizing_llm_responses_with_rag_in_java.services.ChatAssistant;

@RestController
@RequestMapping("/ai")
public class ChatController {
    Logger logger = LoggerFactory.getLogger(ChatController.class);
    private final ChatAssistant chatAssistant;

    public ChatController(ChatAssistant chatAssistant) {
        this.chatAssistant = chatAssistant;
    }
}
