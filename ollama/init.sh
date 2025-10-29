#!/bin/sh
set -e

ollama serve &
sleep 3

ollama list | grep -q 'qwen3:1.7b-q4_K_M' || ollama pull qwen3:1.7b-q4_K_M
ollama list | grep -q 'mxbai-embed-large' || ollama pull mxbai-embed-large

wait