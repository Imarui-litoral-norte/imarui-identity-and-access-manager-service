package br.com.imarui.ima.identity.core.domain.model.identity;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

public record IdentityId(@NotNull UUID value) {

    public IdentityId {
        Objects.requireNonNull(value, "IdentityId value must not be null.");
    }

    public static @NotNull IdentityId generate() {
        return new IdentityId(UUID.randomUUID());
    }

    public static @NotNull IdentityId from(@NotNull UUID value) {
        return new IdentityId(value);
    }

    public static @NotNull IdentityId from(@NotNull String value) {
        Objects.requireNonNull(value, "IdentityId value must not be null.");

        try {
            return from(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Invalid IdentityId: " + value,
                    exception
            );
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}