package br.com.imarui.ima.identity.core.domain.model.identityapplication;

import br.com.imarui.ima.identity.core.domain.enums.identity.IdentityKind;

public sealed interface IdentityApplicationDetails
        permits PersonIdentityApplicationDetails,
                LegalEntityIdentityApplicationDetails,
                ServiceIdentityApplicationDetails {

    IdentityKind kind();
}
