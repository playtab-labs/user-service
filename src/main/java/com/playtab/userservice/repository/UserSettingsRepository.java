package com.playtab.userservice.repository;

import com.playtab.userservice.entity.UserSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserSettingsRepository extends JpaRepository<UserSettings, UUID> {
    Optional<UserSettings> findByIdentity_IdentityId(UUID identityId);
}
