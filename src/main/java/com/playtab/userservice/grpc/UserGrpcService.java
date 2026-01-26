package com.playtab.userservice.grpc;

import com.google.protobuf.Timestamp;
import com.playtab.userservice.dto.user.SignupCommand;
import com.playtab.userservice.entity.UserProfile;
import com.playtab.userservice.exception.GrpcExceptionMapper;
import com.playtab.userservice.grpc.interceptor.AuthContextKeys;
import com.playtab.userservice.proto.v1.*;
import com.playtab.userservice.repository.UserProfileRepository;
import com.playtab.userservice.service.user.UserSignupService;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@GrpcService
public class UserGrpcService extends UserServiceGrpc.UserServiceImplBase {

    private final UserSignupService signupService;
    private final UserProfileRepository profileRepo;
    private final GrpcExceptionMapper ex;

    public UserGrpcService(UserSignupService signupService, UserProfileRepository profileRepo, GrpcExceptionMapper ex) {
        this.signupService = signupService;
        this.profileRepo = profileRepo;
        this.ex = ex;
    }

    @Override
    public void signUpWithEmail(SignUpWithEmailRequest request, StreamObserver<SignUpResponse> responseObserver) {
        try {
            SignupCommand cmd = toSignupCommand(request);
            UUID identityId = signupService.signUpWithEmail(cmd);

            UserProfile profile = profileRepo.findByIdentity_IdentityId(identityId)
                    .orElseThrow(() -> new RuntimeException("Profile not found after signup"));

            responseObserver.onNext(SignUpResponse.newBuilder()
                    .setIdentityId(identityId.toString())
                    .setProfileId(profile.getProfileId().toString())
                    .setProfileCompleted(isProfileCompleted(profile))
                    .setCreatedAt(toTs(Instant.now()))
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(ex.toStatus(e));
        }
    }

    @Override
    public void getMyProfile(GetMyProfileRequest request, StreamObserver<UserProfileResponse> responseObserver) {
        try {
            UUID identityId = AuthContextKeys.IDENTITY_ID.get();
            if (identityId == null) throw ex.unauthenticated();

            UserProfile profile = profileRepo.findByIdentity_IdentityId(identityId)
                    .orElseThrow(() -> new RuntimeException("Profile not found"));

            responseObserver.onNext(toUserProfileResponse(profile));
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(ex.toStatus(e));
        }
    }

    @Override
    public void updateMyProfile(UpdateMyProfileRequest request, StreamObserver<UserProfileResponse> responseObserver) {
        responseObserver.onError(io.grpc.Status.UNIMPLEMENTED
                .withDescription("UpdateMyProfile will be implemented in Step5.")
                .asRuntimeException());
    }

    @Override
    public void updateConsents(UpdateConsentsRequest request, StreamObserver<UpdateConsentsResponse> responseObserver) {
        responseObserver.onError(io.grpc.Status.UNIMPLEMENTED
                .withDescription("UpdateConsents will be implemented in Step5.")
                .asRuntimeException());
    }

    @Override
    public void verifyAdult(VerifyAdultRequest request, StreamObserver<VerifyAdultResponse> responseObserver) {
        responseObserver.onError(io.grpc.Status.UNIMPLEMENTED
                .withDescription("VerifyAdult will be implemented in Step5.")
                .asRuntimeException());
    }

    // -------- mapping helpers --------

    private SignupCommand toSignupCommand(SignUpWithEmailRequest req) {
        return SignupCommand.builder()
                .email(req.getEmail())
                .password(req.getPassword())
                .name(blankToNull(req.getName()))
                .nickname(blankToNull(req.getNickname()))
                .phoneNumber(blankToNull(req.getPhoneNumber()))
                .birthDate(req.getBirthDate().isBlank() ? null : LocalDate.parse(req.getBirthDate()))
                .nationality(blankToNull(req.getNationality()))
                .consents(req.getConsentsList().stream().map(c ->
                        SignupCommand.Consent.builder()
                                .termsVersion(c.getTermsVersion())
                                .type(mapConsentType(c.getType()))
                                .isAgreed(c.getIsAgreed())
                                .build()
                ).toList())
                .build();
    }

    private com.playtab.userservice.entity.enums.ConsentType mapConsentType(com.playtab.userservice.proto.v1.ConsentType t) {
        return switch (t) {
            case PRIVACY -> com.playtab.userservice.entity.enums.ConsentType.PRIVACY;
            case SERVICE -> com.playtab.userservice.entity.enums.ConsentType.SERVICE;
            case MARKETING -> com.playtab.userservice.entity.enums.ConsentType.MARKETING;
            default -> com.playtab.userservice.entity.enums.ConsentType.SERVICE;
        };
    }

    private UserProfileResponse toUserProfileResponse(UserProfile p) {
        // ⚠️ updatedAt 필드명이 너 엔티티에서 다를 수 있음
        Instant updated = p.getUpdatedAt(); // ✅ UserProfile에 updatedAt 있어야 함 (SQL 기준)

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
                .setUpdatedAt(toTs(updated))
                .build();
    }

    private boolean isProfileCompleted(UserProfile p) {
        return p.getEmail() != null && !p.getEmail().isBlank()
                && p.getNickname() != null && !p.getNickname().isBlank();
    }

    private String blankToNull(String s) { return (s == null || s.isBlank()) ? null : s; }
    private String nvl(String s) { return s == null ? "" : s; }

    private Timestamp toTs(Instant i) {
        if (i == null) return Timestamp.getDefaultInstance();
        return Timestamp.newBuilder().setSeconds(i.getEpochSecond()).setNanos(i.getNano()).build();
    }
}
