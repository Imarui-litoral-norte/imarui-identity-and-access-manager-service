package br.com.imarui.ima.authorization.core.domain;

import static org.junit.jupiter.api.Assertions.*;
import br.com.imarui.ima.authorization.api.catalog.*;
import br.com.imarui.ima.authorization.core.domain.model.PermissionManifest;
import java.util.*;
import org.junit.jupiter.api.Test;

class PermissionManifestTest {
  private PermissionManifest.Entry entry(String code) { return new PermissionManifest.Entry(code,"Description","journey",false); }

  @Test void canonicalDigestIsOrderIndependentAndChangesWithMetadataAndVersion() {
    var a=entry("fleet.read"); var b=entry("fleet.write");
    var manifest=new PermissionManifest("fleet","fleet",1,List.of(a,b));
    assertEquals(manifest.digest(),new PermissionManifest("fleet","fleet",1,List.of(b,a)).digest());
    assertNotEquals(manifest.digest(),new PermissionManifest("fleet","fleet",2,List.of(a,b)).digest());
    assertNotEquals(manifest.digest(),new PermissionManifest("fleet","fleet",1,List.of(a)).digest());
    assertThrows(UnsupportedOperationException.class,() -> manifest.permissions().clear());
  }

  @Test void namespacesDuplicatesAndMalformedManifestsAreRejected() {
    assertThrows(IllegalArgumentException.class,() -> new PermissionManifest("fleet","fleet",1,List.of(entry("other.read"))));
    assertThrows(IllegalArgumentException.class,() -> new PermissionManifest("fleet","fleet",1,List.of(entry("fleet.read"),entry("fleet.read"))));
    assertThrows(IllegalArgumentException.class,() -> new PermissionManifest("FLEET","fleet",1,List.of()));
    assertThrows(IllegalArgumentException.class,() -> new PermissionManifest("fleet","fleet",0,List.of()));
    assertThrows(IllegalArgumentException.class,() -> new PermissionManifest.Entry("fleet.read"," ","module",false));
  }

  @Test void internalCatalogPreservesCodesAndDeclaresAllThreeModules() {
    var manifest=PermissionManifest.internal();
    assertEquals(PermissionCode.values().length,manifest.permissions().size());
    assertEquals(Set.of("IDENTITY","AUTHENTICATION","AUTHORIZATION"),
        new HashSet<>(manifest.permissions().stream().map(PermissionManifest.Entry::module).toList()));
    assertTrue(manifest.permissions().stream().anyMatch(p -> p.code().equals("IDENTITY_READ")));
    assertThrows(IllegalArgumentException.class,() -> new PermissionManifest("fleet","fleet",1,List.of(entry("IDENTITY_READ"))));
  }
}
