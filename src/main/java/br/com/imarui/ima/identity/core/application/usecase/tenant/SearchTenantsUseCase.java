package br.com.imarui.ima.identity.core.application.usecase.tenant;

import br.com.imarui.ima.identity.core.application.port.TenantDirectoryAccess;
import br.com.imarui.ima.identity.core.application.result.tenant.TenantResult;
import br.com.imarui.ima.identity.core.domain.enums.tenant.TenantStatus;
import br.com.imarui.ima.identity.core.repository.TenantRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SearchTenantsUseCase {
    public record Result(List<TenantResult> items, long total, int page, int size) {}
    private final TenantDirectoryAccess access;
    private final TenantRepository tenants;

    @Transactional(readOnly = true)
    public Result execute(String query, String status, int page, int size) {
        String term = query == null ? "" : query.trim();
        if (term.length() > 150 || page < 0 || page > 100000 || size < 1 || size > 100)
            throw new IllegalArgumentException("Invalid tenant search or pagination.");
        TenantStatus filter = "ALL".equals(status) ? null : TenantStatus.valueOf(status);
        var result = tenants.search(access.readableTenantIds(), filter, term, page, size);
        return new Result(result.items().stream().map(TenantResult::from).toList(), result.total(), result.page(), result.size());
    }
}
