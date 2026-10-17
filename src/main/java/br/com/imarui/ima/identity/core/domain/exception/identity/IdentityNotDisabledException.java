package br.com.imarui.ima.identity.core.domain.exception.identity;

public class IdentityNotDisabledException extends RuntimeException {

    public IdentityNotDisabledException(String identityId) {
        super(
                "Identity with id " + identityId
                        + " is not disabled."
        );
    }
}