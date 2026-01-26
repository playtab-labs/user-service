package com.playtab.userservice.repository;

import com.playtab.userservice.entity.AuthConsent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthConsentRepository extends JpaRepository<AuthConsent, Long> {
}
