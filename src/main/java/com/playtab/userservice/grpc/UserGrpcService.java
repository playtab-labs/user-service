package com.playtab.userservice.grpc;

import com.playtab.userservice.dto.user.SignupCommand;
import com.playtab.userservice.entity.UserProfile;
import com.playtab.userservice.entity.enums.Gender;
import com.playtab.userservice.exception.GrpcExceptionMapper;
import com.playtab.userservice.grpc.interceptor.AuthContextKeys;
import com.playtab.userservice.grpc.mapper.SettingsGrpcMapper;
import com.playtab.userservice.grpc.mapper.UserGrpcMapper;
import com.playtab.userservice.proto.v1.*;
import com.playtab.userservice.service.user.AccountCommandService;
import com.playtab.userservice.service.user.ConsentCommandService;
import com.playtab.userservice.service.user.ConsentQueryService;
import com.playtab.userservice.service.user.UserSettingsService;
import com.playtab.userservice.service.user.UserSignupService;
import com.playtab.userservice.service.user.email.EmailVerificationService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.UUID;

@GrpcService
@RequiredArgsConstructor
public class UserGrpcService extends UserServiceGrpc.UserServiceImplBase {

    private final UserSignupService signupService;
    private final com.playtab.userservice.repository.UserProfileRepository profileRepo;

    private final UserGrpcMapper mapper;
    private final SettingsGrpcMapper settingsMapper;

    private final GrpcExceptionMapper ex;
    private final EmailVerificationService emailVerificationService;

    private final UserSettingsService settingsService;
    private final ConsentQueryService consentQueryService;
    private final ConsentCommandService consentCommandService;
    private final AccountCommandService accountCommandService;

