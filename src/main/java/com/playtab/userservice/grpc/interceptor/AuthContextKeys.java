package com.playtab.userservice.grpc.interceptor;

import io.grpc.Context;
import java.util.UUID;

public class AuthContextKeys {
    public static final Context.Key<UUID> IDENTITY_ID = Context.key("identityId");
    public static final Context.Key<String> ROLE = Context.key("role");
}
