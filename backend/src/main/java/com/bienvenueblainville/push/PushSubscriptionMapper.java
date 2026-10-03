package com.bienvenueblainville.push;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PushSubscriptionMapper {
    void upsert(PushSubscription subscription);

    void deleteByEndpoint(@Param("endpoint") String endpoint);

    int deleteByEndpointForUser(@Param("endpoint") String endpoint, @Param("userId") Long userId);

    List<PushSubscription> findForResidentsWithReminders();

    List<PushSubscription> findForUser(@Param("userId") Long userId);
}
