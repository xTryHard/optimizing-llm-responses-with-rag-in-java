# Ejercicio 2: Memoria Conversacional (Chat Memory)

En el ejercicio anterior, observamos que los LLMs son inherentemente "sin estado" (stateless). Cada interacción es un evento aislado; el modelo no tiene memoria de las preguntas o respuestas anteriores. Para construir un chatbot o asistente virtual verdaderamente útil, necesitamos que recuerde el contexto de la conversación.

Aquí es donde entra en juego la __Memoria de Chat (Chat Memory)__. Es el mecanismo que nos permite almacenar y recuperar el historial de una conversación, proporcionando al LLM el contexto necesario para entender preguntas de seguimiento, referencias a temas anteriores y mantener una interacción coherente con el usuario.

Spring AI ofrece una abstracción `ChatMemory` con varias implementaciones listas para usar [Ver Documentación completa](https://docs.spring.io/spring-ai/reference/api/chat-memory.html):

En este ejercicio, implementaremos dos de las estrategias disponibles:

1. `InMemoryChatMemory`:  Almacena el historial en la memoria de la aplicación. Es simple y rápida, ideal para desarrollo y pruebas, pero volátil (los datos se pierden al reiniciar la aplicación).
2. `JdbcChatMemory`: Persiste el historial en una base de datos relacional a través de JDBC, proporcionando una memoria duradera entre sesiones de la aplicación. En nuestro caso, usaremos nuestra base de datos PostgreSQL existente.

## Manos a la obra

### Parte 1 - Refactorización para Soportar Conversaciones

Para que el asistente pueda recordar múltiples conversaciones con diferentes usuarios simultáneamente, necesitamos una forma de identificar cada conversación. Usaremos el ID de la sesión HTTP como un identificador de conversación único.

1. __Actualizar la interfaz__ `ChatAssistant` 
    </br>
    Modifica todos los métodos de la interfaz `ChatAssistant` para que acepten un `String conversationId` como primer parámetro. Esto nos permitirá asociar cada solicitud a una conversación específica.

```java
    // src/main/java/com/theitdojo/optimizing_llm_responses_with_rag_in_java/services/ChatAssistant.java
    public interface ChatAssistant {
        String getResponse(String conversationId, String message);
        Stream<String> streamResponse(String conversationId, String message);
        Stream<String> askQuestion(String conversationId, String message, boolean stream);
        Stream<String> askQuestionWithContext(String conversationId, String question);
    }
```

2 __Actualizar el Controlador__ `ChatController`

Vamos a colocar un valor fijo como identificador de la conversación, esto simula la estrategia de identificación utilizada en tu propio sistema.

```java
// src/main/java/com/theitdojo/optimizing_llm_responses_with_rag_in_java/controllers/ChatController.java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
// ... otras importaciones

@RestController
@RequestMapping("/ai")
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);
    private final ChatAssistant chatAssistant;

    public ChatController(ChatAssistant chatAssistant) {
        this.chatAssistant = chatAssistant;
    }

    @GetMapping("/chat")
    public String chat(@RequestParam String message, @RequestParam(defaultValue = "false") boolean stream) {
        // El valor fijo en CONVERSATION_ID simula el identificador utilizado en tu sistema para tus usuarios.
        Stream<String> responseStream = chatAssistant.askQuestion("CONVERSATION_ID", message, stream);

        StringBuilder responseBuilder = new StringBuilder();

        responseStream.forEach(chunk -> {
            logger.info(chunk);
            responseBuilder.append(chunk);
        });

        return responseBuilder.toString();
    }
}
```

### Parte 2 - Implementando ChatMemory en Memoria (In-Memory)

Registraremos un `MessageChatMemoryAdvisor`, primero agregamos las siguientes importaciones:

```java
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
```

1. Actualizar `ChatAssistantService`

Modifica el constructor de `ChatAssistantService` para que también inyecte un bean de `ChatMemory`. Luego, agregamos un advisor predeterminado desde `MessageChatMemoryAdvisor.builder()`

```java
    // src/main/java/com/theitdojo/optimizing_llm_responses_with_rag_in_java/services/ChatAssistantService.java

import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
// ...

@Service
public class ChatAssistantService implements ChatAssistant {

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;

    public ChatAssistantService(ChatClient.Builder builder,
                                @Value("classpath:/system-prompt.md") Resource systemPrompt,
                                ChatMemory chatMemory) {
        this.chatMemory = chatMemory;
        this.chatClient = builder
                .defaultSystem(systemPrompt)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }
    // ... El resto de la clase
}

```
2. Especificar el ID de la Conversación para el Advisor

Aunque ya registramos un `advisor` de memoria en el constructor, este no sabe mágicamente a qué conversación pertenece cada nueva solicitud. Por ello, en cada método que interactúa con el `ChatClient`, debemos pasar explícitamente el `conversationId`. Esto le indica al `MessageChatMemoryAdvisor` qué historial de conversación debe cargar y actualizar para esta interacción en particular.

Ahora, actualicemos los métodos de ChatAssistantService para pasar este identificador:

```java
    // Dentro de ChatAssistantService.java

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
                .user(message)
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content()
                .toStream();
    }
    

    @Override
    public Stream<String> askQuestionWithContext(String conversationId, String question) {
        // TODO: Implementar la lógica de RAG en un futuro ejercicio.
        return chatClient.prompt()
                .user(question)
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content()
                .toStream();
    }
```

## Probando la Memoria Volátil (con MessageChatMemoryAdvisor)

Antes de cambiar a la memoria persistente, primero vamos a verificar el comportamiento de la implementación en memoria que configuraste en la Parte 2.

1. Ejecuta la aplicación.
2. Abre tu navegador y haz una pregunta inicial: `http://localhost:8080/ai/chat?message=Mi nombre es Fulano. ¿Puedes decirme qué es Spring AI?`.
3. En la misma pestaña (misma sesión), pregunta: `http://localhost:8080/ai/chat?message=¿Cuál es mi nombre?, `
    - __Resultado esperado__: El asistente responderá correctamente "Fulano", demostrando que la memoria funciona dentro de la sesión de la aplicación.

4. Reinicia tu aplicación Spring Boot.
5. Una vez reiniciada, en la misma pestaña del navegador, vuelve a preguntar: `http://localhost:8080/ai/chat?message=¿Cuál es mi nombre?`
    - __Resultado esperado__: El asistente NO recordará tu nombre. Esto demuestra la naturaleza volátil de `InMemoryChatMemory`: la memoria se borra cuando la aplicación se detiene.



### Parte 3 - Implementando Memoria Persistente con PostgreSQL y JDBC

Almacenar información en memoria es útil, pero se pierde al reiniciar. Para una aplicación real, necesitamos persistencia. `JdbcChatMemoryRepository` es una implementación que utiliza JDBC para guardar los mensajes en una base de datos relacional, ideal para aplicaciones que requieren que el historial de chat sobreviva a los reinicios.

1. Agregar dependencia `spring-ai-starter-model-chat-memory-repository-jdbc` 

En tu `pom.xml` agrega la siguiente dependencia

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-chat-memory-repository-jdbc</artifactId>
</dependency>
```

2. Verificar Dependencias

En tu `pom.xml`, asegúrate de que las siguientes dependencias estén incluidas. La nueva dependencia clave aquí es  `spring-ai-starter-model-chat-memory-repository-jdbc`, que nos proporciona la autoconfiguración para la memoria de chat con JDBC.
```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-chat-memory-repository-jdbc</artifactId>
</dependency>
        
<dependency>
<groupId>org.springframework.boot</groupId>
<artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<dependency>
<groupId>org.postgresql</groupId>
<artifactId>postgresql</artifactId>
<scope>runtime</scope>
</dependency>
```
- `spring-ai-starter-model-chat-memory-repository-jdbc`: Proporciona la autoconfiguración para `JdbcChatMemoryRepository`
- `spring-boot-starter-data-jpa`: Que a su vez incluye `spring-boot-starter-jdbc`, necesario para `JdbcChatMemoryRepository`.
- `postgresql`: El driver JDBC para conectarse a tu base de datos. 

3. Verificar la Configuración de la Base de Datos

En tu `application.properties` asegúrate que se incluya:

```properties
    # src/main/resources/application.properties
    spring.datasource.url=jdbc:postgresql://localhost:5432/optimizing-with-rag-db
    spring.datasource.username=postgres
    spring.datasource.password=postgres
    spring.datasource.driver-class-name=org.postgresql.Driver
    spring.jpa.hibernate.ddl-auto=update
    spring.ai.chat.memory.repository.jdbc.initialize-schema=always
```
La propiedad `spring.ai.chat.memory.repository.jdbc.initialize-schema=always` le indica a Spring AI que cree la tabla spring_ai_chat_memory si no existe al arrancar.

4. Configurar un Bean de `MessageWindowChatMemory`

Ahora, en lugar de una memoria simple, crearemos una `ChatMemory` más avanzada que solo considera las últimas interacciones. Esto se conoce como "memoria de ventana" (window memory) y es crucial para evitar enviar un historial de conversación demasiado largo al LLM, lo que consumiría muchos tokens y aumentaría los costos.

Spring AI autoconfigura un bean `JdbcChatMemoryRepository` porque detecta el starter de JDBC y una `DataSource` activa. Simplemente lo inyectamos y lo usamos para construir nuestra `ChatMemory`.

Crea un nuevo paquete `config` y, dentro, una clase `ChatConfig`.
```java
// src/main/java/com/theitdojo/optimizing_llm_responses_with_rag_in_java/config/ChatConfig.java
package com.theitdojo.optimizing_llm_responses_with_rag_in_java.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatConfig {

    /**
     * Spring AI autoconfigura un JdbcChatMemoryRepository porque ve
     * spring-ai-starter-model-chat-memory-repository-jdbc en el classpath y un DataSource configurado.
     * Nosotros solo necesitamos inyectarlo y crear el bean de ChatMemory que lo use.
     */
    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(10) // Limita el historial a los últimos 10 mensajes
                .build();
    }
}
```

Con este cambio, tu aplicación ahora persistirá el historial de chat en PostgreSQL, pero solo enviará el contexto más reciente al LLM en cada solicitud, manteniendo las conversaciones eficientes y relevantes.

## Hora de probar la memoria del asistente (Persistente)

Ahora, volvamos a la configuración de la Parte 3 para ver la diferencia.

1. Ejecuta la aplicación.
2. Abre tu navegador y haz una pregunta para establecer contexto: `http://localhost:8080/ai/chat?message=Mi nombre es Fulano. ¿Puedes decirme qué es Spring AI?`.
3. En la misma pestaña, haz una pregunta de seguimiento que dependa del contexto: `http://localhost:8080/ai/chat?message=¿Podrías darme un ejemplo de código simple sobre eso?`
4. Finalmente, prueba si recuerda tu nombre: `http://localhost:8080/ai/chat?message=¿Recuerdas cómo me llamo?`
    - __Resultado esperado__: El asistente __recordará__ tu nombre.

