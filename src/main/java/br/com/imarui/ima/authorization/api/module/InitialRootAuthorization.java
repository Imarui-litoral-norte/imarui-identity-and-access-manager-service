package br.com.imarui.ima.authorization.api.module;
import java.util.UUID;
/** Sole integration point used inside the existing one-shot Authentication bootstrap transaction. */
public interface InitialRootAuthorization { void assign(UUID identityId); }
