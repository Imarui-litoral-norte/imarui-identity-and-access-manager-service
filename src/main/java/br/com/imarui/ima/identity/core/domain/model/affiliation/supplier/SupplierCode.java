package br.com.imarui.ima.identity.core.domain.model.affiliation.supplier;

import br.com.imarui.ima.identity.core.domain.exception.affiliation.InvalidSupplierCodeException;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public record SupplierCode(String value) {

    public SupplierCode {
        Objects.requireNonNull(
                value,
                "SupplierCode value cannot be null"
        );

        value = value.trim();

        if (value.length() > 255) throw new InvalidSupplierCodeException("SupplierCode must not exceed 255 characters.");
        if (value.isBlank()) {
            throw new InvalidSupplierCodeException(
                    "SupplierCode value cannot be blank."
            );
        }
    }

    public static SupplierCode from(@NotNull String value) {
        return new SupplierCode(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
