package br.com.imarui.ima.authorization.api.http;
import br.com.imarui.ima.authorization.core.application.usecase.*;
import br.com.imarui.ima.authorization.core.repository.CatalogInspectionRepository;
import br.com.imarui.ima.authorization.core.domain.model.PermissionManifest;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/authorization/admin/catalog/owners")
@RequiredArgsConstructor
@SecurityRequirement(name="bearerAuth")
public class CatalogInspectionController {
  private final CatalogInspectionQueries catalogs;
  @GetMapping public AuthorizationQueries.Page<CatalogInspectionRepository.Owner> owners(
      @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return catalogs.owners(page,size); }
  @GetMapping("/{service}") public CatalogInspectionRepository.Owner owner(@PathVariable String service) { return catalogs.owner(service); }
  @GetMapping("/{service}/revisions") public AuthorizationQueries.Page<CatalogInspectionRepository.Revision> revisions(@PathVariable String service,
      @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return catalogs.revisions(service,page,size); }
  @GetMapping("/{service}/revisions/{version}") public PermissionManifest manifest(@PathVariable String service,@PathVariable long version) { return catalogs.manifest(service,version); }
}
