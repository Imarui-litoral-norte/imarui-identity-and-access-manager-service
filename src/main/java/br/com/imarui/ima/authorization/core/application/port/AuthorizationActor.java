package br.com.imarui.ima.authorization.core.application.port;
import java.util.UUID;
/** Trusted authenticated actor; never supplied by a request payload. */
public interface AuthorizationActor { UUID requireIdentityId(); }
