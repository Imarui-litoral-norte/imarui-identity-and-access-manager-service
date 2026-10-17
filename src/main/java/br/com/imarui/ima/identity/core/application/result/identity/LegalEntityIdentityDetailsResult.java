package br.com.imarui.ima.identity.core.application.result.identity;
public record LegalEntityIdentityDetailsResult(String legalName, String tradeName, String cnpj) implements IdentityDetailsResult {}
