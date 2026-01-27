package com.playtab.userservice.grpc;

import com.playtab.userservice.dto.auth.AuthTokens;
import com.playtab.userservice.dto.auth.LoginCommand;
import com.playtab.userservice.dto.auth.LogoutCommand;
import com.playtab.userservice.dto.auth.RefreshCommand;
import com.playtab.userservice.dto.auth.SocialLoginCommand;
import com.playtab.userservice.exception.GrpcExceptionMapper;
import com.playtab.userservice.service.auth.AuthService;
import com.playtab.userservice.entity.enums.CredentialType;
import com.playtab.userservice.proto.v1.*;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
@RequiredArgsConstructor
public class AuthGrpcService extends AuthServiceGrpc.AuthServiceImplBase {

    private final AuthService authService;
    private final GrpcExceptionMapper exceptionMapper;

    @Override
    public void loginWithEmail(LoginWithEmailRequest request, StreamObserver<AuthTokensResponse> responseObserver) {
        try {
            LoginCommand cmd = LoginCommand.builder()
                    .email(request.getEmail())
                    .password(request.getPassword())
                    .deviceFingerprint(request.hasClient() ? request.getClient().getDeviceFingerprint() : null)
                    .userAgent(request.hasClient() ? request.getClient().getUserAgent() : null)
                    .ipAddress(request.hasClient() ? request.getClient().getIpAddress() : null)
                    .build();

            AuthTokens tokens = authService.loginWithEmail(cmd);

            responseObserver.onNext(toAuthTokensResponse(tokens));
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(exceptionMapper.toStatus(e));
        }
    }

    @Override
    public void loginWithSocial(LoginWithSocialRequest request, StreamObserver<AuthTokensResponse> responseObserver) {
        try {
            SocialLoginCommand cmd = SocialLoginCommand.builder()
                    .type(mapCredentialType(request.getType()))
                    .idToken(request.getIdToken())
                    .accessToken(request.getAccessToken())
                    .deviceFingerprint(request.hasClient() ? request.getClient().getDeviceFingerprint() : null)
                    .userAgent(request.hasClient() ? request.getClient().getUserAgent() : null)
                    .ipAddress(request.hasClient() ? request.getClient().getIpAddress() : null)
                    .build();

            AuthTokens tokens = authService.loginWithSocial(cmd);

            responseObserver.onNext(toAuthTokensResponse(tokens));
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(exceptionMapper.toStatus(e));
        }
    }

    @Override
    public void refreshTokens(RefreshTokensRequest request, StreamObserver<AuthTokensResponse> responseObserver) {
        try {
            RefreshCommand cmd = RefreshCommand.builder()
                    .refreshToken(request.getRefreshToken())
                    .deviceFingerprint(request.hasClient() ? request.getClient().getDeviceFingerprint() : null)
                    .userAgent(request.hasClient() ? request.getClient().getUserAgent() : null)
                    .ipAddress(request.hasClient() ? request.getClient().getIpAddress() : null)
                    .build();

            AuthTokens tokens = authService.refreshTokens(cmd);

            responseObserver.onNext(toAuthTokensResponse(tokens));
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(exceptionMapper.toStatus(e));
        }
    }

    @Override
    public void logout(LogoutRequest request, StreamObserver<LogoutResponse> responseObserver) {
        try {
            LogoutCommand cmd = LogoutCommand.builder()
                    .refreshToken(request.getRefreshToken())
                    .build();

            authService.logout(cmd);

            responseObserver.onNext(LogoutResponse.newBuilder().setSuccess(true).build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(exceptionMapper.toStatus(e));
        }
    }

    private AuthTokensResponse toAuthTokensResponse(AuthTokens t) {
        return AuthTokensResponse.newBuilder()
                .setAccessToken(t.accessToken())
                .setRefreshToken(t.refreshToken())
                .setAccessExpiresInSeconds(t.accessExpiresInSeconds())
                .setRefreshExpiresInSeconds(t.refreshExpiresInSeconds())
                .setProfileCompleted(t.profileCompleted())
                .build();
    }

    private CredentialType mapCredentialType(
            com.playtab.userservice.proto.v1.CredentialType t
    ) {
        return switch (t) {
            case GOOGLE -> CredentialType.GOOGLE;
            case NAVER  -> CredentialType.NAVER;
            case KAKAO  -> CredentialType.KAKAO;

            case EMAIL, CREDENTIAL_TYPE_UNSPECIFIED, UNRECOGNIZED ->
                    throw new com.playtab.userservice.exception.DomainException(
                            com.playtab.userservice.exception.ErrorCode.UNSUPPORTED_SOCIAL_PROVIDER
                    );

            default -> throw new com.playtab.userservice.exception.DomainException(
                    com.playtab.userservice.exception.ErrorCode.UNSUPPORTED_SOCIAL_PROVIDER
            );
        };
    }
}