package com.playtab.userservice.grpc.mapper;

import com.google.protobuf.Timestamp;
import com.playtab.userservice.dto.user.ProfilePatch;
import com.playtab.userservice.dto.user.SignupCommand;
import com.playtab.userservice.entity.UserProfile;
import com.playtab.userservice.entity.enums.ConsentType;
import com.playtab.userservice.entity.enums.Gender;
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
                // ✅ Gender 매핑 추가
                .gender(mapGender(req.getGender()))
                .phoneNumber(blankToNull(req.getPhoneNumber()))
                .birthDate(parseDate(req.getBirthDate()))
                .nationality(blankToNull(req.getNationality()))
                .sessionId(blankToNull(req.getSessionId()))
                .consents(req.getConsentsList().stream().map(c ->
                        SignupCommand.Consent.builder()
                                .termsVersion(c.getTermsVersion())
                                .type(mapConsentType(c.getType()))
                                .isAgreed(c.getIsAgreed())
                                .build()
                ).toList())
                .build();
    }

    public ProfilePatch toProfilePatch(UpdateMyProfileRequest req) {
        return new ProfilePatch(
                blankToNull(req.getName()),
                blankToNull(req.getPhoneNumber()),
                parseDate(req.getBirthDate()),
                blankToNull(req.getNationality()),
                // ✅ 순서 수정 (ProfilePatch 생성자 파라미터 순서 확인)
                mapGender(req.getGender())
        );
    }

    public UserProfileResponse toUserProfileResponse(UserProfile p) {
        return UserProfileResponse.newBuilder()
                .setIdentityId(p.getIdentity().getIdentityId().toString())
                .setProfileId(p.getProfileId().toString())
                .setEmail(nvl(p.getEmail()))
                .setName(nvl(p.getName()))
                // ✅ Gender 응답 추가
                .setGender(mapGenderProto(p.getGender()))
                .setPhoneNumber(nvl(p.getPhoneNumber()))
                .setBirthDate(p.getBirthDate() == null ? "" : p.getBirthDate().toString())
                .setIsAdult(Boolean.TRUE.equals(p.getIsAdult()))
                .setNationality(nvl(p.getNationality()))
                .setUpdatedAt(toTs(p.getUpdatedAt()))
                .build();
    }

    // ✅ 추가: Proto Enum -> Entity Enum 변환
    private Gender mapGender(com.playtab.userservice.proto.v1.Gender g) {
        if (g == null) return Gender.UNSPECIFIED;
        return switch (g) {
            case MALE -> Gender.MALE;
            case FEMALE -> Gender.FEMALE;
            case OTHER -> Gender.OTHER;
            default -> Gender.UNSPECIFIED;
        };
    }

    // ✅ 추가: Entity Enum -> Proto Enum 변환
    private com.playtab.userservice.proto.v1.Gender mapGenderProto(Gender g) {
        if (g == null) return com.playtab.userservice.proto.v1.Gender.GENDER_UNSPECIFIED;
        return switch (g) {
            case MALE -> com.playtab.userservice.proto.v1.Gender.MALE;
            case FEMALE -> com.playtab.userservice.proto.v1.Gender.FEMALE;
            case OTHER -> com.playtab.userservice.proto.v1.Gender.OTHER;
            default -> com.playtab.userservice.proto.v1.Gender.GENDER_UNSPECIFIED;
        };
    }

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
        try {
            return LocalDate.parse(s);
        } catch (Exception e) {
            return null;
        }
    }

    private String blankToNull(String s) { return (s == null || s.isBlank()) ? null : s; }
    private String nvl(String s) { return s == null ? "" : s; }

    // ✅ private -> public 변경 (UserGrpcService에서 접근 가능하도록)
    public Timestamp toTs(Instant i) {
        if (i == null) return Timestamp.getDefaultInstance();
        return Timestamp.newBuilder().setSeconds(i.getEpochSecond()).setNanos(i.getNano()).build();
    }
}