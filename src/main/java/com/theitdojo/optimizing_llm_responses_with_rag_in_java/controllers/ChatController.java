package com.theitdojo.optimizing_llm_responses_with_rag_in_java.controllers;

import com.theitdojo.optimizing_llm_responses_with_rag_in_java.services.ChatAssistant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Stream;

@RestController
@RequestMapping("/ai")
public class ChatController {

    Logger logger = LoggerFactory.getLogger(ChatController.class);
    private final ChatAssistant chatAssistant;

    public ChatController(ChatAssistant chatAssistant) {
        this.chatAssistant = chatAssistant;
    }

    @GetMapping("/chat")
    public String chat(@RequestParam String message, @RequestParam(defaultValue = "false") boolean stream) {
        Stream<String> responseStream = chatAssistant.askQuestion(message, stream);

        StringBuilder responseBuilder = new StringBuilder();

        responseStream.forEach(chunk -> {
            logger.info(chunk);
            responseBuilder.append(chunk);
        });

        return responseBuilder.toString();
    }
}
