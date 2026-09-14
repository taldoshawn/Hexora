# Arquitetura

## Fronteiras atuais

`app` contém integração Android, Compose, Room, DataStore, SAF e configuração progressiva de permissões. `core:filesystem` é Kotlin/JVM e contém as regras que precisam ser verificáveis sem dispositivo.

Toda referência é um `FileRef(providerId, opaqueId)`. O valor opaco nunca é usado diretamente pela UI para abrir um arquivo. O registry seleciona o provider, o capability manager exige a capacidade e o provider valida novamente o identificador e o root.

## Fluxo de uma escrita

1. A UI envia intenção e uma referência opaca.
2. `CapabilityManager` confirma `WRITE`/`CREATE` no provider atual.
3. `FileOperationEngine` registra o início no journal.
4. O conteúdo é escrito em arquivo temporário com buffer e cancelamento.
5. O stream é sincronizado quando o provider expõe um descritor local.
6. O original é movido para backup.
7. O temporário assume o nome original; uma falha tenta rollback.
8. O journal recebe sucesso, falha ou cancelamento.

## Evolução modular

As próximas fases devem separar providers privilegiados em processos/serviços com permissões mínimas. Parsers de APK, DEX, AXML, ARSC e ELF devem permanecer módulos independentes, com fuzz tests e limites explícitos antes de integrarem a UI.
