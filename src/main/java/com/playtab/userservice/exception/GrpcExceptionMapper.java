package com.playtab.userservice.exception;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.springframework.stereotype.Component;

@Component
public class GrpcExceptionMapper {

    public StatusRuntimeException unauthenticated() {
        return Status.UNAUTHENTICATED.withDescription("Unauthenticated").asRuntimeException();
    }

    public StatusRuntimeException toStatus(Exception e) {
        if (e instanceof DomainException de) {
            ErrorCode code = de.getErrorCode(); // ✅ 네 DomainException에 getter 있어야 함
            return switch (code) {
                case AUTH_FAILED -> Status.UNAUTHENTICATED.withDescription(de.getMessage()).asRuntimeException();
                case ACCOUNT_DELETED, ACCOUNT_LOCKED -> Status.PERMISSION_DENIED.withDescription(de.getMessage()).asRuntimeException();
                case DUPLICATE_EMAIL, DUPLICATE_NICKNAME -> Status.ALREADY_EXISTS.withDescription(de.getMessage()).asRuntimeException();
                default -> Status.INTERNAL.withDescription(de.getMessage()).asRuntimeException();
            };
        }
        return Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException();
    }
}
