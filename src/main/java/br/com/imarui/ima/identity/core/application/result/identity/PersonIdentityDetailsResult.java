package br.com.imarui.ima.identity.core.application.result.identity;
public record PersonIdentityDetailsResult(String fullName, String cpf, java.time.LocalDate birthDate, String phoneNumber) implements IdentityDetailsResult {}
