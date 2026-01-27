package com.playtab.userservice.grpc;

import com.playtab.userservice.dto.user.SignupCommand;
import com.playtab.userservice.entity.UserProfile;
import com.playtab.userservice.exception.GrpcExceptionMapper;
import com.playtab.userservice.grpc.interceptor.AuthContextKeys;
import com.playtab.userservice.grpc.mapper.UserGrpcMapper;
import com.playtab.userservice.proto.v1.*;
import com.playtab.userservice.repository.UserProfileRepository;
import com.playtab.userservice.service.user.UserSignupService;
import com.playtab.userservice.service.user.email.EmailVerificationService;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.transaction.annotation.Transactional; // ✅ 추가

import java.util.UUID;

@GrpcService
public class UserGrpcService extends UserServiceGrpc.UserServiceImplBase {

    private final UserSignupService signupService;
    private final UserProfileRepository profileRepo;
    private final UserGrpcMapper mapper;
    private final GrpcExceptionMapper ex;
    private final EmailVerificationService emailVerificationService;

    public UserGrpcService(UserSignupService signupService,
                           UserProfileRepository profileRepo,
                           UserGrpcMapper mapper,
                           GrpcExceptionMapper ex,
                           EmailVerificationService emailVerificationService) {
        this.signupService = signupService;
        this.profileRepo = profileRepo;
        this.mapper = mapper;
        this.ex = ex;
        this.emailVerificationService = emailVerificationService;
    }

    @Override
    @Transactional // ✅ 트랜잭션을 걸어 응답 빌드 시점까지 세션을 유지합니다.
    public void signUpWithEmail(SignUpWithEmailRequest request, StreamObserver<SignUpResponse> responseObserver) {
        try {
            SignupCommand cmd = mapper.toSignupCommand(request);
            UUID identityId = signupService.signUpWithEmail(cmd);

            // 트랜잭션 안에서 조회하므로 profile.getIdentity() 접근 시 세션이 살아있습니다.
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
    @Transactional(readOnly = true) // ✅ 조회 전용 트랜잭션 추가
    public void getMyProfile(GetMyProfileRequest request, StreamObserver<UserProfileResponse> responseObserver) {
        try {
            UUID identityId = AuthContextKeys.IDENTITY_ID.get();
            if (identityId == null) throw ex.unauthenticated();

            UserProfile profile = profileRepo.findByIdentity_IdentityId(identityId)
                    .orElseThrow(() -> new RuntimeException("Profile not found"));

            // mapper 안에서 p.getIdentity() 등을 호출해도 세션이 있어 안전합니다.
            responseObserver.onNext(mapper.toUserProfileResponse(profile));
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

    @Override
    public void updateMyProfile(UpdateMyProfileRequest request, StreamObserver<UserProfileResponse> responseObserver) {
        responseObserver.onError(io.grpc.Status.UNIMPLEMENTED.asRuntimeException());
    }

    @Override
    public void updateConsents(UpdateConsentsRequest request, StreamObserver<UpdateConsentsResponse> responseObserver) {
        responseObserver.onError(io.grpc.Status.UNIMPLEMENTED.asRuntimeException());
    }

    @Override
    public void verifyAdult(VerifyAdultRequest request, StreamObserver<VerifyAdultResponse> responseObserver) {
        responseObserver.onError(io.grpc.Status.UNIMPLEMENTED.asRuntimeException());
    }
}