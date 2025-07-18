# Ejercicio 4: Generación Aumentada por Recuperación (RAG) - El Enfoque Manual

En los ejercicios anteriores, construimos un asistente que puede mantener una conversación, pero su conocimiento se limita a lo que aprendió durante su entrenamiento. No puede responder preguntas sobre datos específicos de una empresa, documentos recientes o cualquier información que no sea de dominio público.

Aquí es donde entra en juego la **Generación Aumentada por Recuperación (RAG)**. RAG es una técnica poderosa que permite a los LLMs acceder a información externa y actualizada en el momento de la consulta. En lugar de simplemente responder desde su memoria interna, el sistema primero "recupera" información relevante de una fuente de datos (como documentos, bases de datos, etc.) y luego la "aumenta" al prompt, dándole al LLM el contexto necesario para generar una respuesta precisa.

En este ejercicio, implementaremos una versión muy básica y manual de RAG. No usaremos una base de datos de vectores todavía; en su lugar, cargaremos un documento de texto completo en la memoria y lo inyectaremos directamente en el prompt. Utilizaremos el archivo `src/main/resources/simv/glosario.txt`, que contiene un glosario de términos del mercado de valores.

Este enfoque nos permitirá responder preguntas sobre el glosario, pero también revelará sus importantes limitaciones en cuanto a eficiencia y escalabilidad, preparando el terreno para la solución RAG optimizada que construiremos en el siguiente ejercicio.

## Manos a la obra

### Parte 1 - Actualizar la Interfaz `ChatAssistant`

Primero, vamos a añadir un nuevo método a nuestra interfaz `ChatAssistant` que se encargará específicamente de las preguntas que requieren contexto externo.

Añade la siguiente firma de método a la interfaz `ChatAssistant.java`:

```java
// src/main/java/com/theitdojo/optimizing_llm_responses_with_rag_in_java/services/ChatAssistant.java

public interface ChatAssistant {
    // ... métodos existentes de ejercicios anteriores
    Stream<String> askQuestionWithContext(String conversationId, String question);
}
```
Nota: Dependiendo de tu progreso en los ejercicios anteriores, este método ya podría existir. Asegúrate de que la firma coincida.

### Parte 2 - Crear un `PromptTemplate` para el Contexto

Para inyectar nuestro contexto de manera estructurada, usaremos un `PromptTemplate` de Spring AI. Un `PromptTemplate` nos permite definir una plantilla para nuestros prompts con marcadores de posición que se rellenan dinámicamente.

En nuestra clase `ChatConfig`, crearemos un bean para un `PromptTemplate` que instruya al modelo sobre cómo usar el contexto.

1. Crea el archivo de plantilla: Primero, crea un nuevo archivo llamado `rag-prompt-template.st` en la carpeta `src/main/resources`. El `.st` es por "StringTemplate", el formato que usa Spring AI.

```st
// src/main/resources/rag-prompt-template.st
Usa el siguiente contexto para responder la pregunta al final.
Si la respuesta no puede ser encontrada en el contexto, indica amablemente que no tienes la información para responder esa pregunta.
No añadas ninguna otra información adicional.

Contexto:
{context}

Pregunta:
{question}    
```

2. Crea el Bean del `PromptTemplate`: Ahora, en tu clase `ChatConfig`, crea un bean que cargue esta plantilla.

```java
    // src/main/java/com/theitdojo/optimizing_llm_responses_with_rag_in_java/config/ChatConfig.java
    import org.springframework.ai.chat.prompt.PromptTemplate;
    import org.springframework.beans.factory.annotation.Value;
    import org.springframework.core.io.Resource;
    // ...

    @Configuration
    public class ChatConfig {

        // ... bean de ChatMemory existente

        @Bean
        public PromptTemplate ragPromptTemplate(@Value("classpath:/rag-prompt-template.st") Resource ragPromptTemplate) {
            return new PromptTemplate(ragPromptTemplate);
        }
    }
```

### Parte 3 - Cargar el Contexto y Usar el Template

Ahora, modificaremos `ChatAssistantService` para cargar el contenido del `glosario.txt` y usar nuestro nuevo `PromptTemplate` para responder.

