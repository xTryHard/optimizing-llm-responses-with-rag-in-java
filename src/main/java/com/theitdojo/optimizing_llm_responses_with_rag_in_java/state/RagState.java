package com.theitdojo.optimizing_llm_responses_with_rag_in_java.state;

import org.springframework.stereotype.Component;
import com.vaadin.flow.spring.annotation.UIScope;

@Component
@UIScope
public class RagState {
    private boolean enabled = true;
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
