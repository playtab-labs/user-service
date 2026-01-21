package com.playtab.userservice.repository;

import com.playtab.userservice.entity.AuthIdentity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuthIdentityRepository extends JpaRepository<AuthIdentity, UUID> {
}
