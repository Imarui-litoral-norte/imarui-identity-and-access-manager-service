package br.com.imarui.ima.authorization.core.repository;

import java.util.UUID;

public interface ServiceResourceRepository {
  UUID resolve(String service,String type,String resourceId);
  String permissionOwner(String permission);
  void bind(String service,String type,String resourceId,UUID scope);
}
