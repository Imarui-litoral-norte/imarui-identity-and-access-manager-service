package br.com.imarui.ima.identity.core.application.exception;

public class CurrentActorUnavailableException extends RuntimeException {
    public CurrentActorUnavailableException() { super("An authenticated Identity subject is required."); }
}
