package br.com.imarui.ima.identity.core.application.port;

import java.util.UUID;

public interface InitialAccessProof {
  boolean established(UUID identityId);
}
