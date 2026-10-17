package br.com.imarui.ima.identity.core.application.exception;

public class TenantUnavailableException extends RuntimeException {
    public TenantUnavailableException() { super("Tenant must be active for onboarding."); }
}
