package com.playtab.userservice.grpc.interceptor;

import com.playtab.userservice.entity.AuthIdentity;
import com.playtab.userservice.entity.enums.IdentityStatus;
import com.playtab.userservice.repository.AuthIdentityRepository;
import io.grpc.*;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@GrpcGlobalServerInterceptor
public class JwtAuthInterceptor implements ServerInterceptor {

    private static final Metadata.Key<String> X_IDENTITY_ID =
            Metadata.Key.of("x-identity-id", Metadata.ASCII_STRING_MARSHALLER);

    private static final Metadata.Key<String> X_ROLE =
            Metadata.Key.of("x-role", Metadata.ASCII_STRING_MARSHALLER);

    private final AuthIdentityRepository identityRepository;

    public JwtAuthInterceptor(AuthIdentityRepository identityRepository) {
        this.identityRepository = identityRepository;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next
    ) {
        String identityIdStr = headers.get(X_IDENTITY_ID);
        String role = headers.get(X_ROLE);

        if (identityIdStr != null) {
            try {
                UUID identityId = UUID.fromString(identityIdStr);

                Optional<AuthIdentity> identity = identityRepository.findById(identityId);
                if (identity.isEmpty() || identity.get().getStatus() != IdentityStatus.ACTIVE) {
                    call.close(Status.UNAUTHENTICATED.withDescription("Identity is inactive or not found"), new Metadata());
                    return new ServerCall.Listener<>() {};
                }

                Context ctx = Context.current()
                        .withValue(AuthContextKeys.IDENTITY_ID, identityId)
                        .withValue(AuthContextKeys.ROLE, role);
                return Contexts.interceptCall(ctx, call, headers, next);
            } catch (IllegalArgumentException ignored) {
                // invalid identity id format -> proceed without context
            }
        }
        return next.startCall(call, headers);
    }
}
