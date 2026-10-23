# Preflight, atualização e recuperação de banco

Procedimento para uma futura operação autorizada. Esta entrega executa apenas testes locais/CI
com dados sintéticos. Não acessa VPS, não valida backup de produção e não executa deploy.
Responsável operacional, janela, autorizações e RPO/RTO precisam ser definidos antes da operação.

## Pré-deploy

1. Registrar SHA/imagem de origem e destino, PostgreSQL/clientes, schema, responsável e janela.
   Conferir que o artefato é o mesmo validado no CI. Não misturar binários antigos que desconhecem
   lifecycle/ROOT/M2M com V25. Java 17; persistência validada em PostgreSQL 17.11 no CI anterior
   e 18.4 local. A compatibilidade da versão real de produção deve ser conferida, não presumida.
2. Configurar conexão com secret manager, serviço libpq ou pgpass protegido; TLS e permissões
   mínimas conforme ambiente. Não usar senha/token em argumentos, logs, Git ou exemplos copiados.
   Conferir banco e search_path manualmente antes de qualquer comando operacional.
3. Executar inventário somente leitura com `psql -X --set=ON_ERROR_STOP=1
   "service=iiam-preflight" --file=scripts/authorization/preflight.sql` (uma linha no terminal).
   O serviço libpq deve selecionar o schema correto. O script registra versões/checksums/sucesso,
   estimativas de espaço/linhas, índices e locks; tolera ausência de tabelas ainda não migradas.
   Saída deve permanecer em armazenamento autorizado. Não contém consulta de senha/token/chave.
4. Confrontar versões/checksums com migrations do release. Executar Flyway validate do artefato
   no ensaio e antes de migrar conforme processo aprovado. Não usar repair, baseline automático,
   clean ou editar migrations aplicadas para forçar a atualização. V1–V25 continuam imutáveis.
5. Verificar ROOT ACTIVE e autoridade de recuperação através do avaliador/diagnóstico autorizado,
   não apenas contagem SQL. Confirmar Identity/Tenant, sessões e DENYs. O preflight mostra
   elegibilidade cadastral e duplicatas; não é um segundo algoritmo de autorização.
6. Conferir espaço livre no volume de dados, WAL, temporários e destino do backup com ferramenta
   do host/infra. `pg_database_size` não informa espaço livre. Avaliar locks longos, sessões abertas,
   replicação, autovacuum, espaço para índices e margem de rollback. Não registrar consultas com PII.
7. Aprovar indisponibilidade/janela e suspender novas escritas se o plano exigir. V18 atualiza
   DENYs duplicados e cria unicidade; V19 altera tabela/histórico; V20 cria estruturas/chaves e
   atualiza governança; V21 cria triggers; V22/V24 criam índices; V23 registra autoridade legada;
   V25 substitui guarda. Índices convencionais/ALTER TABLE podem bloquear. Ensaiar no volume real
   autorizado antes da janela. Global lock 810082 é parte da consistência e não deve ser removido.

## Backup consistente

Usar cliente `pg_dump` compatível com o servidor, preferencialmente da mesma versão major.
Não usar cliente mais antigo contra servidor mais novo. Exemplos pressupõem serviços libpq
previamente configurados, conexão segura e destino com permissões restritas:

```text
pg_dump --dbname=service=iiam-backup --format=custom --file=<destino-protegido>/iiam-before-upgrade.dump
pg_restore --list <destino-protegido>/iiam-before-upgrade.dump
```

`pg_dump` fornece snapshot lógico consistente para o banco; não é cópia dos segredos externos,
roles globais, configurações do cluster, arquivos de chave JWT, SMTP ou outras bases. Catalogar
dependências e backup seguro separado de configuração/chaves; dumpall globals contém material
privilegiado e exige procedimento restrito próprio. Não executar esse export nesta entrega.
Usar cópia de banco completo quando o aplicativo ocupa o banco; filtro por schema no ensaio
serve somente ao isolamento de dados de teste e não é recomendação genérica de backup parcial.

Conferir exit code, tamanho e listagem; produzir SHA-256 (`Get-FileHash -Algorithm SHA256` no
Windows ou `sha256sum` no host apropriado). Manifesto: release, versão PostgreSQL/schema,
data, escopo, hash, tamanho e custodiante. Hash detecta alteração, não prova restauração.
Criptografar/transmitir/armazenar com política aprovada, chave separada e acesso auditável.
Verificar cópia externa e retenção do backup conforme governança. Não afirmar essas proteções
como ativas somente porque o aplicativo tem um runbook.

## Ensaio de restauração e migração

