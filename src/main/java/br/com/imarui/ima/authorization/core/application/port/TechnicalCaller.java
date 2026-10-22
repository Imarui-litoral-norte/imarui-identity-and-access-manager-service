package br.com.imarui.ima.authorization.core.application.port;

import br.com.imarui.ima.authentication.api.module.TechnicalServiceAuthentication.Caller;

public interface TechnicalCaller {
  Caller require();
}
