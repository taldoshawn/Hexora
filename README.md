# Hexora

Hexora é um workspace Android open source para engenharia de arquivos. O projeto começa por um gerenciador dual-pane seguro e evolui, por fases, para toolkit de APK/DEX/Smali, editor binário, terminal, armazenamento remoto e integração local controlada com IA.

> **Estado:** `0.1.0-alpha01`. Esta é a primeira implementação funcional da Fase 1, não uma alegação de paridade completa com ferramentas maduras. Recursos que ainda não existem aparecem como planejados, nunca como botões fictícios.

## O que já funciona

- Android 11+ (`minSdk 30`), Kotlin e Jetpack Compose;
- explorador adaptativo com dois painéis, estado e histórico independentes;
- acesso ao workspace privado, SAF e opt-in de “Todos os arquivos”;
- copiar e mover entre providers com streaming, progresso, pausa e cancelamento;
- criar, renomear e excluir com validação novamente na camada de execução;
- abstração central `FileAccessProvider` e decisões por `CapabilityManager`;
- editor de texto com encoding, busca, replace, regex, destaque básico e backup transacional;
- editor hexadecimal paginado com offset de 64 bits, busca HEX/UTF-8, overwrite, undo/redo e bookmarks;
- navegação de ZIP/APK/JAR e extração protegida contra ZIP Slip, traversal e bombas de descompressão;
- hashes em streaming e busca recursiva cancelável;
- Room para favoritos/recentes/journal e DataStore para preferências e raízes SAF;
- CI que executa lint, testes, debug/release build e publica APKs como Artifacts.

## Capturas de tela

Capturas reais serão adicionadas após a primeira validação em dispositivo. O projeto deliberadamente não usa mockups como se fossem screenshots do aplicativo compilado.

## Compilar

Requisitos:

- JDK 17;
- Android SDK Platform 36 e Build Tools 36.0.0;
- conexão com Google Maven e Maven Central na primeira compilação.

```bash
git clone https://github.com/taldoshawn/Hexora.git
cd Hexora
./gradlew lint test assembleDebug
```

O APK debug será criado em `app/build/outputs/apk/debug/app-debug.apk`.

## APK pelo GitHub Actions

O workflow `.github/workflows/android.yml` roda em pushes e pull requests para `main`, além de execução manual. Após sucesso:

1. abra **Actions → Android CI**;
2. selecione a execução;
3. baixe `Hexora-APK-debug`;
4. o ZIP do Artifact contém `Hexora-debug.apk`.

Um release sem assinatura também é compilado para validar R8. Para um release assinado, configure os seguintes Actions Secrets; nenhum deles pertence ao repositório:

- `HEXORA_KEYSTORE_BASE64`;
- `HEXORA_KEYSTORE_PASSWORD`;
- `HEXORA_KEY_ALIAS`;
- `HEXORA_KEY_PASSWORD`.

## Arquitetura

```text
app (Android/Compose/Room/DataStore)
└── core:filesystem (Kotlin/JVM)
    ├── provider + capability boundary
    ├── transactional operation engine
    ├── safe path/archive policies
    └── streaming text/hex/search/hash services
```

O módulo de núcleo não depende do Android. Isso mantém regras críticas testáveis em JVM e evita acoplar autorização à interface. Veja [Arquitetura](docs/architecture.md) e [Modelo de segurança](docs/security-model.md).

## Modos de acesso

| Modo | Estado | Limite de confiança |
|---|---|---|
| Normal | Implementado | Apenas roots privados configurados |
| SAF | Implementado | Somente árvores escolhidas pelo usuário; referências internas opacas |
| All files | Implementado, opt-in | Ainda sujeito ao sandbox e às proteções do Android |
| Shizuku | Fase 2 | Não será tratado como root |
| Wireless ADB | Fase 2 | Exigirá pareamento/autorização oficial |
| Root | Fase 2 | Nunca automático; menor privilégio por operação |

## Segurança e privacidade

- sem conta, anúncios, trackers ou telemetria escondida;
- relatórios de falha serão opt-in;
- nenhum comando privilegiado é montado por concatenação de input;
- referências de arquivo são revalidadas no provider no momento da execução;
- operações de escrita usam temporário, sync, backup e rollback quando suportado;
- arquivos grandes usam paginação ou streaming, não `readBytes()` irrestrito;
- conteúdo de arquivo é sempre dado não confiável.

Para reportar uma vulnerabilidade, consulte [SECURITY.md](SECURITY.md).

## Plugins

O SDK de plugins pertence à Fase 5 e ainda não é uma API estável. As fronteiras e regras de permissão propostas estão documentadas em [docs/plugin-sdk.md](docs/plugin-sdk.md); plugins nunca receberão root implicitamente.

## Contribuir

Leia [CONTRIBUTING.md](CONTRIBUTING.md) e [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md). Ao contribuir, mantenha a licença `GPL-3.0-or-later` e não inclua código, assets ou textos proprietários de outros aplicativos.

## Licença

Copyright © 2026 Hexora contributors. Distribuído sob a GNU General Public License v3.0 ou posterior. Consulte [LICENSE](LICENSE).
