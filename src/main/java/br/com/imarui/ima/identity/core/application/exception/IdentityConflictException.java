package br.com.imarui.ima.identity.core.application.exception;

public class IdentityConflictException extends RuntimeException {
    public IdentityConflictException() { super("Identity or pending application conflicts with an existing record."); }
}
