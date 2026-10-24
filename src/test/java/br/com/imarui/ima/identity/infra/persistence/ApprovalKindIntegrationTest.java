package br.com.imarui.ima.identity.infra.persistence;

import static org.junit.jupiter.api.Assertions.*;
import br.com.imarui.ima.identity.support.IdentityIntegrationSupport;
import br.com.imarui.ima.identity.core.domain.enums.identity.*;
import br.com.imarui.ima.identity.core.domain.enums.affiliation.AffiliationType;
import br.com.imarui.ima.identity.core.domain.model.identity.IdentityId;
import br.com.imarui.ima.identity.core.domain.model.identityapplication.*;
import br.com.imarui.ima.identity.core.application.command.identityapplication.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ApprovalKindIntegrationTest extends IdentityIntegrationSupport {
  @ParameterizedTest @EnumSource(value=IdentityKind.class,names={"PERSON","LEGAL_ENTITY"})
  void documentApplicationCanBecomeServiceWithoutLosingOriginalDetails(IdentityKind source) {
    var id=request(source,"candidate@example.test");
    var result=approve.execute(new ApproveIdentityApplicationCommand(id,tenant.getId(),
      new AffiliationSelection(AffiliationType.EXTERNAL,null,null,null),IdentityKind.SERVICE));
    var persisted=applications.findById(id).orElseThrow();
    assertEquals(source,persisted.getKind());
    assertEquals(IdentityKind.SERVICE,persisted.getApprovedKind());
    assertEquals(IdentityKind.SERVICE,result.approvedKind());
    assertEquals(IdentityKind.SERVICE,identities.findById(IdentityId.from(result.resolvedIdentityId())).orElseThrow().getKind());
    assertNotNull(persisted.getReviewedBy());
    assertNotNull(persisted.getReviewedAt());
    assertEquals(0,jdbc.queryForObject("select count(*) from ima_person_identities where id=?",Integer.class,result.resolvedIdentityId()));
    assertEquals(0,jdbc.queryForObject("select count(*) from ima_legal_entity_identities where id=?",Integer.class,result.resolvedIdentityId()));
    if(source==IdentityKind.PERSON) assertEquals("52998224725",((PersonIdentityApplicationDetails)persisted.getDetails()).cpf().value());
    else assertEquals("11222333000181",((LegalEntityIdentityApplicationDetails)persisted.getDetails()).cnpj().value());
  }
  @Test void serviceApplicationCannotBeApprovedAsPersonWithoutDocument() {
    var id=request(IdentityKind.SERVICE,"candidate@example.test");
    assertThrows(IllegalArgumentException.class,()->approve.execute(new ApproveIdentityApplicationCommand(id,tenant.getId(),
      new AffiliationSelection(AffiliationType.EXTERNAL,null,null,null),IdentityKind.PERSON)));
    assertTrue(applications.findById(id).orElseThrow().isPending());
    assertNull(applications.findById(id).orElseThrow().getApprovedKind());
  }
  @Test void publicEndpointsInferDocumentTypeAndCreateOnlyPendingRequests() throws Exception {
    var person=http("POST","/api/v1/identity-applications/users","""
      {"name":"Person Example","document":"529.982.247-25","nickname":"person-example","email":"person@example.test"}
      """,null);
    assertEquals(201,person.statusCode(),person.body());
    var parsed=json.readTree(person.body());
    assertEquals("PERSON",parsed.path("type").asText());
    assertEquals("PENDING",parsed.path("status").asText());
    assertTrue(parsed.path("approvedKind").isNull());
    assertTrue(parsed.path("resolvedIdentityId").isNull());
    assertEquals(201,http("POST","/api/v1/identity-applications/services","""
      {"name":"Integration","nickname":"service-example","email":"service@example.test","description":"Test integration"}
      """,null).statusCode());
    assertEquals(400,http("POST","/api/v1/identity-applications/users","""
      {"name":"Invalid","nickname":"invalid-example","email":"invalid@example.test"}
      """,null).statusCode());
  }
}
