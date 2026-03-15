package com.playtab.userservice.grpc.mapper;

import com.playtab.userservice.entity.UserSettings;
import com.playtab.userservice.proto.v1.UserSettingsResponse;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SettingsGrpcMapper {

    public UserSettingsResponse toUserSettingsResponse(
            UUID identityId,
            UserSettings s,
            boolean marketingAgreed,
            String marketingTermsVersion
    ) {

        return UserSettingsResponse.newBuilder()
                .setIdentityId(identityId.toString())
                .setLocale(nvl(s.getLocale()))
                .setPushEnabled(Boolean.TRUE.equals(s.getPushEnabled()))
                .setEmailNotificationsEnabled(Boolean.TRUE.equals(s.getEmailNotificationsEnabled()))
                .setMarketingAgreed(marketingAgreed)
                .setMarketingTermsVersion(nvl(marketingTermsVersion))
                .build();
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }
}
