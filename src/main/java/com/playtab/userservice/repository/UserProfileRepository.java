package com.playtab.userservice.repository;

import com.playtab.userservice.entity.UserProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {
    Optional<UserProfile> findByIdentity_IdentityId(UUID identityId);
    Optional<UserProfile> findByEmail(String email);
    Page<UserProfile> findByEmailContainingIgnoreCase(String emailFilter, Pageable pageable);
}
