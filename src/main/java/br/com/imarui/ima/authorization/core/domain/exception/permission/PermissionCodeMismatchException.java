// Adapted from Jeep Club master 9ad04a71e50917e0a53c1c4855ea6770730bd8b9; see
// docs/authentication/reuse-matrix.md
package br.com.imarui.ima.authorization.core.domain.exception.permission;

import br.com.imarui.ima.authorization.api.catalog.PermissionCode;

public class PermissionCodeMismatchException extends RuntimeException {

  public PermissionCodeMismatchException(
      PermissionCode currentCode, PermissionCode definitionCode) {
    super(
        "Cannot synchronize permission with different code. Current code: "
            + currentCode
            + ". Definition code: "
            + definitionCode
            + ".");
  }
}
