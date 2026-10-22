package br.com.imarui.ima.identity.api.module;

import java.util.*;

/** Minimal current lifecycle projections; authorization remains independent of Affiliation. */
public interface IdentityAuthorizationApi {
  Set<UUID> activeIdentityIds();
  Set<UUID> activeTenantIds();
}
