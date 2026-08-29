package ru.xataaa.torrentbot.llm;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

@Component
public class InMemoryUserDialogStateRepository implements UserDialogStateRepository {

    private final ConcurrentMap<Long, UserDialogState> states = new ConcurrentHashMap<>();

    @Override
    public UserDialogState find(Long userId) {
        return states.getOrDefault(userId, UserDialogState.empty(userId));
    }

    @Override
    public void save(UserDialogState state) {
        if (state == null || state.userId() == null) {
            return;
        }
        states.put(state.userId(), state);
    }
}
