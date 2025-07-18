# Ejercicio 3: Interfaz de Usuario (UI) Reactiva con Vaadin

Hasta ahora, hemos interactuado con nuestro asistente a través de cURL, lo cual no es muy amigable. Para crear una experiencia de chatbot real, necesitamos una interfaz de usuario (UI).

Además, una buena UI de chat muestra las respuestas del LLM a medida que se generan, no todo de una vez. Para lograr esto de manera eficiente, necesitamos cambiar de un `java.util.stream.Stream` a un stream reactivo. En este ejercicio, usaremos el framework **Vaadin** para la UI, que se integra perfectamente con Spring Boot. Para alimentar esta UI reactiva, adaptaremos nuestro backend para que use **Project Reactor** y su tipo `Flux`, ideal para flujos de datos asíncronos.

En este ejercicio, integraremos una interfaz de chat pre-construida con Vaadin y adaptaremos nuestro backend para que se comunique con ella de forma reactiva.

## Manos a la obra

### Parte 1 - Integrar la Interfaz de Usuario mediante Fusión de Rama (Branch Merge)

Desarrollar una interfaz de usuario desde cero está fuera del alcance de este taller. Por ello, te proporcionaremos una ya lista. La forma más sencilla de integrarla en tu proyecto es fusionando (haciendo "merge") una rama de Git que ya contiene todo lo necesario.

1.  **Fusionar la rama de la UI**

    Abre una terminal en la raíz de tu proyecto y ejecuta los siguientes comandos para traer y fusionar la rama `jconfdominicana2025-exercise-3-ui`:

    ```shell
    # Trae las últimas actualizaciones del repositorio remoto (incluyendo la nueva rama)
    git fetch

    # Fusiona la rama que contiene la UI en tu rama actual
    git merge jconfdominicana2025-exercise-3-ui --no-edit
    ```

2.  **¿Qué acabas de añadir a tu proyecto?**

    Esta fusión de rama ha introducido una interfaz de usuario moderna construida con Vaadin. Los archivos clave son:
    *   `src/main/java/com/theitdojo/optimizing_llm_responses_with_rag_in_java/views/chat/ChatView.java`: Esta es la vista principal de nuestra UI. Utiliza componentes de Vaadin como `MessageList` y `MessageInput` para crear la interfaz de chat. Es la responsable de capturar la entrada del usuario, llamar a nuestro servicio de backend y mostrar la respuesta del asistente en tiempo real a medida que llega.
    *   `src/main/java/com/theitdojo/optimizing_llm_responses_with_rag_in_java/views/MainLayout.java`: Esta clase define la estructura o "shell" principal de la aplicación, dentro de la cual se mostrará nuestra `ChatView`.

    A diferencia del `ChatController` que usamos para pruebas, esta es una UI web completa y reactiva servida directamente por Spring Boot gracias a la integración con Vaadin.

### Parte 2 - Adaptar el Servicio para Streaming Reactivo

La nueva `ChatView` de Vaadin está diseñada para consumir un `Flux<String>` de nuestro servicio y actualizar la UI a medida que llegan los datos. Actualmente, nuestros métodos devuelven un `Stream<String>`. Necesitamos actualizar nuestra capa de servicio para que "hable" el mismo lenguaje reactivo.

1.  **Actualizar la Interfaz `ChatAssistant`**

    El método `askQuestionWithContext` es el que usará nuestra nueva UI. Modifícalo en la interfaz `ChatAssistant` para que devuelva un `Flux<String>` en lugar de un `Stream<String>`.

    Asegúrate de añadir la importación necesaria: `import reactor.core.publisher.Flux;`.

    ```java
    // en /src/main/java/com/theitdojo/optimizing_llm_responses_with_rag_in_java/services/ChatAssistant.java

    import reactor.core.publisher.Flux;
    import java.util.stream.Stream;

    public interface ChatAssistant {
        // ... otros métodos de la interfaz

        Flux<String> askQuestionWithContext(String conversationId, String question);

        // ... otros métodos y el método default
    }
    ```

