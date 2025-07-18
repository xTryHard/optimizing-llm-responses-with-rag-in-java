package com.theitdojo.optimizing_llm_responses_with_rag_in_java.views.chat;

import com.theitdojo.optimizing_llm_responses_with_rag_in_java.services.ChatAssistantService;
import com.vaadin.collaborationengine.UserInfo;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.messages.MessageInput;
import com.vaadin.flow.component.messages.MessageInputI18n;
import com.vaadin.flow.component.messages.MessageList;
import com.vaadin.flow.component.messages.MessageListItem;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.PreserveOnRefresh;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import com.vaadin.flow.theme.lumo.LumoUtility.Display;
import com.vaadin.flow.theme.lumo.LumoUtility.Flex;
import com.vaadin.flow.theme.lumo.LumoUtility.Width;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vaadin.lineawesome.LineAwesomeIconUrl;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@PageTitle("Asistente Financiero Inteligente 🤖 🇩🇴")
@Route("")
@PreserveOnRefresh
@Menu(order = 0, icon = LineAwesomeIconUrl.COMMENTS)
public class ChatView extends HorizontalLayout {

    public static final Logger logger = LoggerFactory.getLogger(ChatView.class);

    private final MessageList messageList;
    private final VerticalLayout chatContainer;

    private final UserInfo humanUserInfo;
    private final String conversationId;

    private static final UserInfo AI_USER_INFO = new UserInfo(
            "ai-assistant-" + UUID.randomUUID().toString(), // Unique ID for AI
            "AI Assistant 🤖",
            "https://png.pngtree.com/png-clipart/20210311/original/pngtree-cute-robot-mascot-logo-png-image_6023574.jpg"
    );

    private final ChatAssistantService chatAssistantService;

    public ChatView(ChatAssistantService chatAssistantService) {
        // El valor fijo en USER_CONVERSATION_ID simula el identificador utilizado en tu sistema para tus usuarios.
        humanUserInfo = new UserInfo("USER_CONVERSATION_ID", "Usuario");
        this.conversationId = humanUserInfo.getId();
        logger.info("ChatView initialized with conversationId: {}", this.conversationId);
        this.chatAssistantService = chatAssistantService;

        addClassNames("chat-view", Width.FULL, Display.FLEX, Flex.AUTO);
        setSpacing(false);


        messageList = new MessageList();
        messageList.setMarkdown(true);
        messageList.setSizeFull();

        MessageInput input = new MessageInput();
        input.setTooltipText("Escribe tu consulta financiera");
        input.setI18n(new MessageInputI18n().setMessage("Mensaje").setSend("Consulta"));
        input.setWidthFull();

        input.addSubmitListener(event -> {
            String userMessageText = event.getValue();
            if (userMessageText == null || userMessageText.isBlank()) {
                return;
            }

            MessageListItem userItem = createMessageListItem(userMessageText, humanUserInfo, false);
            logger.info("Created user message item: '{}'", userItem.getText());

            final UI currentUI = UI.getCurrent();
            if (currentUI == null || !currentUI.isAttached()) {
                logger.error("UI not available or attached when submitting user message.");
                return;
            }

            currentUI.access(() -> {
                if (messageList.isAttached()) {
                    List<MessageListItem> currentDisplayItems = new ArrayList<>(messageList.getItems());
                    currentDisplayItems.add(userItem);
                    messageList.setItems(currentDisplayItems);

                    logger.info("User message added to messageList. Total items now: {}. Last item: '{}'",
                            messageList.getItems().size(),
                            messageList.getItems().isEmpty() ? "N/A" : messageList.getItems().getLast().getText());

                    scrollToBottomChatContainer(currentUI);
                } else {
                    logger.error("MessageList is null or not attached when trying to add user message.");
                }
            });

            respondAsAiAssistant(userMessageText);
        });

        chatContainer = new VerticalLayout();
        chatContainer.addClassNames(Flex.AUTO, LumoUtility.Overflow.AUTO, LumoUtility.Padding.NONE);
        chatContainer.setSpacing(false);
        chatContainer.setHeightFull();

        chatContainer.add(messageList, input);
        chatContainer.expand(messageList);

        add(chatContainer);
        expand(chatContainer);
        setSizeFull();

    }

    private MessageListItem createMessageListItem(String text, UserInfo user, boolean isAssistant) {
        MessageListItem item = new MessageListItem(text, Instant.now(), user.getName());
        if (user.getImage() != null && !user.getImage().isEmpty()) {
            item.setUserImage(user.getImage());
        } else {
            String name = user.getName();
            if (name != null && !name.isEmpty()) {
                String[] parts = name.split("\\s+");
                if (parts.length > 1 && !parts[0].isEmpty() && !parts[1].isEmpty()) {
                    item.setUserAbbreviation(String.valueOf(parts[0].charAt(0)) + parts[1].charAt(0));
                } else {
                    item.setUserAbbreviation(String.valueOf(name.charAt(0)));
                }
            } else {
                item.setUserAbbreviation("?");
            }
        }
        item.setUserColorIndex(isAssistant ? 2 : 1);
        return item;
    }


