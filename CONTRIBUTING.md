# Contribuindo com o Hexora

Obrigado por ajudar a construir um toolkit Android seguro e aberto.

## Antes de enviar mudanças

1. Crie uma branch curta a partir de `main`.
2. Preserve as fronteiras `FileAccessProvider` e `CapabilityManager`.
3. Nunca autorize uma operação somente porque a UI mostrou um botão.
4. Não adicione secrets, keystores, APKs proprietários ou código copiado de aplicativos fechados.
5. Inclua testes de sucesso e abuso para parsers, caminhos, arquivos compactados e operações privilegiadas.
6. Execute:

```bash
./gradlew lint test assembleDebug assembleRelease
```

## Commits

Use mensagens curtas e coerentes, por exemplo:

- `feat: add safe archive extraction`
- `fix: reject symlink traversal in local provider`
- `test: cover malformed zip metadata`
- `ci: publish debug apk artifact`

## Definition of done

Uma feature deve ter implementação, estados de loading/erro, cancelamento quando pesado, revalidação de capacidade na execução, testes relevantes e build verde. Mockups e botões sem backend não contam como implementação.

## Dependências

Prefira dependências open source mantidas e com licença compatível com GPL-3.0-or-later. Fixe versões, revise advisories e explique dependências grandes ou que processem formatos não confiáveis.
