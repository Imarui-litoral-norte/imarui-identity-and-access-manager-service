package br.com.imarui.ima.identity.core.domain.model.identity.service;

import br.com.imarui.ima.identity.core.domain.exception.identity.InvalidServiceNameException;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public record ServiceName(String value) {

    private static final int MAX_LENGTH = 150;

    public ServiceName {
        Objects.requireNonNull(
                value,
                "ServiceName value cannot be null"
        );

        value = value.trim();

        if (value.isBlank()) {
            throw new InvalidServiceNameException(
                    "ServiceName value cannot be blank."
            );
        }

        if (value.length() > MAX_LENGTH) {
            throw new InvalidServiceNameException(
                    "ServiceName value cannot exceed "
                            + MAX_LENGTH
                            + " characters."
            );
        }
    }

    public static ServiceName from(@NotNull String value) {
        return new ServiceName(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
