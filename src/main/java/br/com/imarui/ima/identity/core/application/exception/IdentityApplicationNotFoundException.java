package br.com.imarui.ima.identity.core.application.exception;

public class IdentityApplicationNotFoundException extends RuntimeException {
    public IdentityApplicationNotFoundException() { super("Identity application not found."); }
}
