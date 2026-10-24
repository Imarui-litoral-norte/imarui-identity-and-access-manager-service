package br.com.imarui.ima.identity.infra.persistence;

import static org.junit.jupiter.api.Assertions.*;

import br.com.imarui.ima.identity.support.IdentityIntegrationSupport;
import br.com.imarui.ima.identity.core.domain.enums.identity.*;
import br.com.imarui.ima.identity.core.domain.model.identity.*;
import br.com.imarui.ima.identity.core.application.command.identityapplication.*;
import br.com.imarui.ima.identity.core.application.usecase.identity.ChangeNicknameUseCase;
import br.com.imarui.ima.identity.core.application.exception.IdentityConflictException;
import br.com.imarui.ima.identity.api.module.IdentityAuthenticationApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class GlobalLoginIdentifiersIntegrationTest extends IdentityIntegrationSupport {
  @Autowired IdentityAuthenticationApi resolver;
  @Autowired ChangeNicknameUseCase rename;

  @ParameterizedTest @EnumSource(IdentityKind.class)
  void resolvesAllKindsByGlobalNicknameAndEmailWithoutKind(IdentityKind kind) {
    var i=sample(kind,"account@example.test"); i.assignNickname("global-account"); i=identities.save(i);
    firstAccess(i.getId());
    assertEquals(i.getId().value(),resolver.resolve(null," GLOBAL-ACCOUNT ").orElseThrow().id());
    assertEquals(i.getId().value(),resolver.resolve(null," ACCOUNT@EXAMPLE.TEST ").orElseThrow().id());
    assertNotNull(authentication.login(null,"ACCOUNT@example.test","Fixture password 123!").accessToken());
    if(kind==IdentityKind.PERSON) assertEquals(i.getId().value(),resolver.resolve(null,"529.982.247-25").orElseThrow().id());
    if(kind==IdentityKind.LEGAL_ENTITY) assertEquals(i.getId().value(),resolver.resolve(null,"11.222.333/0001-81").orElseThrow().id());
  }

  @Test void pendingNicknameAndEmailReserveAcrossTypesAndBlockRename() {
    create.execute(new CreateServiceIdentityApplicationCommand("Integration",null,"reserved@example.test","reserved-login"));
    assertThrows(IdentityConflictException.class,()->create.execute(new CreatePersonIdentityApplicationCommand("Person","52998224725","other@example.test",null,null,"RESERVED-LOGIN")));
    assertThrows(IdentityConflictException.class,()->create.execute(new CreatePersonIdentityApplicationCommand("Person","52998224725","RESERVED@EXAMPLE.TEST",null,null,"different-login")));
    assertThrows(IdentityConflictException.class,()->rename.execute(reviewer.getId(),"reserved-login"));
  }

  @Test void existingIdentityReservesEmailAndNicknameAcrossTypes() {
    assertThrows(IdentityConflictException.class,()->create.execute(new CreatePersonIdentityApplicationCommand("Person","52998224725","reviewer@example.test",null,null,"different-login")));
    assertThrows(IdentityConflictException.class,()->create.execute(new CreatePersonIdentityApplicationCommand("Person","52998224725","other@example.test",null,null,"reviewer")));
  }
}
