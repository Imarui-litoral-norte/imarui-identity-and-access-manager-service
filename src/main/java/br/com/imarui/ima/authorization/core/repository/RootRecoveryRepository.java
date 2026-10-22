package br.com.imarui.ima.authorization.core.repository;

import br.com.imarui.ima.authorization.core.domain.model.RootTransfer;
import java.time.Instant;
import java.util.*;

public interface RootRecoveryRepository {
  void create(RootTransfer transfer);
  Optional<RootTransfer> lock(UUID id);
  void confirm(UUID id,Instant now);
}