1. __Cargar el archivo de glosario__: En `ChatAssistantService`, inyectaremos el archivo del glosario como un `Resource` y lo leeremos como un `String` en el constructor.
2. __Implementar__ `askQuestionWithContext`: Implementaremos el método que dejamos pendiente. Inyectaremos el `PromptTemplate` que acabamos de crear. Luego, usaremos este template para construir un `Prompt` con el contexto del glosario y la pregunta del usuario.

Actuaiza tu `ChatAssistantService.java` para que se vea así: 

```java
    package com.theitdojo.optimizing_llm_responses_with_rag_in_java.services;

    import org.springframework.ai.chat.client.ChatClient;
    import org.springframework.ai.chat.memory.ChatMemory;
    import org.springframework.ai.chat.prompt.Prompt;
    import org.springframework.ai.chat.prompt.PromptTemplate;
    import org.springframework.beans.factory.annotation.Value;
    import org.springframework.core.io.Resource;
    import org.springframework.stereotype.Service;

    import java.io.IOException;
    import java.nio.charset.StandardCharsets;
    import java.util.Map;
    import java.util.stream.Stream;

    @Service
    public class ChatAssistantService implements ChatAssistant {

        private final ChatClient chatClient;
        private final ChatMemory chatMemory;
        private final PromptTemplate ragPromptTemplate;
        private final String glossaryContext;

        public ChatAssistantService(ChatClient.Builder builder,
                                    @Value("classpath:/system-prompt.md") Resource systemPrompt,
                                    ChatMemory chatMemory,
                                    PromptTemplate ragPromptTemplate, // Inyectar el PromptTemplate
                                    @Value("classpath:/simv/glosario.txt") Resource glossaryResource) throws IOException { // Inyectar el glosario
            this.chatMemory = chatMemory;
            this.ragPromptTemplate = ragPromptTemplate;
            this.chatClient = builder
                    .defaultSystem(systemPrompt)
                    .build();

            // Cargar el contenido del glosario a un String
            this.glossaryContext = glossaryResource.getContentAsString(StandardCharsets.UTF_8);
        }

        // ... getResponse, streamResponse, askQuestion...

        @Override
        public Stream<String> askQuestionWithContext(String conversationId, String question) {
            // 1. Crear un mapa con los valores para el template
            Map<String, Object> model = Map.of(
                    "context", this.glossaryContext,
                    "question", question
            );

            // 2. Crear un Prompt usando el template y el modelo
            Prompt prompt = this.ragPromptTemplate.create(model);

            // 3. Llamar al LLM con el prompt que ya incluye el contexto
            return chatClient.prompt(prompt)
                    .chatReference(conversationId)
                    .chatMemory(this.chatMemory)
                    .stream()
                    .content()
                    .toStream();
        }
    }
    
```

Nota: El constructor ahora lanza una IOException. No te olvides de añadir throws IOException a la firma del constructor.


### Parte 4 - Exponer un Nuevo Endpoint para RAG

Finalmente, vamos a crear un nuevo endpoint en nuestro `ChatController `para poder probar esta funcionalidad.

Añade el siguiente método a `ChatController.java`

```java
// src/main/java/com/theitdojo/optimizing_llm_responses_with_rag_in_java/controllers/ChatController.java
import jakarta.servlet.http.HttpSession;
// ...

@RestController
@RequestMapping("/ai")
public class ChatController {
    // ... código existente del controlador ...
    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

    @GetMapping("/rag")
    public String rag(HttpSession session, @RequestParam String question) {
        Stream<String> responseStream = chatAssistant.askQuestionWithContext(session.getId(), question);

        StringBuilder responseBuilder = new StringBuilder();

        responseStream.forEach(chunk -> {
            logger.info(chunk);
            responseBuilder.append(chunk);
        });

        return responseBuilder.toString();
    }
}
```

## Hora de probar nuestro RAG manual 

1. Ejecuta la aplicación.
2. Abre tu navegador y haz una pregunta cuya respuesta se encuentre en glosario.txt: `http://localhost:8080/ai/rag?question=¿Qué es un emisor?`
   - Deberías obtener una respuesta precisa basada en la definición del archivo.
