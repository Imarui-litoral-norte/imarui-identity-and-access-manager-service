package br.com.imarui.ima.identity.core.domain.exception.identity;

public class IdentityAlreadyDisabledException
        extends RuntimeException {

  public IdentityAlreadyDisabledException(String identityId) {
    super(
            "Identity with id " + identityId
                    + " is already disabled."
    );
  }
}