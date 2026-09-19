package com.bienvenueblainville.notification;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ResidentNotificationMapper {
    /**
     * One row per resident with reminders enabled, in a single statement.
     *
     * <p>{@code INSERT ... SELECT} rather than a loop: the recipient list is a
     * query the database can answer, and reading it into the application only
     * to write it back would turn one round trip into as many as there are
     * residents.
     *
     * @return how many residents it reached
     */
    int fanOutToResidentsWithReminders(
            @Param("noticeId") Long noticeId,
            @Param("eventId") String eventId
    );

    List<ResidentNotification> findForUser(@Param("userId") Long userId, @Param("limit") int limit);

    long countUnread(@Param("userId") Long userId);

    int markRead(@Param("userId") Long userId, @Param("id") Long id);
}
