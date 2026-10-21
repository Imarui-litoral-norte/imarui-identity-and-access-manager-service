# Authentication V1 — IIAMS-39

O IIAM autentica Identity UUID e mantém credenciais, sessões e refresh tokens em PostgreSQL. Identity conserva ownership de nickname, documentos, contato e lifecycle. Authorization fornece somente catálogo/roles persistidos e a integração básica de authorities; Platform compõe segurança HTTP, assinatura e operação.

## Decisões desta rodada

- Fonte Jeep Club master `9ad04a71e50917e0a53c1c4855ea6770730bd8b9`; base IIAM main `687fb0a1a9dabed772dba26a08e14fcd9c6ee684`. Ambos conferidos por `git ls-remote` antes do desenvolvimento.
- Jira consultado exclusivamente pelo Rovo Legacy: IIAMS-39–80 (IIAMS-80 somente integração futura), IIAMS-5, IIAMS-12 e IIAMS-38. Regras definitivas V1 prevalecem sobre pendências antigas.
- PERSON: nickname ou CPF; LEGAL_ENTITY: nickname ou CNPJ; SERVICE: nickname. Login nunca utiliza e-mail. Kind é obrigatório.
- Nickname pertence a Identity; trim/lowercase, 3–64 caracteres ASCII `a-z0-9_.-`, ao menos uma letra. Documentos e e-mail não são nicknames. Unicidade real por kind, com índice PostgreSQL, constraints e locks de cadastro. Atualização por titular/IDENTITY_MANAGE, sem alteração de roles.
- E-mail válido e nickname são obrigatórios para PERSON, LEGAL_ENTITY e SERVICE, inclusive bootstrap ROOT. E-mail não possui unicidade: pode repetir no mesmo kind e entre kinds. Recuperação resolve kind + nickname/documento e usa somente o contato daquela Identity. Não há recuperação alternativa de SERVICE sem contato.
- Primeira ativação depende de convite e senha. Aprovação permanece PENDING. O endpoint antigo de ativação não contorna a prova do primeiro acesso. Reativação administrativa de uma Identity previamente ativada preserva sua semântica.
- Alteração humana posterior ao pedido inicial: o usuário solicitou bootstrap ROOT igual ao Jeep e permissions funcionais para @PreAuthorize. Por isso a role ROOT real, persistida, recebe o catálogo IIAM e é associada somente à Identity inicial pelo bootstrap controlado. Não existe permission fictícia ROOT, wildcard, acesso por nickname root ou concessão por Affiliation.
- A associação IdentityRole PLATFORM desta V1 não é o modelo definitivo de RBAC. Não há endpoints de gestão incompleta de Authorization. Escopos TENANT e decisão por recurso pertencem à próxima rodada.

## Navegação

- [Contrato web e JWT/JWKS](web-and-jwt.md)
- [Operação, bootstrap, migrations e recuperação](operations.md)
- [Matriz completa de auditoria/reutilização](reuse-matrix.md)
- [Relatório por Story e subtask](delivery-report.md)
- [Evidência de validação](validation.md)

## Integração entre módulos

Authentication resolve/locka Identity por contrato mínimo, não por JPA de outro módulo. O adapter consumidor chama a API de módulo publicada por Identity. A ativação da senha e da Identity usa a mesma transação. O outbox permanece de ownership Identity e é consumido por API deliberada, sem importar a entidade JPA no core Authentication.

Session/RefreshToken, seus IDs técnicos Long, mappers e adapters existentes foram preservados. Session ganhou Identity UUID, SID UUID público e instante de revogação; os IDs técnicos não representam usuários. Credential e Challenge têm adapters próprios. Nenhum User paralelo, dado de Memberships ou cópia de cadastro foi criado.

O próprio IIAM valida sessão/estado/cutoff e consulta permissions persistidas após validar a assinatura. Claims `permissions` não concedem authorities. Consumidores futuros validam o JWT localmente e criam Actor runtime com `sub`/`sid`, sem imports de domínio IIAM, sem objeto Actor transmitido no login e sem chave privada.

## Segunda rodada do PR #2

Regras atuais de contato substituem decisões anteriores. Novos links coexistem até expiração ou consumo bem-sucedido de um link da mesma Identity/finalidade. A fila SMTP permite no máximo cinco tentativas totais, com FAILED terminal. Logout compara Bearer/cookie antes dos efeitos. Ver [matriz F-01–F-09](delivery-report.md#segunda-rodada--findings). IIAM continua independente; Gateway é a futura [IIAMS-80](https://logimarui.atlassian.net/browse/IIAMS-80).
