package br.com.imarui.ima.identity.core.domain.model.identity;

import br.com.imarui.ima.identity.core.domain.enums.identity.EmailType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

public class IdentityEmail {

    private final IdentityEmailId id;
    private final Email email;
    private EmailType type;
    private boolean verified;
    private Instant verifiedAt;
    private final Instant createdAt;

    private IdentityEmail(
            IdentityEmailId id,
            Email email,
            EmailType type,
            boolean verified,
            Instant verifiedAt,
            Instant createdAt
    ) {
        validate(
                id,
                email,
                type,
                verified,
                verifiedAt,
                createdAt
        );

        this.id = id;
        this.email = email;
        this.type = type;
        this.verified = verified;
        this.verifiedAt = verifiedAt;
        this.createdAt = createdAt;
    }

    public static @NotNull IdentityEmail create(
            @NotNull Email email,
            @NotNull EmailType type,
            @NotNull Instant createdAt
    ) {
        return new IdentityEmail(
                IdentityEmailId.generate(),
                email,
                type,
                false,
                null,
                createdAt
        );
    }

    public static @NotNull IdentityEmail reconstitute(
            @NotNull IdentityEmailId id,
            @NotNull Email email,
            @NotNull EmailType type,
            boolean verified,
            @Nullable Instant verifiedAt,
            @NotNull Instant createdAt
    ) {
        return new IdentityEmail(
                id,
                email,
                type,
                verified,
                verifiedAt,
                createdAt
        );
    }

    private static void validate(
            IdentityEmailId id,
            Email email,
            EmailType type,
            boolean verified,
            Instant verifiedAt,
            Instant createdAt
    ) {
        Objects.requireNonNull(
                id,
                "IdentityEmailId must not be null."
        );

        Objects.requireNonNull(
                email,
                "Email must not be null."
        );

        Objects.requireNonNull(
                type,
                "EmailType must not be null."
        );

        Objects.requireNonNull(
                createdAt,
                "CreatedAt must not be null."
        );

        if (verified && verifiedAt == null) {
            throw new IllegalArgumentException(
                    "Verified email must have a verification timestamp."
            );
        }

        if (!verified && verifiedAt != null) {
            throw new IllegalArgumentException(
                    "Unverified email must not have a verification timestamp."
            );
        }

        if (verifiedAt != null && verifiedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException(
                    "Verification timestamp must not be before creation timestamp."
            );
        }
    }

    public @NotNull IdentityEmailId id() {
        return id;
    }

    public @NotNull Email email() {
        return email;
    }

    public @NotNull EmailType type() {
        return type;
    }

    public boolean isVerified() {
        return verified;
    }

    public @Nullable Instant verifiedAt() {
        return verifiedAt;
    }

    public @NotNull Instant createdAt() {
        return createdAt;
    }
}