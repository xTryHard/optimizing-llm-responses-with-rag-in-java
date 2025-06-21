package com.theitdojo.optimizing_llm_responses_with_rag_in_java.views.chat;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@PageTitle("Chat with RAG")
@Route("/")
public class ChatView extends VerticalLayout {

    private final Div chatArea = new Div();
    private final TextField userInput = new TextField();
    private final Checkbox ragToggle = new Checkbox("Use RAG", true);

    public ChatView() {
        // Full viewport with a unified background and centered content
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        getStyle().set("background-color", "#f5f5f5");
        setAlignItems(FlexComponent.Alignment.CENTER);

        // Header with title and toggle
        H3 title = new H3("RAG Chat");
        title.getStyle().set("margin", "0").set("font-size", "var(--lumo-font-size-xl)");

        HorizontalLayout header = new HorizontalLayout(title, ragToggle);
        header.setWidthFull();
        header.setPadding(true);
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.getStyle().set("background-color", "#ffffff");
        header.getStyle().set("box-shadow", "0 2px 4px rgba(0,0,0,0.1)");

        // Chat area: vertical scroll only, responsive width
        chatArea.setHeightFull();
        chatArea.setWidth("80%");
        chatArea.getStyle().set("overflow-y", "auto");
        chatArea.getStyle().set("overflow-x", "hidden");
        chatArea.getStyle().set("padding", "var(--lumo-space-m)");

        // Input bar: responsive width and centered
        userInput.setWidthFull();
        userInput.setPlaceholder("Type a message...");
        Button sendButton = new Button(new Icon(VaadinIcon.PLAY));
        sendButton.addThemeName("primary");
        sendButton.getStyle().set("min-width", "40px");

        HorizontalLayout inputBar = new HorizontalLayout(userInput, sendButton);
        inputBar.setWidth("80%");
        inputBar.getStyle().set("margin", "0 auto");
        inputBar.setPadding(true);
        inputBar.setAlignItems(FlexComponent.Alignment.END);
        inputBar.expand(userInput);
        inputBar.getStyle().set("background-color", "#ffffff");
        inputBar.getStyle().set("box-shadow", "0 -2px 4px rgba(0,0,0,0.1)");

        // Assemble view
        add(header, chatArea, inputBar);
        expand(chatArea);

        // Handlers
        sendButton.addClickListener(e -> sendMessage());
        userInput.addKeyPressListener(event -> {
            if ("Enter".equals(event.getKey().getKeys().getFirst())) {
                sendMessage();
            }
        });
    }

    private void sendMessage() {
        String text = userInput.getValue().trim();
        if (text.isEmpty()) {
            return;
        }
        boolean useRag = ragToggle.getValue();

        // User message
        chatArea.add(createMessageBlock("U", text));
        chatArea.add(new Hr());

        // Bot response (placeholder)
        String response = useRag ? "This is a response using RAG." : "This is a response without RAG.";
        chatArea.add(createMessageBlock("B", response));
        chatArea.add(new Hr());

        userInput.clear();
        chatArea.getElement().executeJs("this.scrollTop = this.scrollHeight");
    }

    private Div createMessageBlock(String senderIcon, String message) {
        // Avatar circle
        Div avatar = new Div();
        avatar.setText(senderIcon);
        avatar.getStyle()
                .set("width", "32px").set("height", "32px")
                .set("border-radius", "50%")
                .set("display", "flex").set("align-items", "center").set("justify-content", "center")
                .set("background-color", senderIcon.equals("U") ? "#007BFF" : "#FFC107")
                .set("color", "#ffffff");

        // Message bubble
        Div bubble = new Div();
        bubble.setText(message);
        bubble.getStyle()
                .set("padding", "var(--lumo-space-s)")
                .set("border-radius", "8px")
                .set("background-color", senderIcon.equals("U") ? "#E1F5FE" : "#FFF8E1")
                .set("max-width", "100%");

        // Container: left-aligned with indent within responsive width
        HorizontalLayout container = new HorizontalLayout(avatar, bubble);
        container.setWidthFull();
        container.setAlignItems(FlexComponent.Alignment.START);
        container.getStyle().set("gap", "8px");
        container.getStyle().set("margin-left", "var(--lumo-space-m)");
        container.setJustifyContentMode(FlexComponent.JustifyContentMode.START);

        return new Div(container);
    }
}
