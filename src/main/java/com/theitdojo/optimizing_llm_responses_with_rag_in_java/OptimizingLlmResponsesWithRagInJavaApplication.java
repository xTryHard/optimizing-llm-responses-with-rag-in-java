package com.theitdojo.optimizing_llm_responses_with_rag_in_java;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Push
@SpringBootApplication
public class OptimizingLlmResponsesWithRagInJavaApplication implements AppShellConfigurator {

	public static void main(String[] args) {
		SpringApplication.run(OptimizingLlmResponsesWithRagInJavaApplication.class, args);
	}

}
