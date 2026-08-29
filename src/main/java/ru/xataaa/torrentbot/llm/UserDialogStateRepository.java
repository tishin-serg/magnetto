package ru.xataaa.torrentbot.llm;

public interface UserDialogStateRepository {
    UserDialogState find(Long userId);
    void save(UserDialogState state);
}
