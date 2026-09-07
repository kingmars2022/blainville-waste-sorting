package com.bienvenueblainville.preference;

import com.bienvenueblainville.preference.dto.UserPreferenceRequest;
import org.springframework.stereotype.Service;

@Service
public class UserPreferenceService {
    private final UserPreferenceMapper mapper;

    public UserPreferenceService(UserPreferenceMapper mapper) {
        this.mapper = mapper;
    }

    public UserPreference getForUser(Long userId) {
        return mapper.findByUserId(userId)
                .orElseGet(() -> {
                    mapper.insertDefault(userId);
                    return mapper.findByUserId(userId).orElseThrow();
                });
    }

    public UserPreference update(Long userId, UserPreferenceRequest request) {
        mapper.update(userId, request.sector(), request.languageCode(), request.reminderEnabled(), request.reminderTime());
        return mapper.findByUserId(userId).orElseThrow();
    }
}
