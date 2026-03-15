package com.playtab.userservice.service.user;

import com.playtab.userservice.entity.AuthIdentity;
import com.playtab.userservice.entity.UserSettings;
import com.playtab.userservice.exception.DomainException;
import com.playtab.userservice.exception.ErrorCode;
import com.playtab.userservice.repository.AuthIdentityRepository;
import com.playtab.userservice.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserSettingsService {

    private final UserSettingsRepository settingsRepo;
    private final AuthIdentityRepository identityRepo;

    @Transactional
    public UserSettings getOrCreate(UUID identityId) {
        return settingsRepo.findByIdentity_IdentityId(identityId)
                .orElseGet(() -> {
                    AuthIdentity identity = identityRepo.findById(identityId)
                            .orElseThrow(() -> new DomainException(ErrorCode.INVALID_REQUEST));

                    UserSettings s = new UserSettings();
                    s.setIdentity(identity);
                    // 기본값(locale/push/email)은 엔티티 default/prePersist가 처리
                    return settingsRepo.save(s);
                });
    }

    @Transactional
    public UserSettings patch(UUID identityId, String locale, Boolean pushEnabled, Boolean emailEnabled) {
        UserSettings s = getOrCreate(identityId);

        boolean changed = false;

        if (locale != null && !locale.isBlank()) {
            s.setLocale(locale);
            changed = true;
        }
        if (pushEnabled != null) {
            s.setPushEnabled(pushEnabled);
            changed = true;
        }
        if (emailEnabled != null) {
            s.setEmailNotificationsEnabled(emailEnabled);
            changed = true;
        }

        if (changed) {
            return settingsRepo.save(s);
        }
        return s;
    }
}
