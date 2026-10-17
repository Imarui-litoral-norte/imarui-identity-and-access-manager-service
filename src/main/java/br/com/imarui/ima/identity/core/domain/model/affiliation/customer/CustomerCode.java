package br.com.imarui.ima.identity.core.domain.model.affiliation.customer;

import br.com.imarui.ima.identity.core.domain.exception.affiliation.InvalidCustomerCodeException;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public record CustomerCode(String value) {

    public CustomerCode {
        Objects.requireNonNull(
                value,
                "CustomerCode value cannot be null"
        );

        value = value.trim();

        if (value.length() > 255) throw new InvalidCustomerCodeException("CustomerCode must not exceed 255 characters.");
        if (value.isBlank()) {
            throw new InvalidCustomerCodeException(
                    "CustomerCode value cannot be blank."
            );
        }
    }

    public static CustomerCode from(@NotNull String value) {
        return new CustomerCode(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
