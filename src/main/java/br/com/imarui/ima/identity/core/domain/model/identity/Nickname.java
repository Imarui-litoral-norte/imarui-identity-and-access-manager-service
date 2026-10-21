package br.com.imarui.ima.identity.core.domain.model.identity;

import java.util.Locale;

public record Nickname(String value) {
  public Nickname {
    if (value == null) throw new IllegalArgumentException("Nickname is required.");
    value = value.trim().toLowerCase(Locale.ROOT);
    if (!value.matches("(?=.{3,64}$)(?=.*[a-z])[a-z0-9_.-]+"))
      throw new IllegalArgumentException("Invalid nickname.");
  }

  public static Nickname from(String value) {
    return new Nickname(value);
  }
}
