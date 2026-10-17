package br.com.imarui.ima.identity.core.application.command.identity;

/** Target is always resolved from CurrentActor, never supplied by the caller. */
public record SelfDeleteIdentityCommand(String reason) {}
