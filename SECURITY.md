# Política de segurança

## Reportar uma vulnerabilidade

Não abra uma issue pública com detalhes exploráveis. Use o formulário privado de [Security Advisory do Hexora](https://github.com/taldoshawn/Hexora/security/advisories/new).

Inclua, quando possível:

- versão/commit afetado;
- versão do Android e modo de acesso (Normal, SAF, All files, Shizuku, ADB ou Root);
- passos mínimos para reproduzir;
- impacto e arquivos que podem ser acessados/modificados;
- prova de conceito inofensiva.

Não inclua arquivos pessoais, credenciais, chaves privadas ou APKs sem direito de redistribuição.

## Escopo prioritário

- escape de roots, path/symlink traversal e TOCTOU;
- ZIP Slip, bombas de descompressão e parsers malformados;
- execução de comandos ou elevação de privilégio não autorizada;
- vazamento de keystore, token ou conteúdo privado;
- permissão excessiva de plugins ou futuras ferramentas de IA;
- corrupção/perda de arquivo durante operações transacionais.

## Princípios

Referências recebidas da UI não são autorização. Toda operação é revalidada no provider, privilégios são progressivos e root nunca é concedido implicitamente. Conteúdo de arquivos e mensagens de ferramentas são dados não confiáveis.

Versões alpha recebem correções somente na branch `main` até a primeira release estável.
