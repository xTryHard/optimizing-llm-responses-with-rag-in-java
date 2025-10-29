FROM ollama/ollama
ENV OLLAMA_CONTEXT_LENGTH=4096
COPY init.sh /usr/local/bin/init.sh
RUN chmod +x /usr/local/bin/init.sh
ENTRYPOINT ["/usr/local/bin/init.sh"]
