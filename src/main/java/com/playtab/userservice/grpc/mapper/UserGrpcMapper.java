package com.playtab.userservice.grpc.mapper;

import com.google.protobuf.Timestamp;
import com.playtab.userservice.dto.user.ProfilePatch;
import com.playtab.userservice.dto.user.SignupCommand;
import com.playtab.userservice.entity.UserProfile;
import com.playtab.userservice.entity.enums.ConsentType;
import com.playtab.userservice.proto.v1.*;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Component
public class UserGrpcMapper {

    public SignupCommand toSignupCommand(SignUpWithEmailRequest req) {
        return SignupCommand.builder()
                .email(req.getEmail())
                .password(req.getPassword())
                .name(blankToNull(req.getName()))
                .nickname(blankToNull(req.getNickname()))
                .phoneNumber(blankToNull(req.getPhoneNumber()))
                .birthDate(parseDate(req.getBirthDate()))
                .nationality(blankToNull(req.getNationality()))
                .consents(req.getConsentsList().stream().map(c ->
                        SignupCommand.Consent.builder()
                                .termsVersion(c.getTermsVersion())
                                .type(mapConsentType(c.getType())) // ✅ proto enum 타입 맞춤
                                .isAgreed(c.getIsAgreed())
                                .build()
                ).toList())
                .build();
    }

    // ✅ UpdateProfileCommand 제거 -> Patch DTO로 변경
    public ProfilePatch toProfilePatch(UpdateMyProfileRequest req) {
        return new ProfilePatch(
                blankToNull(req.getName()),
                blankToNull(req.getNickname()),
                blankToNull(req.getPhoneNumber()),
                parseDate(req.getBirthDate()),
                blankToNull(req.getNationality())
        );
    }

    public List<SignupCommand.Consent> toConsentCommands(UpdateConsentsRequest req) {
        return req.getConsentsList().stream().map(c ->
                SignupCommand.Consent.builder()
                        .termsVersion(c.getTermsVersion())
                        .type(mapConsentType(c.getType()))
                        .isAgreed(c.getIsAgreed())
                        .build()
        ).toList();
    }

    public SignUpResponse toSignUpResponse(UUID identityId, UserProfile profile) {
        return SignUpResponse.newBuilder()
                .setIdentityId(identityId.toString())
                .setProfileId(profile.getProfileId().toString())
                .setProfileCompleted(profile.getNickname() != null && !profile.getNickname().isBlank())
                .setCreatedAt(toTs(profile.getIdentity().getCreatedAt()))
                .build();
    }

    public UserProfileResponse toUserProfileResponse(UserProfile p) {
        return UserProfileResponse.newBuilder()
                .setIdentityId(p.getIdentity().getIdentityId().toString())
                .setProfileId(p.getProfileId().toString())
                .setEmail(nvl(p.getEmail()))
                .setName(nvl(p.getName()))
                .setNickname(nvl(p.getNickname()))
                .setPhoneNumber(nvl(p.getPhoneNumber()))
                .setBirthDate(p.getBirthDate() == null ? "" : p.getBirthDate().toString())
                .setIsAdult(Boolean.TRUE.equals(p.getIsAdult()))
                .setNationality(nvl(p.getNationality()))
                .setUpdatedAt(toTs(p.getUpdatedAt()))
                .build();
    }

    // ✅ 핵심: 여기 타입은 playtab.user.v1가 아니라 java_package 기반
    private ConsentType mapConsentType(com.playtab.userservice.proto.v1.ConsentType t) {
        return switch (t) {
            case PRIVACY -> ConsentType.PRIVACY;
            case SERVICE -> ConsentType.SERVICE;
            case MARKETING -> ConsentType.MARKETING;
            default -> ConsentType.SERVICE;
        };
    }

    private LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        return LocalDate.parse(s);
    }

    private String blankToNull(String s) { return (s == null || s.isBlank()) ? null : s; }
    private String nvl(String s) { return s == null ? "" : s; }

    private Timestamp toTs(Instant i) {
        if (i == null) return Timestamp.getDefaultInstance();
        return Timestamp.newBuilder().setSeconds(i.getEpochSecond()).setNanos(i.getNano()).build();
    }
}
