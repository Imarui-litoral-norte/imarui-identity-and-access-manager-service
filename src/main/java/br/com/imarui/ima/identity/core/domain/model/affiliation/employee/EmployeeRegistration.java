package br.com.imarui.ima.identity.core.domain.model.affiliation.employee;

import br.com.imarui.ima.identity.core.domain.exception.affiliation.InvalidEmployeeRegistrationException;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public record EmployeeRegistration(String value) {

    public EmployeeRegistration {
        Objects.requireNonNull(
                value,
                "EmployeeRegistration value cannot be null"
        );

        value = value.trim();

        if (value.length() > 255) throw new InvalidEmployeeRegistrationException("EmployeeRegistration must not exceed 255 characters.");
        if (value.isBlank()) {
            throw new InvalidEmployeeRegistrationException(
                    "EmployeeRegistration value cannot be blank."
            );
        }
    }

    public static EmployeeRegistration from(@NotNull String value) {
        return new EmployeeRegistration(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
