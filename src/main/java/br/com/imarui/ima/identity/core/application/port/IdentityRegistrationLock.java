package br.com.imarui.ima.identity.core.application.port;
import br.com.imarui.ima.identity.core.domain.model.identityapplication.IdentityApplication;
/** Serialize registration/review for overlapping normalized natural keys in the current transaction. */
public interface IdentityRegistrationLock { void acquire(IdentityApplication application); }
