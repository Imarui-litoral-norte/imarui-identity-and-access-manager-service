package br.com.imarui.ima.identity.core.domain.model.identityapplication;

import br.com.imarui.ima.identity.core.domain.enums.identity.IdentityKind;
import br.com.imarui.ima.identity.core.domain.model.identity.service.ServiceDescription;
import br.com.imarui.ima.identity.core.domain.model.identity.service.ServiceName;

import java.util.Objects;

public record ServiceIdentityApplicationDetails(
        ServiceName name,
        ServiceDescription description
) implements IdentityApplicationDetails {

    public ServiceIdentityApplicationDetails {
        Objects.requireNonNull(name, "name cannot be null");
    }

    @Override
    public IdentityKind kind() {
        return IdentityKind.SERVICE;
    }
}