    @Override
    @Transactional
    public void signUpWithEmail(SignUpWithEmailRequest request,
                                StreamObserver<SignUpResponse> responseObserver) {
        try {
            SignupCommand cmd = mapper.toSignupCommand(request);
            UUID identityId = signupService.signUpWithEmail(cmd);

            UserProfile profile = profileRepo.findByIdentity_IdentityId(identityId)
                    .orElseThrow(() -> new RuntimeException("Profile not found after signup"));

            responseObserver.onNext(SignUpResponse.newBuilder()
                    .setIdentityId(identityId.toString())
                    .setProfileId(profile.getProfileId().toString())
                    .setProfileCompleted(isProfileCompleted(profile))
                    .setCreatedAt(mapper.toTs(profile.getIdentity().getCreatedAt()))
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(ex.toStatus(e));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void getMyProfile(GetMyProfileRequest request,
                             StreamObserver<UserProfileResponse> responseObserver) {
        try {
            UUID identityId = requireIdentityId();

            UserProfile profile = profileRepo.findByIdentity_IdentityId(identityId)
                    .orElseThrow(() -> new RuntimeException("Profile not found"));

            responseObserver.onNext(mapper.toUserProfileResponse(profile));
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(ex.toStatus(e));
        }
    }

    @Override
    @Transactional
    public void updateMyProfile(UpdateMyProfileRequest request,
                                StreamObserver<UserProfileResponse> responseObserver) {
        try {
            UUID identityId = requireIdentityId();

            UserProfile profile = profileRepo.findByIdentity_IdentityId(identityId)
                    .orElseThrow(() -> new RuntimeException("Profile not found"));

            boolean changed = false;

            if (!request.getName().isBlank()) {
                profile.setName(request.getName());
                changed = true;
            }

            if (request.getGender() != com.playtab.userservice.proto.v1.Gender.GENDER_UNSPECIFIED) {
                profile.setGender(mapGender(request.getGender()));
                changed = true;
            }

            if (!request.getPhoneNumber().isBlank()) {
                profile.setPhoneNumber(request.getPhoneNumber());
                changed = true;
            }

            if (!request.getBirthDate().isBlank()) {
                try {
                    LocalDate birth = LocalDate.parse(request.getBirthDate());
                    profile.setBirthDate(birth);
                    changed = true;
                } catch (DateTimeParseException e) {
                    throw new IllegalArgumentException("birth_date must be ISO-8601 yyyy-MM-dd");
                }
            }

            if (!request.getNationality().isBlank()) {
                profile.setNationality(request.getNationality());
                changed = true;
            }

            if (changed) profileRepo.save(profile);

            responseObserver.onNext(mapper.toUserProfileResponse(profile));
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(ex.toStatus(e));
        }
    }

    @Override
    @Transactional
    public void updateConsents(UpdateConsentsRequest request,
                               StreamObserver<UpdateConsentsResponse> responseObserver) {
        try {
            UUID identityId = requireIdentityId();

            if (request.getConsentsList() == null || request.getConsentsList().isEmpty()) {
                throw new IllegalArgumentException("consents is empty");
            }

            consentCommandService.upsertConsents(identityId, request.getConsentsList());

            responseObserver.onNext(UpdateConsentsResponse.newBuilder().setSuccess(true).build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(ex.toStatus(e));
        }
    }

    @Override
    @Transactional
    public void verifyAdult(VerifyAdultRequest request,
                            StreamObserver<VerifyAdultResponse> responseObserver) {
        try {
            UUID identityId = requireIdentityId();

            UserProfile profile = profileRepo.findByIdentity_IdentityId(identityId)
                    .orElseThrow(() -> new RuntimeException("Profile not found"));

            profile.setIsAdult(request.getIsAdult());
            profileRepo.save(profile);

            responseObserver.onNext(VerifyAdultResponse.newBuilder()
                    .setSuccess(true)
                    .setIsAdult(Boolean.TRUE.equals(profile.getIsAdult()))
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(ex.toStatus(e));
        }
    }

    @Override
    public void sendEmailVerificationCode(SendEmailVerificationCodeRequest request,
                                          StreamObserver<SendEmailVerificationCodeResponse> responseObserver) {
        try {
            long ttl = emailVerificationService.sendCode(request.getEmail(), request.getSessionId());
            responseObserver.onNext(SendEmailVerificationCodeResponse.newBuilder()
                    .setSuccess(true)
                    .setTtlSeconds(ttl)
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(ex.toStatus(e));
        }
    }

    @Override
    public void verifyEmailCode(VerifyEmailCodeRequest request,
                                StreamObserver<VerifyEmailCodeResponse> responseObserver) {
        try {
            boolean ok = emailVerificationService.verifyCode(request.getEmail(), request.getCode(), request.getSessionId());
            responseObserver.onNext(VerifyEmailCodeResponse.newBuilder()
                    .setSuccess(true)
                    .setVerified(ok)
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(ex.toStatus(e));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void getMySettings(GetMySettingsRequest request,
                              StreamObserver<UserSettingsResponse> responseObserver) {
        try {
            UUID identityId = requireIdentityId();

            var settings = settingsService.getOrCreate(identityId);
            var marketing = consentQueryService.getLatestMarketing(identityId);

            responseObserver.onNext(
                    settingsMapper.toUserSettingsResponse(
                            identityId,
                            settings,
                            marketing.agreed(),
                            marketing.termsVersion()
                    )
            );
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(ex.toStatus(e));
        }
    }

    @Override
    @Transactional
    public void updateMySettings(UpdateMySettingsRequest request,
                                 StreamObserver<UserSettingsResponse> responseObserver) {
        try {
            UUID identityId = requireIdentityId();

            String locale = request.hasLocale() ? request.getLocale().getValue() : null;
            Boolean pushEnabled = request.hasPushEnabled() ? request.getPushEnabled().getValue() : null;
            Boolean emailEnabled = request.hasEmailNotificationsEnabled()
                    ? request.getEmailNotificationsEnabled().getValue()
                    : null;

            var settings = settingsService.patch(identityId, locale, pushEnabled, emailEnabled);
            var marketing = consentQueryService.getLatestMarketing(identityId);

            responseObserver.onNext(
                    settingsMapper.toUserSettingsResponse(
                            identityId,
                            settings,
                            marketing.agreed(),
                            marketing.termsVersion()
                    )
            );
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(ex.toStatus(e));
        }
    }

    @Override
    @Transactional
    public void changeMyPassword(ChangeMyPasswordRequest request,
                                 StreamObserver<ChangeMyPasswordResponse> responseObserver) {
        try {
            UUID identityId = requireIdentityId();

            accountCommandService.changeMyPassword(
                    identityId,
                    request.getCurrentPassword(),
                    request.getNewPassword()
            );

            responseObserver.onNext(ChangeMyPasswordResponse.newBuilder()
                    .setSuccess(true)
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(ex.toStatus(e));
        }
    }

    @Override
    @Transactional
    public void withdrawMyAccount(WithdrawMyAccountRequest request,
                                  StreamObserver<WithdrawMyAccountResponse> responseObserver) {
        try {
            UUID identityId = requireIdentityId();

            accountCommandService.withdrawMyAccount(
                    identityId,
                    request.getRefreshToken()
            );

            responseObserver.onNext(WithdrawMyAccountResponse.newBuilder()
                    .setSuccess(true)
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(ex.toStatus(e));
        }
    }

    private UUID requireIdentityId() {
        UUID identityId = AuthContextKeys.IDENTITY_ID.get();
        if (identityId == null) throw ex.unauthenticated();
        return identityId;
    }

    private boolean isProfileCompleted(UserProfile p) {
        return p.getEmail() != null && !p.getEmail().isBlank()
                && p.getName() != null && !p.getName().isBlank();
    }

    private Gender mapGender(com.playtab.userservice.proto.v1.Gender g) {
        return switch (g) {
            case MALE -> Gender.MALE;
            case FEMALE -> Gender.FEMALE;
            case OTHER -> Gender.OTHER;
            case GENDER_UNSPECIFIED, UNRECOGNIZED -> Gender.UNSPECIFIED;
        };
    }
}