5. Reinicia tu aplicación Spring Boot.
6. Una vez reiniciada, en la misma pestaña del navegador, vuelve a preguntar: `http://localhost:8080/ai/chat?message=¿Recuerdas mi nombre?`
    - __Resultado esperado__: El asistente __SÍ__ recordará tu nombre. La conversación sobrevivió al reinicio de la aplicación gracias a la persistencia en la base de datos PostgreSQL.

#### Verificando la Persistencia en la Base de Datos

1. Conecta tu cliente de PostgreSQL preferido (como psql, pgAdmin, DBeaver, o el cliente de base de datos de tu IDE) a tu instancia optimizing-with-rag-db.
2. Ejecuta la siguiente consulta SQL:

```sql
SELECT * FROM spring_ai_chat_memory;
```
Verás las filas que representan el historial de la conversación que acabas de tener.

### Parte 4 - Mejorando el Prompt del Sistema para la Conversación

Un buen prompt de sistema es aún más importante en una conversación. Vamos a darle a nuestro asistente una personalidad más definida.

Actualiza `src/main/resources/system-prompt.md`:

```markdown
Eres un asistente de IA amigable y servicial llamado "JConfDominicana Assistant".
Tu propósito es ayudar a los usuarios con sus preguntas sobre programación, tecnología y desarrollo de software.
Responde de manera clara y concisa. Mantén un tono profesional pero cercano.
Habla siempre en español.
/no_think
```