1. Criar outra base isolada com o mesmo major PostgreSQL e extensões necessárias. Nunca restaurar
   com `--clean` sobre o banco operacional para testar. Conferir nome/host antes de prosseguir.
2. Restaurar usando credencial de ensaio, destino explícito e falha imediata:

```text
pg_restore --dbname=service=iiam-restore-test --no-owner --no-privileges --exit-on-error --single-transaction <arquivo.dump>
```

3. `--no-owner/--no-privileges` é usado no ensaio para separar ambiente; antes da operação real,
   restaurar/revalidar proprietários, grants, extensões e roles com plano aprovado. Não conceder
   superusuário ao aplicativo. Volume grande pode exigir outra estratégia de restore revisada;
   não trocar atomicidade por paralelismo sem definir tratamento de falha parcial.
4. Validar Flyway na versão de origem, integridade/FKs/índices, contagens e fingerprints estáveis
   por tabela. Confirmar IDs, bindings, composição de Roles, assignments, DENYs, delegações,
   credenciais/revogações/nonces e históricos. Não publicar conteúdo de tabelas em logs.
5. Aplicar migrations pelo mesmo artefato/processo que será usado na janela. O aplicativo já
   executa Flyway no startup; iniciar uma única instância controlada, readiness bloqueada até
   sucesso, schema validado pelo Hibernate e health. Não iniciar uma segunda rota de migração
   divergente. Nos testes o mesmo diretório SQL é executado pela API Flyway.
6. Simular falha controlada no ambiente de ensaio, verificar rollback e restaurar o backup em
   outra base. Não validar recuperação apenas abrindo a listagem do dump.
7. Aplicar upgrade também na restauração, executar regressão de Identity/Authentication/
   Authorization e smoke autorizado: PDP allow/deny, M2M replay/revogação, sessão revogada,
   ROOT protegido, auditoria persistida e rollback. Não criar concessões para fazer smoke passar.

## Interrupção e recuperação

Parar avanço se houver checksum divergente, migration falha, perda de vínculo/ID, ROOT sem
recuperabilidade, ganho indevido, auditoria indisponível, falta de espaço ou lock excedendo
a janela aprovada. Isolar tráfego, preservar logs/estado do Flyway e investigar antes de repetir.
Não apagar linhas de histórico nem desabilitar constraints/triggers para contornar falha.

Migrations PostgreSQL deste conjunto usam transações. Falha na migration atual normalmente
reverte aquela migration; migrations anteriores já commitadas permanecem aplicadas. Não afirmar
rollback de toda a cadeia nem down migration automático. Conferir estado real após erro.
Preferir correção progressiva revisada quando compatível. Restauração exige aprovação operacional:
parar escritores, preservar backup/estado do incidente, restaurar em destino novo, validar dados,
aplicar binário compatível com o schema restaurado e só então trocar destino/reabrir tráfego.
Definir como tratar escritas posteriores ao backup (PITR/WAL ou reconciliação) e impacto de RPO.
Não apontar binário novo para schema antigo sem validar compatibilidade.

## Evidência executável desta rodada

`AuthorizationRecoveryRehearsalTest` cria schema V20 populado em banco local de teste, faz dump
custom real, calcula SHA-256, aplica V21–V25, executa o preflight, simula erro SQL com rollback
de assignment+journal, restaura em **outra base**, valida V20, reaplica migrations e compara
fingerprints de 12 tabelas. Inclui Role excluída histórica, DENY/delegação, ROOT, chave pública
e nonce. Confere append-only e rejeição da saída do último ROOT para PENDING.
[Resultado estruturado](round-4-recovery-rehearsal.json). Não simula falha de disco/rede,
PITR, cifragem/custódia remota nem restauração de produção; não define RPO/RTO.

Para repetir somente o ensaio em Windows, após disponibilizar PostgreSQL isolado na porta local:

```powershell
$env:JAVA_HOME = '<JDK-17>'
$env:IDENTITY_TEST_DB_URL = 'jdbc:postgresql://127.0.0.1:<porta>/identity_test'
$env:IIAM_PG_BIN = '<PostgreSQL-mesmo-major>\bin'
.\mvnw.cmd -B '-Dtest=AuthorizationRecoveryRehearsalTest' test
```

Base/usuário/senha `identity_test` são exclusivos da fixture. O teste recusa URLs fora do
loopback e banco esperado, cria nomes aleatórios e só remove seus próprios schemas/bases.
CI usa clientes dentro do serviço PostgreSQL para evitar incompatibilidade de versão.
Arquivos `.dump` e logs temporários ficam em `target`, sem upload do arquivo de backup.