3. Ahora, haz una pregunta que no tenga que ver con el contexto proporcionado: `http://localhost:8080/ai/rag?question=¿Cuál es la capital de España?`
   - Gracias a nuestras instrucciones en el PromptTemplate, el asistente debería responder algo como: "Lo siento, no tengo la información para responder esa pregunta."


### Análisis: Las (Grandes) Limitaciones de este Enfoque 

1. Uso Ineficiente de Tokens y Alto Costo: Con cada pregunta que hacemos al endpoint `/rag`, estamos enviando el contenido completo del archivo `glosario.txt` al LLM. Nuestro glosario tiene ~4KB, lo que equivale a aproximadamente 1000 tokens. Si usáramos una API de pago como la de OpenAI, estaríamos pagando por esos 1000 tokens de contexto en cada consulta, incluso si la pregunta solo necesita una frase del documento para ser respondida. Esto es extremadamente caro e ineficiente.
2. Límite de la ventana de Contexto: Los LLMs tienen una "ventana de contexto" (context window) finita, es decir, un número máximo de tokens que pueden procesar a la vez (por ejemplo, 4K, 8K, 128K). Nuestro glosario cabe sin problemas, pero ¿qué pasaría si quisiéramos que el asistente respondiera preguntas sobre un manual de 200 páginas o la documentación completa de un proyecto? El texto simplemente no cabría en el prompt, y este enfoque fallaría por completo.
3. Baja Relevancia y Posible "Ruido": Al enviar todo el documento, le estamos dando al modelo mucha información irrelevante o "ruido". Para responder "¿Qué es un emisor?", no necesita saber la definición de "Manipulación de mercado". Este exceso de información no solo es ineficiente, sino que a veces puede confundir al modelo y degradar la calidad de la respuesta.


## Conclusión 

En este ejercicio, hemos implementado una forma rudimentaria de RAG y, lo más importante, hemos aprendido por qué necesitamos una estrategia más inteligente. Hemos demostrado que podemos enseñar a nuestro asistente sobre datos privados, pero también hemos expuesto las debilidades de un enfoque ingenuo.

### Conceptos de LLM que hemos aplicado

- RAG Manual (Context Stuffing): Hemos aplicado el principio básico de RAG: recuperar datos (en este caso, todo el archivo) y aumentarlos en el prompt. Esto prueba que la técnica funciona conceptualmente.
- Importancia de la Relevancia del Contexto: La principal lección de este ejercicio es una de eficiencia. Descubrimos que enviar contexto irrelevante no solo es costoso en términos de tokens y dinero, sino que también puede "contaminar" el prompt y no es escalable debido a los límites de la ventana de contexto de los LLMs.
- Control del Comportamiento (Guardrails): A través de nuestro PromptTemplate, instruimos al modelo sobre cómo actuar cuando la respuesta no se encuentra en el contexto. Esta es una práctica fundamental para construir sistemas de RAG confiables y evitar que el modelo "alucine" o invente respuestas.

### Cómo nos ayudó Spring AI

- `PromptTemplate` para Prompts Dinámicos: Esta característica de Spring AI ha sido la estrella del ejercicio. Nos permitió externalizar y estructurar nuestro prompt de RAG de una manera limpia, separando la lógica de la plantilla. Rellenar los marcadores `{context}` y `{question}` dinámicamente es trivial y hace que el código sea mucho más legible y mantenible.
- API `ChatClient` Consistente: La API fluida del ChatClient nos permitió usar nuestro `Prompt` recién creado con el template relleno de una manera muy natural, demostrando su flexibilidad para manejar tanto prompts simples de usuario como prompts complejos y estructurados.

El problema de la ineficiencia y la escalabilidad nos lleva directamente a la necesidad de una solución más sofisticada. ¿Cómo podemos encontrar y enviar solo los fragmentos de texto más relevantes para la pregunta del usuario, en lugar de todo el documento? La respuesta es utilizando Embeddings y una Base de Datos de Vectores (Vector Store).

### Próximo ejercicio