Luego de actualizar el `system-prompt.md` si deseas puedes volver a ejecutar los pasos anteriores en la sección __Hora de probar la memoria del asistente (Persistente)__.
Esto no es estrictamente necesario, vamos a poder probar esto y otras funcionalidades en los siguientes ejercicios.


## Solución

[Ir a la siguiente rama de git - jconfdominicana2025-exercise-2-solution](https://github.com/xTryHard/optimizing-llm-responses-with-rag-in-java/tree/jconfdominicana2025-exercise-2-solution)

## Conclusión

En este ejercicio, hemos dado un paso crucial para transformar nuestro asistente de un simple contestador de preguntas a un verdadero compañero de conversación. Hemos superado la limitación inherente de la falta de estado de los LLMs implementando una memoria conversacional.

### Conceptos de LLM que hemos aplicado

-   **Memoria Conversacional (Chat Memory)**: Hemos aprendido que para que una conversación sea coherente, el LLM necesita acceso al historial de la interacción. Este es el propósito fundamental de la memoria de chat.
-   **Contexto Persistente vs. Volátil**: Exploramos dos estrategias de memoria. Primero, una memoria en memoria (`InMemoryChatMemory`), rápida y sencilla pero que se pierde al reiniciar. Luego, una memoria persistente (`JdbcChatMemory`), que guarda la conversación en una base de datos, permitiendo al asistente recordar interacciones incluso después de reinicios.
-   **Gestión de Sesiones**: Comprendimos la necesidad de un identificador de conversación (`conversationId`) para gestionar múltiples diálogos simultáneos con diferentes usuarios, utilizando el ID de sesión HTTP como una solución práctica.

### Cómo nos ayudó Spring AI

-   **Abstracción `ChatMemory`**: La integración de la memoria en nuestras llamadas al `ChatClient` fue increíblemente sencilla. Al configurar un `MessageChatMemoryAdvisor` por defecto y luego especificar el `conversationId` en cada llamada con el método `.advisors()`, mantuvimos nuestro código limpio y legible.
-   **API Fluida e Integrada**: La integración de la memoria en nuestras llamadas al `ChatClient` fue increíblemente sencilla gracias a los métodos `.chatReference()` y `.chatMemory()`, manteniendo nuestro código limpio y legible.
-   **Bondades de la Autoconfiguración**: El cambio de una memoria volátil a una persistente con una base de datos PostgreSQL fue sorprendentemente simple. Gracias a la autoconfiguración de Spring Boot, Spring AI detectó nuestra configuración de base de datos y proveyó automáticamente el `JdbcChatMemoryRepository`, demostrando el poder del ecosistema de Spring para reducir el código repetitivo.

### Próximo ejercicio

[Ejercicio 3: Interfaz de Usuario (UI) Reactiva con Vaadin](./exercise-3.md)