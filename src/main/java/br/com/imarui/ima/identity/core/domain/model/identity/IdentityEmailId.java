package br.com.imarui.ima.identity.core.domain.model.identity;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

public record IdentityEmailId(@NotNull UUID value) {

    public IdentityEmailId {
        Objects.requireNonNull(
                value,
                "IdentityEmailId value must not be null."
        );
    }

    public static @NotNull IdentityEmailId generate() {
        return new IdentityEmailId(UUID.randomUUID());
    }

    public static @NotNull IdentityEmailId from(@NotNull UUID value) {
        return new IdentityEmailId(value);
    }

    public static @NotNull IdentityEmailId from(@NotNull String value) {
        Objects.requireNonNull(
                value,
                "IdentityEmailId value must not be null."
        );

        try {
            return from(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Invalid IdentityEmailId: " + value,
                    exception
            );
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}