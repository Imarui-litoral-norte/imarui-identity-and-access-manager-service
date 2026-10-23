package br.com.imarui.ima.authorization.core.repository;
import br.com.imarui.ima.authorization.core.domain.model.PermissionManifest;
import java.time.Instant;
import java.util.*;
public interface CatalogInspectionRepository {
  record Owner(String serviceCode,String namespace,long version,String digest,Instant updatedAt) {}
  record Revision(long version,String digest,Instant recordedAt,int entries) {}
  List<Owner> owners(int offset,int size);
  long ownerCount();
  Optional<Owner> owner(String code);
  List<Revision> revisions(String service,int offset,int size);
  long revisionCount(String service);
  Optional<PermissionManifest> manifest(String service,long version);
}
