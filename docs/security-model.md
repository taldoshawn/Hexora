# Modelo de segurança

## Ativos

- conteúdo e metadados de arquivos do usuário;
- permissões SAF e All files;
- futuras sessões Shizuku, ADB e root;
- keystores de assinatura;
- journal e backups de recuperação.

## Fronteiras de confiança

- UI e parâmetros de navegação: não confiáveis;
- nomes, paths, MIME, extensões e magic bytes: não confiáveis;
- archives e formatos executáveis: hostis por padrão;
- providers Android/documentos remotos: podem falhar ou mudar durante a operação;
- plugins e respostas de IA: não confiáveis e sem privilégios implícitos.

## Controles implementados

- allowlist de roots e normalização de paths;
- bloqueio de nomes com separadores, controle/NUL e `..`;
- rejeição de travessia por symlink no provider local;
- tokens SAF opacos emitidos apenas para árvores concedidas e descendentes observados;
- streaming, limites de entrada/profundidade e cancelamento cooperativo;
- extração sem overwrite, canonical containment e limites de expansão/ratio;
- temporário + sync + backup + rollback para writes;
- cleartext de rede desativado e nenhuma telemetria.

## Limitações conhecidas da alpha

O filesystem Android e providers SAF não fornecem atomicidade idêntica. Backups são mantidos quando um swap verdadeiramente atômico não pode ser garantido. Operações POSIX, lixeira com restore, Shizuku, ADB e root ainda não estão habilitadas.
