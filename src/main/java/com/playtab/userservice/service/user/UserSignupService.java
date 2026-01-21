package com.playtab.userservice.service.user;

import com.playtab.userservice.dto.user.SignupCommand;
import com.playtab.userservice.entity.AuthConsent;
import com.playtab.userservice.entity.AuthCredential;
import com.playtab.userservice.entity.AuthIdentity;
import com.playtab.userservice.entity.UserProfile;
import com.playtab.userservice.entity.enums.CredentialType;
import com.playtab.userservice.exception.DomainException;
import com.playtab.userservice.exception.ErrorCode;
import com.playtab.userservice.repository.AuthCredentialRepository;
import com.playtab.userservice.repository.AuthIdentityRepository;
import com.playtab.userservice.repository.UserProfileRepository;
import com.playtab.userservice.service.auth.PasswordService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserSignupService {

    private final AuthIdentityRepository identityRepo;
    private final AuthCredentialRepository credentialRepo;
    private final UserProfileRepository profileRepo;
    private final PasswordService passwordService;

    public UserSignupService(
            AuthIdentityRepository identityRepo,
            AuthCredentialRepository credentialRepo,
            UserProfileRepository profileRepo,
            PasswordService passwordService
    ) {
        this.identityRepo = identityRepo;
        this.credentialRepo = credentialRepo;
        this.profileRepo = profileRepo;
        this.passwordService = passwordService;
    }

    @Transactional
    public UUID signUpWithEmail(SignupCommand cmd) {

        // 이메일 중복 방어(최종 방어는 uq_credentials_type_identifier)
        if (credentialRepo.existsByTypeAndIdentifier(CredentialType.EMAIL, cmd.email())) {
            throw new DomainException(ErrorCode.DUPLICATE_EMAIL);
        }

        // 닉네임 중복(스키마 unique 없으면 동시성 완전보장X)
        if (cmd.nickname() != null && !cmd.nickname().isBlank()
                && profileRepo.existsByNickname(cmd.nickname())) {
            throw new DomainException(ErrorCode.DUPLICATE_NICKNAME);
        }

        AuthIdentity identity = new AuthIdentity();
        // identityId는 @PrePersist에서 자동 생성되지만, 즉시 필요하면 여기서 미리 생성해도 됨
        identity.setIdentityId(UUID.randomUUID());

        // Credential
        AuthCredential credential = new AuthCredential();
        credential.setType(CredentialType.EMAIL);
        credential.setIdentifier(cmd.email());
        credential.setPasswordHash(passwordService.hash(cmd.password()));
        credential.setIsPrimary(true);
        identity.addCredential(credential);

        // Profile
        UserProfile profile = new UserProfile();
        profile.setEmail(cmd.email());
        profile.setName(cmd.name());
        profile.setNickname(cmd.nickname());
        profile.setPhoneNumber(cmd.phoneNumber());
        profile.setBirthDate(cmd.birthDate());
        profile.setNationality(
                (cmd.nationality() == null || cmd.nationality().isBlank()) ? "KR" : cmd.nationality()
        );
        identity.attachProfile(profile);

        // Consents
        if (cmd.consents() != null) {
            for (SignupCommand.Consent c : cmd.consents()) {
                AuthConsent consent = new AuthConsent();
                consent.setTermsVersion(c.termsVersion());
                consent.setType(c.type());
                consent.setIsAgreed(c.isAgreed());
                identity.addConsent(consent);
            }
        }

        // ✅ 여기서 한 번만 저장하면 cascade로 전부 저장됨
        AuthIdentity saved = identityRepo.save(identity);
        return saved.getIdentityId();
    }
}
