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

Modificaremos nuestro `ChatController` para que inyecte el objeto `HttpSession` de Spring. Usaremos el ID de la sesión (`session.getId()`) como el `conversationId` que pasaremos a nuestro servicio.
```java
    // src/main/java/com/theitdojo/optimizing_llm_responses_with_rag_in_java/controllers/ChatController.java
import jakarta.servlet.http.HttpSession; // ¡Asegúrate de importar HttpSession!
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
    public String chat(HttpSession session, @RequestParam String message, @RequestParam(defaultValue = "false") boolean stream) {
        // Usamos el ID de la sesión como ID de la conversación
        Stream<String> responseStream = chatAssistant.askQuestion(session.getId(), message, stream);

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

Comenzaremos con la implementación más simple: `InMemoryChatMemory`.

1. Actualizar `ChatAssistantService`

Modifica el constructor de `ChatAssistantService` para que también inyecte un bean de `ChatMemory`. Luego, configura el `ChatClient.Builder` para que use esta memoria por defecto.

```java
    // src/main/java/com/theitdojo/optimizing_llm_responses_with_rag_in_java/services/ChatAssistantService.java
    import org.springframework.ai.chat.memory.ChatMemory;
    // ...

    @Service
    public class ChatAssistantService implements ChatAssistant {

       private final ChatClient chatClient;
       private final ChatMemory chatMemory;

       public ChatAssistantService(ChatClient.Builder builder,
                                   @Value("classpath:/system-prompt.md") Resource systemPrompt,
                                   ChatMemory chatMemory) { // Inyectar ChatMemory
          this.chatMemory = chatMemory;
          this.chatClient = builder
                  .defaultSystem(systemPrompt)
                  .build();
       }
       // ... El resto de la clase
    }
    
```
2. Crear el Bean de `ChatMemory`

Por defecto, Spring AI no crea un bean de _ChatMemory_. Vamos a crear una clase de configuración para proveer una instancia de _InMemoryChatMemory_.

Crea un nuevo paquete `config` y dentro una clase `ChatConfig`.

```java
    // src/main/java/com/theitdojo/optimizing_llm_responses_with_rag_in_java/config/ChatConfig.java
    package com.theitdojo.optimizing_llm_responses_with_rag_in_java.config;

    import org.springframework.ai.chat.memory.ChatMemory;
    import org.springframework.ai.chat.memory.InMemoryChatMemory;
    import org.springframework.context.annotation.Bean;
    import org.springframework.context.annotation.Configuration;

    @Configuration
    public class ChatConfig {

        @Bean
        public ChatMemory chatMemory() {
            return new InMemoryChatMemory();
        }
    }
```

3. Usar la Memoria en las Llamadas al LLM

Ahora, actualiza los métodos de `ChatAssistantService` para que utilicen el `conversationId` y la `chatMemory` en cada llamada. 

Esto se hace encadenando `.chatReference(conversationId)` y `.chatMemory(chatMemory)` en la llamada al `prompt`. No olvides actualizar las firmas de los métodos para que coincidan con la interfaz.

```java
    // Dentro de ChatAssistantService.java

    @Override
    public String getResponse(String conversationId, String message) {
        return this.chatClient.prompt()
                .chatReference(conversationId) // Identifica la conversación
                .chatMemory(this.chatMemory)   // Usa la memoria
                .user(message)
                .call()
                .content();
    }

    @Override
    public Stream<String> streamResponse(String conversationId, String message) {
        return chatClient.prompt()
                .chatReference(conversationId) // Identifica la conversación
                .chatMemory(this.chatMemory)   // Usa la memoria
                .user(message)
                .stream()
                .content()
                .toStream();
    }
    
    // ... Asegúrate de actualizar también askQuestion y askQuestionWithContext
