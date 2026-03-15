package com.playtab.userservice.grpc.mapper;

import com.google.protobuf.Timestamp;
import com.playtab.userservice.dto.user.ProfilePatch;
import com.playtab.userservice.dto.user.SignupCommand;
import com.playtab.userservice.entity.UserProfile;
import com.playtab.userservice.entity.enums.ConsentType;
import com.playtab.userservice.entity.enums.Gender;
import com.playtab.userservice.exception.DomainException;
import com.playtab.userservice.exception.ErrorCode;
import com.playtab.userservice.proto.v1.*;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Component
public class UserGrpcMapper {

    public SignupCommand toSignupCommand(SignUpWithEmailRequest req) {
        return SignupCommand.builder()
                .email(req.getEmail())
                .password(req.getPassword())
                .name(blankToNull(req.getName()))
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
                mapGender(req.getGender())
        );
    }

    public UserProfileResponse toUserProfileResponse(UserProfile p) {
        return UserProfileResponse.newBuilder()
                .setIdentityId(p.getIdentity().getIdentityId().toString())
                .setProfileId(p.getProfileId().toString())
                .setEmail(nvl(p.getEmail()))
                .setName(nvl(p.getName()))
                .setGender(mapGenderProto(p.getGender()))
                .setPhoneNumber(nvl(p.getPhoneNumber()))
                .setBirthDate(p.getBirthDate() == null ? "" : p.getBirthDate().toString())
                .setIsAdult(Boolean.TRUE.equals(p.getIsAdult()))
                .setNationality(nvl(p.getNationality()))
                .setUpdatedAt(toTs(p.getUpdatedAt()))
                .build();
    }

    private Gender mapGender(com.playtab.userservice.proto.v1.Gender g) {
        if (g == null) return Gender.UNSPECIFIED;
        return switch (g) {
            case MALE -> Gender.MALE;
            case FEMALE -> Gender.FEMALE;
            case OTHER -> Gender.OTHER;
            default -> Gender.UNSPECIFIED;
        };
    }

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
            case CONSENT_TYPE_UNSPECIFIED, UNRECOGNIZED ->
                    throw new DomainException(ErrorCode.CONSENT_TYPE_UNSPECIFIED);
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

    public Timestamp toTs(Instant i) {
        if (i == null) return Timestamp.getDefaultInstance();
        return Timestamp.newBuilder().setSeconds(i.getEpochSecond()).setNanos(i.getNano()).build();
    }
}
