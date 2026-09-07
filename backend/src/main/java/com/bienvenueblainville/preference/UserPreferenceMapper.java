package com.bienvenueblainville.preference;

import com.bienvenueblainville.common.LanguageCode;
import com.bienvenueblainville.common.Sector;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalTime;
import java.util.Optional;

@Mapper
public interface UserPreferenceMapper {
    Optional<UserPreference> findByUserId(@Param("userId") Long userId);

    void insertDefault(@Param("userId") Long userId);

    void update(
            @Param("userId") Long userId,
            @Param("sector") Sector sector,
            @Param("languageCode") LanguageCode languageCode,
            @Param("reminderEnabled") boolean reminderEnabled,
            @Param("reminderTime") LocalTime reminderTime
    );
}
