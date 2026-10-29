package br.com.imarui.ima.identity.core.application.port;

import java.util.Set;
import java.util.UUID;

/** Authoritative tenant visibility, resolved from the current actor and policy. */
public interface TenantDirectoryAccess {
    Set<UUID> readableTenantIds();
}
