# 🛠️ Optimizando respuestas de LLMs con Generación Aumentada por Recuperación (RAG) en Java

Los modelos de lenguaje de gran tamaño (LLMs, por sus siglas en inglés de “Large Language Models”) son muy potentes, pero tienen dificultades para manejar información en tiempo real y, con frecuencia, generan respuestas erróneas o “alucinaciones”.
En este taller práctico aprenderás cómo potenciar tu chatbot, basado en un LLM, mediante Generación Aumentada por Recuperación (RAG - Retrieval Augmented Generation) utilizando Java y SpringAI.


# Configura tu entorno local

Aquí están las instrucciones para ejecutar el taller localmente.

## 🧰 Requisitos Técnicos

### 🔧 Hardware (recomendado)
- Memoria RAM: 16 GB
- Almacenamiento disponible: 25 GB libres
- Conectividad: acceso a Internet (Wi-Fi)

### 💻 Software
- [Docker Desktop](https://www.docker.com/products/docker-desktop/) instalado y funcionando con al menos 4GB de memoria asignada.
- Imagen de Docker de [Ollama](https://hub.docker.com/r/ollama/ollama)
- Java Development Kit (JDK) 21 ([Oracle](https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html), [SDKMAN!](https://sdkman.io/jdks/))
- Un IDE de preferencia (Se sugiere [IntelliJ IDEA](https://www.jetbrains.com/idea/))
- [Git](https://git-scm.com/)

### 🌐 Otros
- Cuenta activa en [GitHub](https://github.com/)


## 🚀 Inicialización del Entorno con Docker Compose

Usaremos Docker Compose para levantar todos los servicios necesarios con un solo comando. El archivo docker-compose.yaml en la raíz del proyecto se encargará de todo.

1. Iniciar los servicios: Abre una terminal en la raíz del proyecto (donde se encuentra el archivo docker-compose.yaml) y ejecuta el siguiente comando:

```shell
  docker-compose up -d
```
Este comando creará e iniciará dos contenedores en segundo plano (-d):
- `ollama`: El servidor para los modelos de lenguaje (LLM). La primera vez que se ejecute, descargará automáticamente el modelo `qwen3:1.7b-q4_K_M`. __¡Ten paciencia, este paso puede tardar varios minutos dependiendo de tu conexión a internet!__
- `optimizing-with-rag-db`: La base de datos PostgreSQL con la extensión `pgvector` que usaremos para los ejercicios de RAG.

2. Verificar que los contenedores están corriendo: Puedes verificar que ambos servicios se están ejecutando con el comando:
```shell
  docker-compose ps
```

Deberías ver ambos servicios (ollama y optimizing-with-rag-db) con el estado running o up

## Ejecutar el taller

Estos comandos se pueden ejecutar tantas veces como sea necesario para completar los pasos del taller.

(Opcional) Reiniciar la instancia de Ollama.
```shell
  docker-compose up -d
```

Construir la aplicación
`./mvnw clean install`

Ejecutar la aplicación
`./mvnw spring-boot:run`