# Ejercicio 1 : Prompting de Cero Disparos (Zero-shot)

Las técnicas de prompting (o ingeniería de prompts) son el arte y la ciencia de crear entradas (prompts) efectivas para guiar a los Modelos de Lenguaje Grandes (LLMs) a generar las respuestas deseadas. La forma en que se formula una pregunta o una instrucción puede cambiar drásticamente la calidad, relevancia y formato de la salida del modelo.

Existen varias técnicas, como:
- __Zero-shot prompting (de cero disparos)__: Pedir al modelo que realice una tarea sin darle ningún ejemplo.
- __Few-shot prompting (de pocos disparos)__: Proporcionar al modelo algunos ejemplos de la tarea para guiar su respuesta.
- __Chain-of-Thought (Cadena de Pensamiento)__: Animar al modelo a que "piense en voz alta" y desglose su razonamiento paso a paso.

En este ejercicio, nos centraremos en el Zero-shot prompting, la técnica más fundamental. Consiste en formular una solicitud directa al LLM, confiando en su conocimiento preexistente y su capacidad de generalización para entender y ejecutar la tarea sin ejemplos previos. Por ejemplo, pedirle que traduzca un texto o resuma un artículo sin mostrarle cómo hacerlo.

Este primer ejercicio tiene como objetivo introducir la interacción con un LLM usando Spring AI, implementando un caso de uso de prompt simple.

## Manos a la obra

Crea una clase que implemente la interfaz `ChatAssistant`, llamada `ChatAssistantService`. 

### Parte 1 - Añadir el objeto ChatClient

Usaremos un objeto `ChatClient` para interactuar con el LLM. Este objeto se puede construir con `ChatClient.Builder`, que ya está instanciado gracias a la autoconfiguración de Spring Boot.

Crea un atributo privado y final de tipo `ChatClient` llamado `chatClient`.
Escribe el constructor de la clase que creaste previamente `ChatAssistantService` con los parámetros `ChatClient.Builder builder` y `@Value("classpath:/system-prompt.md") Resource systemPrompt`.

Asigna `this.chatClient` con el resultado de llamar `builder.defaultSystem(systemPrompt).build()`.

```java
package com.theitdojo.optimizing_llm_responses_with_rag_in_java.services;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
public class ChatAssistantService implements ChatAssistant {

   private final ChatClient chatClient;

   public ChatAssistantService(ChatClient.Builder builder, @Value("classpath:/system-prompt.md") Resource systemPrompt) {
      this.chatClient = builder
              .defaultSystem(systemPrompt)
              .build();
   }
}
```

### Parte 2 - Completar el System Prompt

Actualiza el archivo `system-prompt.md` en la carpeta `src/main/resources` con el siguiente contenido:

```markdown
Responde la pregunta lo más breve posible, en español.
/no_think
```

### Parte 3 - Configurar el model desde `application.properties`

En lugar de configurar las opciones del modelo directamente en el código, usaremos el archivo `application.properties`. Esto nos brinda una configuración más flexible y centralizada, y nos permite cambiar el comportamiento del modelo sin tener que recompilar la aplicación.

Añade las siguientes propiedades al final de tu archivo `src/main/resources/application.properties`:
```properties
spring.ai.model.chat=ollama
spring.ai.ollama.base-url=http://localhost:11434
spring.ai.ollama.chat.options.model=qwen3:1.7b-q4_K_M
spring.ai.ollama.chat.options.temperature=0.1
spring.ai.ollama.chat.options.keep-alive=-1m
spring.ai.ollama.chat.options.seed=43
```

__Explicación de las propiedades__:

- `spring.ai.model.chat=ollama`: Especifica que `ollama` es el proveedor de chat por defecto que Spring AI debe utilizar.
- `spring.ai.ollama.base-url`: Define la URL donde se está ejecutando el servicio de Ollama.
- `spring.ai.ollama.chat.options.model`: Establece el modelo específico de Ollama que se utilizará. En este caso, `qwen3:1.7b-q4_K_M`.
- `spring.ai.ollama.chat.options.temperature`: Controla la aleatoriedad de la respuesta. Un valor bajo como `0.1` hace que la salida sea más determinista.
- `spring.ai.ollama.chat.options.keep-alive`: Controla cuánto tiempo el modelo permanece cargado en memoria después de una solicitud para un acceso más rápido en llamadas posteriores. `-1m` lo mantiene cargado indefinidamente.
- `spring.ai.ollama.chat.options.seed`: Fija una semilla para la generación de texto, lo que asegura que obtendremos la misma respuesta si los demás parámetros no cambian, haciendo los resultados reproducibles.

[Documentación oficial - Ollama Chat Configuration](https://docs.spring.io/spring-ai/reference/api/chat/ollama-chat.html#_auto_configuration)

### Parte 4 - Implementar la consulta al modelo (modo síncrono)

Ahora, implementaremos el método `getResponse` de la interfaz `ChatAssistant`. 

Este método realizará una llamada síncrona al LLM, lo que significa que la aplicación esperará a que el modelo genere la respuesta completa antes de continuar.

Dentro de tu clase `ChatAssistantService`, implementa el método `getResponse` para que coincida con el siguiente código:

```java
@Override
public String getResponse(String message) {
    return this.chatClient.prompt()
            .user(message)
            .call()
            .content();
}
```

### Parte 5 - Implementar la consulta al modelo (modo asíncrono)

Ahora, implementaremos el método `streamResponse` de la interfaz `ChatAssistant`.

Este método realizará una llamada __asíncrona__ al LLM, devolviendo un `Stream` que emite los datos a medida que el modelo los genera. 
Esto permite una experiencia de usuario más interactiva y en tiempo real, sin bloquear la aplicación mientras se espera la respuesta completa.

Dentro de tu clase `ChatAssistantService`, implementa el método `streamResponse` para que coincida con el siguiente código:

```java
@Override
public Stream<String> streamResponse(String message) {
    return chatClient.prompt()
            .user(message)
            .stream()
            .content()
            .toStream();
}
```

### Parte 6 - Implementación Temporal para `askQuestionWithContext`

Este método es la pieza central para implementar RAG (Retrieval-Augmented Generation), lo cual haremos en un ejercicio posterior. Por ahora, para que nuestra clase `ChatAssistantService` compile correctamente, le daremos una implementación temporal.

Esta implementación provisional ignorará el parámetro `context` y simplemente delegará la pregunta al método `streamResponse`. 
Añadiremos también un comentario TODO para recordarnos que debemos volver aquí y desarrollar la lógica completa de RAG.

Dentro de tu clase `ChatAssistantService`, implementa el método `askQuestionWithContext` de la siguiente manera:
```java
@Override
public Stream<String> askQuestionWithContext(String context, String question) {
    // TODO: Implementar la lógica de RAG en un futuro ejercicio.
    // Por ahora, se ignora el contexto y se llama directamente al modelo.
    return this.streamResponse(question);
}
```

### Parte 7 - Exponer un Endpoint de API REST

Para interactuar con nuestro asistente de chat de una manera más estándar, vamos a crear un `RestController` de Spring. Este controlador expondrá un endpoint HTTP que podremos consumir desde un navegador, una herramienta como cURL, o cualquier otro cliente HTTP.

1. Crear el Controlador

    Primero, crea un nuevo paquete llamado `controllers` dentro de tu estructura de paquetes base (`com.theitdojo.optimizing_llm_responses_with_rag_in_java`).
    
    Dentro de este nuevo paquete, crea una clase llamada `ChatController.java`.

2. Implementar el Controlador

    Anota la clase con `@RestController` y `@RequestMapping("/ai")` para definirla como un controlador REST que manejará las peticiones bajo la ruta `/ai`. 

    Inyecta tu servicio `ChatAssistant` a través del constructor.

```java
package com.theitdojo.optimizing_llm_responses_with_rag_in_java.controllers;

import com.theitdojo.optimizing_llm_responses_with_rag_in_java.services.ChatAssistant;
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
}
```

3. Crear el Endpoint de Chat

    Ahora, agrega un método para manejar las peticiones de chat. Este método usará el método `askQuestion` de nuestra interfaz `ChatAssistant`, que convenientemente maneja tanto respuestas síncronas como en streaming.
    
    El endpoint recibirá la pregunta del usuario y un parámetro opcional para habilitar el streaming. Aunque el método `askQuestion` devuelve un `Stream`, nuestro endpoint de API REST recogerá todos los trozos (chunks) y los devolverá como una única respuesta `String`. Sin embargo, registraremos cada chunk en la consola para que puedas observar el comportamiento del streaming en tiempo real cuando esté activado.
    
    Añade el siguiente método a tu `ChatController`:

```java
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
```

## Solución

TODO

## Hora de probar tu primer prompt

Con el controlador listo, ahora puedes probar la interacción con el LLM a través de una simple petición HTTP.

1. Asegúrate de que el contenedor de Ollama esté en ejecución.
2. Ejecuta la aplicación.
3. Abre tu navegador o una herramienta como cURL y accede a la siguiente URL para hacer una pregunta síncrona: `http://localhost:8080/ai/chat?message=¿Cuáles son los 5 características más significativas de Java?`
   - Recibirás la respuesta completa en el navegador. En la consola de tu aplicación, verás la respuesta impresa de una sola vez.
4. Ahora, prueba el modo streaming. Usa esta URL: `http://localhost:8080/ai/chat?message=Escribe un resumen corto sobre la JVM&stream=true`
   - La respuesta en el navegador seguirá apareciendo de golpe (porque los clientes HTTP esperan la respuesta completa). Sin embargo, observa la consola de tu aplicación: verás cómo el resumen aparece palabra por palabra o línea por línea, demostrando que el streaming está funcionando en el backend.

## Conclusión

En este primer ejercicio, hemos sentado las bases para interactuar con un LLM desde una aplicación Java. No solo hemos establecido la comunicación, sino que también hemos puesto en práctica conceptos fundamentales que son cruciales para construir aplicaciones de IA robustas.

### Conceptos de LLM que hemos aplicado

- Prompting de Cero Disparos (Zero-shot): Hemos aprendido a dar instrucciones directas al modelo sin necesidad de ejemplos, confiando en su capacidad de generalización.
- Prompt de Sistema: Configuramos un prompt de sistema para establecer el comportamiento base de nuestro asistente, asegurando que las respuestas sigan un formato y tono consistentes.
- Control de la Generación: A través de parámetros como la temperatura, hemos visto cómo podemos influir en la creatividad y determinismo de las respuestas del modelo.
- Naturaleza sin estado (Stateless): Implícitamente, hemos observado que el LLM no recuerda interacciones pasadas. Cada pregunta es un evento aislado, un concepto clave que abordaremos en el próximo ejercicio sobre memoria conversacional.

### Cómo nos ayudó Spring AI

- __Abstracción y Simplicidad__: Spring AI nos proporcionó la interfaz `ChatClient`, una abstracción de alto nivel que simplifica enormemente la interacción con el LLM. El uso de su API fluida (`.prompt().user().call()`) nos permitió centrarnos en la lógica de negocio en lugar de en los detalles de las llamadas HTTP y el manejo de JSON.
- __Autoconfiguración__: Gracias a la autoconfiguración de Spring Boot, la conexión con Ollama fue trivial. Simplemente añadiendo dependencias y propiedades en `application.properties`, Spring AI se encargó de configurar el cliente por nosotros.

### La facilidad de usar Ollama

- __Entorno de Desarrollo Local__: Ollama ha demostrado ser una herramienta invaluable para el desarrollo local. Nos permitió descargar y ejecutar un modelo de lenguaje potente en nuestra propia máquina con un solo comando, sin necesidad de gestionar APIs en la nube ni claves de acceso.
- __Integración Sencilla__: Al exponer una API estándar, Ollama se integra sin problemas con Spring AI, convirtiéndose en la opción perfecta para prototipar y desarrollar nuestras aplicaciones de IA.

### Próximo ejercicio