2.  **Actualizar la Implementación `ChatAssistantService`**

    Ahora, implementa el método modificado en `ChatAssistantService`. El `ChatClient` de Spring AI hace que este cambio sea muy sencillo. En lugar de llamar a `.toStream()` al final de la cadena de llamadas, simplemente usamos el `Flux<String>` que el método `.content()` nos devuelve de forma natural cuando se combina con `.stream()`.

    La implementación debe seguir usando tanto el `conversationId` como la `chatMemory` que configuramos en el ejercicio anterior para mantener el contexto de la conversación.

    ```java
    // en /src/main/java/com/theitdojo/optimizing_llm_responses_with_rag_in_java/services/ChatAssistantService.java
    import reactor.core.publisher.Flux;
    // ... otras importaciones

    @Override
    public Flux<String> askQuestionWithContext(String conversationId, String question) {
        // TODO: Implementar la lógica de RAG en un futuro ejercicio.
        // Cambiamos a Flux para soportar el streaming reactivo hacia la UI de Vaadin.
        return this.chatClient.prompt()
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId))
                .user(question)
                .stream()
                .content();
    }
    ```
    *Nota: La `ChatView` que obtuviste del merge ya está preparada para llamar a este método y manejar el `Flux` resultante.*

## Hora de probar la nueva UI

Con el backend y el frontend listos y conectados, es hora de ver nuestro chatbot en acción.

1.  Ejecuta la aplicación Spring Boot como siempre.
    ```shell
    ./mvnw spring-boot:run
    ```
2.  Abre tu navegador y ve a `http://localhost:8080`.

3.  Deberías ver una interfaz de chat profesional. ¡Pruébala!

    *   **Prueba una pregunta simple**: Escribe "Escribe un poema corto sobre Java" y presiona Enter. Verás cómo la respuesta aparece en la pantalla palabra por palabra.
    *   **Prueba la memoria conversacional**:
        1.  Primero, di: `Hola, mi nombre es JConf`.
        2.  Luego, pregunta: `¿Recuerdas cómo me llamo?`.

    Gracias a la memoria persistente que configuramos en el ejercicio anterior, el asistente debería recordar tu nombre correctamente, demostrando que toda nuestra pila tecnológica (UI, servicio reactivo, memoria persistente y LLM) está funcionando en armonía.

## Solución

[Ir a la siguiente rama de git - jconfdominicana2025-exercise-3-solution](https://github.com/xTryHard/optimizing-llm-responses-with-rag-in-java/tree/jconfdominicana2025-exercise-3-solution
)

## Conclusión

En este ejercicio hemos dado un salto cualitativo, pasando de una simple API a una aplicación web interactiva y moderna con Vaadin. Hemos construido un puente reactivo entre el backend y el frontend.

### Conceptos que hemos aplicado

-   **Streaming Reactivo**: Hemos aprendido la diferencia entre un `Stream` de Java 8 y un `Flux` de Project Reactor, entendiendo por qué este último es ideal para la comunicación asíncrona con una UI moderna.
-   **UI Push**: Aunque no lo implementamos directamente, hemos aprovechado la capacidad de Vaadin para "empujar" actualizaciones desde el servidor al navegador, lo que nos permite mostrar la respuesta del LLM en tiempo real.
-   **Integración Frontend-Backend con Vaadin**: Hemos visto cómo una UI de Vaadin, escrita completamente en Java, puede invocar directamente servicios de Spring (`@Service`) y consumir tipos de datos reactivos como `Flux`, creando una arquitectura fuertemente tipada y cohesiva.

### Cómo nos ayudó Spring

-   **Spring Boot y Vaadin**: La autoconfiguración de Spring Boot hace que servir una aplicación Vaadin sea trivial. Simplemente añadiendo la dependencia, Spring se encarga del resto.
-   **Integración Reactiva en Spring AI**: Una vez más, Spring AI demostró su flexibilidad. Cambiar de un `Stream` a un `Flux` fue trivial gracias a la API fluida y bien diseñada de `ChatClient`, que soporta nativamente ambos paradigmas.

### Próximo ejercicio

Hemos construido una aplicación de chat conversacional y reactiva. Ahora, ¿cómo hacemos para que responda preguntas sobre nuestros propios datos? En el próximo ejercicio, finalmente abordaremos la "R" de RAG (Retrieval-Augmented Generation), conectando nuestro asistente a una base de conocimientos para que pueda ofrecer respuestas fundamentadas y precisas.