```

## Hora de probar la memoria del asistente (In-Memory)

Para apreciar realmente el valor de la persistencia, vamos a realizar pruebas en dos fases.

### Fase 1: Probando la Memoria Volátil (con InMemoryChatMemory)

Antes de cambiar a la memoria persistente, primero vamos a verificar el comportamiento de la implementación en memoria que configuraste en la Parte 2.

1. Asegúrate de que tu `ChatConfig` esté proveyendo el bean de `InMemoryChatMemory`. Si ya lo cambiaste, vuelve a ponerlo temporalmente.

```java
// En ChatConfig.java
@Bean
public ChatMemory chatMemory() {
    return new InMemoryChatMemory();
}
```

2. Ejecuta la aplicación.
3. Abre tu navegador y haz una pregunta para establecer contexto: `http://localhost:8080/ai/chat?message=Mi nombre es Fulano`.
3. Haz una pregunta inicial: `http://localhost:8080/ai/chat?message=Mi nombre es Fulano. ¿Puedes decirme qué es Spring AI`.
4. En la misma pestaña (misma sesión), pregunta: `http://localhost:8080/ai/chat?message=¿Recuerdas mi nombre?`
    - __Resultado esperado__: El asistente responderá correctamente "Fulano", demostrando que la memoria funciona dentro de la sesión de la aplicación.

5. Reinicia tu aplicación Spring Boot.
6. Una vez reiniciada, en la misma pestaña del navegador, vuelve a preguntar: `http://localhost:8080/ai/chat?message=¿Recuerdas mi nombre?`
    - __Resultado esperado__: El asistente NO recordará tu nombre. Esto demuestra la naturaleza volátil de `InMemoryChatMemory`: la memoria se borra cuando la aplicación se detiene.





### Parte 3 - Implementando Memoria Persistente con PostgreSQL y JDBC

La memoria en memoria es útil, pero se pierde al reiniciar. Para una aplicación real, necesitamos persistencia. Ya que tienes PostgreSQL configurado, vamos a utilizarlo para almacenar el historial de chat. Gracias a la autoconfiguración de Spring AI, este cambio es sorprendentemente fácil.

1. Verificar Dependencias

En tu `pom.xml`, asegúrate que se incluya:

- `spring-boot-starter-data-jpa`: Que a su vez incluye `spring-boot-starter-jdbc`, necesario para `JdbcChatMemoryRepository`.
- `postgresql`: El driver JDBC para conectarse a tu base de datos. 

2. Verificar la Configuración de la Base de Datos

En tu `application.properties` asegúrate que se incluya:

```properties
    # src/main/resources/application.properties
    spring.datasource.url=jdbc:postgresql://localhost:5432/optimizing-with-rag-db
    spring.datasource.username=postgres
    spring.datasource.password=postgres
    spring.datasource.driver-class-name=org.postgresql.Driver
    spring.jpa.hibernate.ddl-auto=update
```

3. Aprovechar las bondades de la Autoconfiguración de Spring Boot 

El siguiente paso es idéntico independientemente de la base de datos subyacente. Simplemente necesitamos declarar un bean `ChatMemory` que utilice el `ChatMemoryRepository` que Spring AI autoconfigura para nosotros al detectar un `DataSource`.

Modifica el bean de `ChatMemory` en tu `ChatConfig` para que Spring inyecte el `JdbcChatMemoryRepository` autoconfigurado.

```java
    // src/main/java/com/theitdojo/optimizing_llm_responses_with_rag_in_java/config/ChatConfig.java
    import org.springframework.ai.chat.memory.ChatMemory;
    import org.springframework.ai.chat.memory.JdbcChatMemory; // Importar
    import org.springframework.ai.chat.memory.ChatMemoryRepository; // Importar
    import org.springframework.context.annotation.Bean;
    import org.springframework.context.annotation.Configuration;

    @Configuration
    public class ChatConfig {

        // Spring AI autoconfigura un JdbcChatMemoryRepository porque ve
        // spring-boot-starter-jdbc en el classpath y un DataSource configurado.
        // Nosotros solo necesitamos crear el bean de ChatMemory que lo use.
        @Bean
        public ChatMemory chatMemory(ChatMemoryRepository repository) { // Inyectamos el repositorio autoconfigurado
            return new JdbcChatMemory(repository);
        }
    }
    
```

Con este cambio, tu aplicación ahora persistirá todo el historial de chat en tu base de datos PostgreSQL. Al iniciar, Spring AI creará automáticamente la tabla chat_memory si no existe.

