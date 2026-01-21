package com.playtab.userservice.grpc.interceptor;

import com.playtab.userservice.service.auth.TokenService;
import io.grpc.*;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@GrpcGlobalServerInterceptor
public class JwtAuthInterceptor implements ServerInterceptor {

    private static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    private final TokenService tokenService;

    public JwtAuthInterceptor(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next
    ) {
        String auth = headers.get(AUTHORIZATION);

        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring("Bearer ".length());
            try {
                UUID identityId = tokenService.parseAccessIdentityId(token);
                Context ctx = Context.current().withValue(AuthContextKeys.IDENTITY_ID, identityId);
                return Contexts.interceptCall(ctx, call, headers, next);
            } catch (Exception ignored) {
                // invalid token -> proceed without context
            }
        }
        return next.startCall(call, headers);
    }
}
