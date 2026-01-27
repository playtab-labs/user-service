package com.playtab.userservice.grpc;

import com.playtab.userservice.dto.user.SignupCommand;
import com.playtab.userservice.entity.AuthConsent;
import com.playtab.userservice.entity.AuthIdentity;
import com.playtab.userservice.entity.UserProfile;
import com.playtab.userservice.entity.enums.Gender;
import com.playtab.userservice.exception.GrpcExceptionMapper;
import com.playtab.userservice.grpc.interceptor.AuthContextKeys;
import com.playtab.userservice.grpc.mapper.UserGrpcMapper;
import com.playtab.userservice.proto.v1.*;
import com.playtab.userservice.repository.AuthConsentRepository;
import com.playtab.userservice.repository.AuthIdentityRepository;
import com.playtab.userservice.repository.UserProfileRepository;
import com.playtab.userservice.service.user.UserSignupService;
import com.playtab.userservice.service.user.email.EmailVerificationService;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.UUID;

@GrpcService
public class UserGrpcService extends UserServiceGrpc.UserServiceImplBase {

    private final UserSignupService signupService;
    private final UserProfileRepository profileRepo;
    private final UserGrpcMapper mapper;
    private final GrpcExceptionMapper ex;
    private final EmailVerificationService emailVerificationService;
    private final AuthIdentityRepository identityRepo;
    private final AuthConsentRepository consentRepo;

    public UserGrpcService(UserSignupService signupService,
                           UserProfileRepository profileRepo,
                           UserGrpcMapper mapper,
                           GrpcExceptionMapper ex,
                           EmailVerificationService emailVerificationService,
                           AuthIdentityRepository identityRepo,
                           AuthConsentRepository consentRepo) {
        this.signupService = signupService;
        this.profileRepo = profileRepo;
        this.mapper = mapper;
        this.ex = ex;
        this.emailVerificationService = emailVerificationService;
        this.identityRepo = identityRepo;
        this.consentRepo = consentRepo;
    }

    @Override
    @Transactional
    public void signUpWithEmail(SignUpWithEmailRequest request, StreamObserver<SignUpResponse> responseObserver) {
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
    public void getMyProfile(GetMyProfileRequest request, StreamObserver<UserProfileResponse> responseObserver) {
        try {
            UUID identityId = AuthContextKeys.IDENTITY_ID.get();
            if (identityId == null) throw ex.unauthenticated();

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
    public void updateMyProfile(UpdateMyProfileRequest request, StreamObserver<UserProfileResponse> responseObserver) {
        try {
            UUID identityId = AuthContextKeys.IDENTITY_ID.get();
            if (identityId == null) throw ex.unauthenticated();

            UserProfile profile = profileRepo.findByIdentity_IdentityId(identityId)
                    .orElseThrow(() -> new RuntimeException("Profile not found"));

            boolean changed = false;

            // name
            if (!request.getName().isBlank()) {
                profile.setName(request.getName());
                changed = true;
            }

            // gender (proto default=GENDER_UNSPECIFIED면 업데이트 안 함)
            if (request.getGender() != com.playtab.userservice.proto.v1.Gender.GENDER_UNSPECIFIED) {
                profile.setGender(mapGender(request.getGender()));
                changed = true;
            }

            // phone_number
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

            // nationality
            if (!request.getNationality().isBlank()) {
                profile.setNationality(request.getNationality());
                changed = true;
            }

            if (changed) {
                profileRepo.save(profile);
            }

            responseObserver.onNext(mapper.toUserProfileResponse(profile));
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(ex.toStatus(e));
        }
    }

    @Override
    @Transactional
    public void updateConsents(UpdateConsentsRequest request, StreamObserver<UpdateConsentsResponse> responseObserver) {
        try {
            UUID identityId = AuthContextKeys.IDENTITY_ID.get();
            if (identityId == null) throw ex.unauthenticated();

            var inputs = request.getConsentsList();
            if (inputs == null || inputs.isEmpty()) {
                throw new IllegalArgumentException("consents is empty");
            }

            AuthIdentity identity = identityRepo.findById(identityId)
                    .orElseThrow(() -> new RuntimeException("Identity not found"));

            Instant now = Instant.now();

            for (var in : inputs) {
                String termsVersion = in.getTermsVersion();
                if (termsVersion == null || termsVersion.isBlank()) {
                    throw new IllegalArgumentException("terms_version is required");
                }

                // ✅ proto -> entity enum (풀패키지로 고정)
                com.playtab.userservice.entity.enums.ConsentType type = mapConsentType(in.getType());

                // ✅ upsert
                AuthConsent consent = consentRepo
                        .findByIdentity_IdentityIdAndTypeAndTermsVersion(identityId, type, termsVersion)
                        .orElseGet(() -> {
                            AuthConsent c = new AuthConsent();
                            c.setIdentity(identity);
                            c.setTermsVersion(termsVersion);
                            c.setType(type);
                            return c;
                        });

                boolean agreed = in.getIsAgreed();
                consent.setIsAgreed(agreed);

                // agreed_at 정책:
                // - true  -> now
                // - false -> null (현재 비동의 상태 명확)
                consent.setAgreedAt(agreed ? now : null);

                consentRepo.save(consent);
            }

            responseObserver.onNext(UpdateConsentsResponse.newBuilder().setSuccess(true).build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            responseObserver.onError(ex.toStatus(e));
        }
    }

    private com.playtab.userservice.entity.enums.ConsentType mapConsentType(
            com.playtab.userservice.proto.v1.ConsentType t
    ) {
        return switch (t) {
            case PRIVACY -> com.playtab.userservice.entity.enums.ConsentType.PRIVACY;
            case SERVICE -> com.playtab.userservice.entity.enums.ConsentType.SERVICE;
            case MARKETING -> com.playtab.userservice.entity.enums.ConsentType.MARKETING;
            case CONSENT_TYPE_UNSPECIFIED, UNRECOGNIZED ->
                    throw new IllegalArgumentException("consent type is unspecified");
        };
    }

    @Override
    @Transactional
    public void verifyAdult(VerifyAdultRequest request, StreamObserver<VerifyAdultResponse> responseObserver) {
        try {
            UUID identityId = AuthContextKeys.IDENTITY_ID.get();
            if (identityId == null) throw ex.unauthenticated();

            UserProfile profile = profileRepo.findByIdentity_IdentityId(identityId)
                    .orElseThrow(() -> new RuntimeException("Profile not found"));

            profile.setIsAdult(request.getIsAdult());
            profileRepo.save(profile);

            responseObserver.onNext(VerifyAdultResponse.newBuilder()
                    .setSuccess(true)
                    .setIsAdult(profile.getIsAdult())
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