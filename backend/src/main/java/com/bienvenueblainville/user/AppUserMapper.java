package com.bienvenueblainville.user;

import com.bienvenueblainville.common.Role;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

@Mapper
public interface AppUserMapper {
    Optional<AppUser> findByEmail(@Param("email") String email);

    Optional<AppUser> findById(@Param("id") Long id);

    long countByRole(@Param("role") Role role);

    void insert(@Param("email") String email,
                 @Param("passwordHash") String passwordHash,
                 @Param("role") Role role);
}
