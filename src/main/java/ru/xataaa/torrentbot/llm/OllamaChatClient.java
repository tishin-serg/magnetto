package ru.xataaa.torrentbot.llm;

public interface OllamaChatClient {
    String chat(String systemPrompt, String userPrompt);
}