## Hora de probar la memoria del asistente (Persistente)

### Fase 2: Probando la Memoria Persistente (con `JdbcChatMemory`)

Ahora, volvamos a la configuración de la Parte 3 para ver la diferencia.

1. Asegúrate de que tu `ChatConfig` esté proveyendo el bean de `JdbcChatMemory`. inyectando el `ChatMemoryRepository`.

```java
// En ChatConfig.java
@Bean
public ChatMemory chatMemory(ChatMemoryRepository repository) {
    return new JdbcChatMemory(repository);
}
```

2. Ejecuta la aplicación.
3. Abre tu navegador y haz una pregunta para establecer contexto: `http://localhost:8080/ai/chat?message=Mi nombre es Fulano. ¿Puedes decirme qué es Spring AI?`.
4. En la misma pestaña, haz una pregunta de seguimiento que dependa del contexto: `http://localhost:8080/ai/chat?message=¿Podrías darme un ejemplo de código simple sobre eso?`
5. Finalmente, prueba si recuerda tu nombre: `http://localhost:8080/ai/chat?message=¿Recuerdas cómo me llamo?`
    - __Resultado esperado__: El asistente __recordará__ tu nombre.

6. Reinicia tu aplicación Spring Boot.
7. Una vez reiniciada, en la misma pestaña del navegador, vuelve a preguntar: `http://localhost:8080/ai/chat?message=¿Recuerdas mi nombre?`
    - __Resultado esperado__: El asistente __SÍ__ recordará tu nombre. La conversación sobrevivió al reinicio de la aplicación gracias a la persistencia en la base de datos PostgreSQL.

#### Verificando la Persistencia en la Base de Datos

1. Conecta tu cliente de PostgreSQL preferido (como psql, pgAdmin, DBeaver, o el cliente de base de datos de tu IDE) a tu instancia optimizing-with-rag-db.
2. Ejecuta la siguiente consulta SQL:

```sql
SELECT * FROM chat_memory;
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

## Solución

TODO

## Conclusión

En este ejercicio, hemos dado un paso crucial para transformar nuestro asistente de un simple contestador de preguntas a un verdadero compañero de conversación. Hemos superado la limitación inherente de la falta de estado de los LLMs implementando una memoria conversacional.

### Conceptos de LLM que hemos aplicado

-   **Memoria Conversacional (Chat Memory)**: Hemos aprendido que para que una conversación sea coherente, el LLM necesita acceso al historial de la interacción. Este es el propósito fundamental de la memoria de chat.
-   **Contexto Persistente vs. Volátil**: Exploramos dos estrategias de memoria. Primero, una memoria en memoria (`InMemoryChatMemory`), rápida y sencilla pero que se pierde al reiniciar. Luego, una memoria persistente (`JdbcChatMemory`), que guarda la conversación en una base de datos, permitiendo al asistente recordar interacciones incluso después de reinicios.
-   **Gestión de Sesiones**: Comprendimos la necesidad de un identificador de conversación (`conversationId`) para gestionar múltiples diálogos simultáneos con diferentes usuarios, utilizando el ID de sesión HTTP como una solución práctica.

### Cómo nos ayudó Spring AI

-   **Abstracción `ChatMemory`**: Spring AI nos ha facilitado enormemente la tarea con su abstracción `ChatMemory`. En lugar de implementar la lógica de almacenamiento y recuperación desde cero, simplemente utilizamos sus implementaciones listas para usar.
-   **API Fluida e Integrada**: La integración de la memoria en nuestras llamadas al `ChatClient` fue increíblemente sencilla gracias a los métodos `.chatReference()` y `.chatMemory()`, manteniendo nuestro código limpio y legible.
-   **Bondades de la Autoconfiguración**: El cambio de una memoria volátil a una persistente con una base de datos PostgreSQL fue sorprendentemente simple. Gracias a la autoconfiguración de Spring Boot, Spring AI detectó nuestra configuración de base de datos y proveyó automáticamente el `JdbcChatMemoryRepository`, demostrando el poder del ecosistema de Spring para reducir el código repetitivo.

### Próximo ejercicio
    