package br.com.imarui.ima.authorization.core.application.port;

/** Implemented only by a trusted technical-authentication adapter, never from a request owner field. */
public interface CatalogServiceAuthentication {
  String requireServiceCode();
}
