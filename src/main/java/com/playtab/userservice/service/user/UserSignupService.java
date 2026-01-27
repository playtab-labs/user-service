package com.playtab.userservice.service.user;

import com.playtab.userservice.dto.user.SignupCommand;
import com.playtab.userservice.entity.AuthConsent;
import com.playtab.userservice.entity.AuthCredential;
import com.playtab.userservice.entity.AuthIdentity;
import com.playtab.userservice.entity.UserProfile;
import com.playtab.userservice.entity.enums.CredentialType;
import com.playtab.userservice.entity.enums.Gender;
import com.playtab.userservice.exception.DomainException;
import com.playtab.userservice.exception.ErrorCode;
import com.playtab.userservice.repository.AuthCredentialRepository;
import com.playtab.userservice.repository.AuthIdentityRepository;
import com.playtab.userservice.repository.UserProfileRepository;
import com.playtab.userservice.service.auth.PasswordService;
import com.playtab.userservice.service.user.email.EmailVerificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserSignupService {

    private final AuthIdentityRepository identityRepo;
    private final AuthCredentialRepository credentialRepo;
    private final UserProfileRepository profileRepo;
    private final PasswordService passwordService;
    private final EmailVerificationService emailVerificationService;

    public UserSignupService(
            AuthIdentityRepository identityRepo,
            AuthCredentialRepository credentialRepo,
            UserProfileRepository profileRepo,
            PasswordService passwordService,
            EmailVerificationService emailVerificationService
    ) {
        this.identityRepo = identityRepo;
        this.credentialRepo = credentialRepo;
        this.profileRepo = profileRepo;
        this.passwordService = passwordService;
        this.emailVerificationService = emailVerificationService;
    }

    @Transactional
    public UUID signUpWithEmail(SignupCommand cmd) {

        // 0) 이메일 인증 강제 (Redis verified 확인)
        emailVerificationService.assertVerifiedOrThrow(cmd.email(), cmd.sessionId());

        // 1) 이메일 중복 방어(최종 방어는 uq_credentials_type_identifier)
        if (credentialRepo.existsByTypeAndIdentifier(CredentialType.EMAIL, cmd.email())) {
            throw new DomainException(ErrorCode.DUPLICATE_EMAIL);
        }

        // (nickname 제거했다면 이 블록은 삭제해야 함)
        // if (cmd.nickname() != null && !cmd.nickname().isBlank()
        //         && profileRepo.existsByNickname(cmd.nickname())) {
        //     throw new DomainException(ErrorCode.DUPLICATE_NICKNAME);
        // }

        AuthIdentity identity = new AuthIdentity();
        identity.setIdentityId(UUID.randomUUID());

        // 2) Credential
        AuthCredential credential = new AuthCredential();
        credential.setType(CredentialType.EMAIL);
        credential.setIdentifier(cmd.email());
        credential.setPasswordHash(passwordService.hash(cmd.password()));
        credential.setIsPrimary(true);
        identity.addCredential(credential);

        // 3) Profile
        UserProfile profile = new UserProfile();
        profile.setEmail(cmd.email());
        profile.setName(cmd.name());
        profile.setGender(cmd.gender() != null ? cmd.gender() : Gender.UNSPECIFIED); // gender 추가
        profile.setPhoneNumber(cmd.phoneNumber());
        profile.setBirthDate(cmd.birthDate());
        profile.setNationality(
                (cmd.nationality() == null || cmd.nationality().isBlank()) ? "KR" : cmd.nationality()
        );
        identity.attachProfile(profile);

        // 4) Consents
        if (cmd.consents() != null) {
            for (SignupCommand.Consent c : cmd.consents()) {
                AuthConsent consent = new AuthConsent();
                consent.setTermsVersion(c.termsVersion());
                consent.setType(c.type());
                consent.setIsAgreed(c.isAgreed());
                identity.addConsent(consent);
            }
        }

        // 5) 저장
        AuthIdentity saved = identityRepo.save(identity);

        // 6) 회원가입 성공 시 인증 기록 소비(삭제) - return 전에 실행!
        emailVerificationService.consumeVerified(cmd.email(), cmd.sessionId());

        return saved.getIdentityId();
    }
}