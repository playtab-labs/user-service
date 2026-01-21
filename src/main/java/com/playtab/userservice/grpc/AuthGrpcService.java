package com.playtab.userservice.grpc;

import com.playtab.userservice.dto.auth.AuthTokens;
import com.playtab.userservice.dto.auth.LoginCommand;
import com.playtab.userservice.exception.GrpcExceptionMapper;
import com.playtab.userservice.proto.v1.*;
import com.playtab.userservice.service.auth.AuthService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
public class AuthGrpcService extends AuthServiceGrpc.AuthServiceImplBase {

    private final AuthService authService;
    private final GrpcExceptionMapper ex;

    public AuthGrpcService(AuthService authService, GrpcExceptionMapper ex) {
        this.authService = authService;
        this.ex = ex;
    }

    @Override
    public void loginWithEmail(LoginWithEmailRequest request, StreamObserver<AuthTokensResponse> responseObserver) {
        try {
            ClientContext c = request.getClient();

            AuthTokens tokens = authService.loginWithEmail(
                    LoginCommand.builder()
                            .email(request.getEmail())
                            .password(request.getPassword())
                            .deviceFingerprint(c.getDeviceFingerprint())
                            .userAgent(c.getUserAgent())
                            .ipAddress(c.getIpAddress())
                            .build()
            );

            responseObserver.onNext(AuthTokensResponse.newBuilder()
                    .setAccessToken(tokens.accessToken())
                    .setRefreshToken(tokens.refreshToken())
                    .setAccessExpiresInSeconds(tokens.accessExpiresInSeconds())
                    .setRefreshExpiresInSeconds(tokens.refreshExpiresInSeconds())
                    .setProfileCompleted(tokens.profileCompleted())
                    .build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            responseObserver.onError(ex.toStatus(e));
        }
    }

    @Override
    public void refreshTokens(RefreshTokensRequest request, StreamObserver<AuthTokensResponse> responseObserver) {
        responseObserver.onError(Status.UNIMPLEMENTED
                .withDescription("RefreshTokens will be implemented in Step5 (session lookup + rotation).")
                .asRuntimeException());
    }

    @Override
    public void logout(LogoutRequest request, StreamObserver<LogoutResponse> responseObserver) {
        responseObserver.onError(Status.UNIMPLEMENTED
                .withDescription("Logout will be implemented in Step5 (session revoke).")
                .asRuntimeException());
    }

    @Override
    public void loginWithSocial(LoginWithSocialRequest request, StreamObserver<AuthTokensResponse> responseObserver) {
        responseObserver.onError(Status.UNIMPLEMENTED
                .withDescription("LoginWithSocial will be implemented in Step6 (OIDC federation).")
                .asRuntimeException());
    }

    @Override
    public void getMyAuthSummary(GetMyAuthSummaryRequest request, StreamObserver<MyAuthSummaryResponse> responseObserver) {
        responseObserver.onError(Status.UNIMPLEMENTED
                .withDescription("GetMyAuthSummary is optional. Implement later if needed.")
                .asRuntimeException());
    }
}