    private void respondAsAiAssistant(String originalUserMessageText) {
        final UI currentUI = UI.getCurrent();
        if (currentUI == null) {
            logger.error("Cannot respond as AI: UI is not available for chat");
            return;
        }

        MessageListItem aiMessageItem = createMessageListItem("...", AI_USER_INFO, true);
        addMessageToUI(currentUI, aiMessageItem);


        StringBuilder fullResponse = new StringBuilder();

        chatAssistantService.askQuestionWithContext(this.conversationId, originalUserMessageText)
                .doOnNext(chunk -> handleStreamingChunk(currentUI, aiMessageItem, fullResponse, chunk))
                .doOnComplete(() -> handleStreamCompletion(currentUI, aiMessageItem, fullResponse))
                .doOnError(error -> handleStreamError(currentUI, aiMessageItem, error))
                .subscribe();
    }

    private void addMessageToUI(UI currentUI, MessageListItem item) {
        currentUI.access(() -> {
            if (messageList.isAttached()) {
                List<MessageListItem> currentDisplayItems = new ArrayList<>(messageList.getItems());
                currentDisplayItems.add(item);
                messageList.setItems(currentDisplayItems);
                scrollToBottomChatContainer(currentUI);
            } else {
                logger.warn("MessageList not attached when trying to add item: {}", item.getText());
            }
        });
    }

    private void handleStreamingChunk(UI currentUI, MessageListItem aiMessageItem, StringBuilder fullResponse, String chunk) {
        if (currentUI.isAttached()) {
            currentUI.access(() -> {
                if (messageList.isAttached() && aiMessageItem != null) {
                    if (fullResponse.isEmpty() && chunk.isBlank() && aiMessageItem.getText().equals("...")) {
                        return;
                    }
                    fullResponse.append(chunk);
                    aiMessageItem.setText(fullResponse.toString());
                    scrollToBottomChatContainer(currentUI);
                }
            });
        }
    }

    private void handleStreamCompletion(UI currentUI, MessageListItem aiMessageItem, StringBuilder fullResponse) {
        if (currentUI.isAttached()) {
            currentUI.access(() -> {
                if (messageList.isAttached() && aiMessageItem != null) {
                    String finalResponseText = fullResponse.toString().trim();
                    if (finalResponseText.isBlank() && aiMessageItem.getText().equals("...")) {
                        aiMessageItem.setText("(AI no generó respuesta)");
                        logger.info("AI Assistant (streamed) produced empty response for chat");
                    } else if (!finalResponseText.isBlank()) {
                        aiMessageItem.setText(finalResponseText);
                    }
                    scrollToBottomChatContainer(currentUI);
                } else {
                    handleUIDetached("stream completion for ", null);
                }
            });
        } else {
            handleUINotAvailable("stream completion for ", null);
        }
    }

    private void handleStreamError(UI currentUI, MessageListItem aiMessageItem, Throwable error) {
        logger.error("Error streaming AI response for chat {}", error.getMessage(), error);
        if (currentUI.isAttached()) {
            currentUI.access(() -> {
                if (messageList.isAttached()) {
                    List<MessageListItem> currentDisplayItems = new ArrayList<>(messageList.getItems());
                    if (aiMessageItem != null) {
                        currentDisplayItems.remove(aiMessageItem);
                    }

                    MessageListItem errorItem = createMessageListItem(
                            "Lo siento, ocurrió un error al intentar responder: " + error.getMessage(),
                            new UserInfo("system-error", "System Error"), true);
                    errorItem.setUserColorIndex(3);

                    currentDisplayItems.add(errorItem);
                    messageList.setItems(currentDisplayItems);

                    scrollToBottomChatContainer(currentUI);
                } else {
                    handleUIDetached("stream error for ", error);
                }
            });
        } else {
            handleUINotAvailable("stream error for ", error);
        }
    }

    private void scrollToBottomChatContainer(UI ui) {
        if (ui != null && ui.isAttached() && chatContainer != null && chatContainer.isAttached()) {
            ui.access(() -> {
                if (chatContainer.isAttached()) {
                    chatContainer.getElement().executeJs("setTimeout(() => { this.scrollTop = this.scrollHeight; }, 0);");
                }
            });
        }
    }

    private void cleanupLingeringUI(String logContext, Throwable error) {
        String errorMessage = error != null ? error.getMessage() : "N/A";
        logger.warn("UI state issue during {}. Error: {}. No specific UI elements to clean in this version.", logContext, errorMessage);
    }

    private void handleUIDetached(String context, Throwable error) {
        cleanupLingeringUI("UI detached during " + context, error);
    }

    private void handleUINotAvailable(String context, Throwable error) {
        cleanupLingeringUI("UI not available for " + context, error);
    }
}
