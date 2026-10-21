// Adapted from Jeep Club master 9ad04a71e50917e0a53c1c4855ea6770730bd8b9; see
// docs/authentication/reuse-matrix.md
package br.com.imarui.ima.authorization.core.domain.exception.permission;

public class PermissionDescriptionTooLongException extends RuntimeException {

  public PermissionDescriptionTooLongException(int maxLength) {
    super("Permission description cannot exceed " + maxLength + " characters.");
  }
}
