# Hexora Plugin SDK — contrato preliminar

O SDK ainda não está publicado. Este documento define invariantes que futuras APIs não podem quebrar.

- Plugins declaram capacidades específicas: handler, syntax, archive, network, preview ou tool.
- Nenhum plugin recebe filesystem, rede, Shizuku, ADB ou root por padrão.
- O executor revalida cada chamada; consentimento de instalação não autoriza operações futuras irrestritas.
- Plugins rodam fora do processo principal sempre que tecnicamente possível.
- Entradas e respostas são limitadas por tamanho/tempo e tratadas como não confiáveis.
- Assinatura, origem, versão e permissões ficam visíveis ao usuário.
- Revogação remove tokens/sessões imediatamente.
- O SDK não aceita shell strings para operações estruturadas de arquivo.

A primeira versão será publicada somente junto de testes de isolamento, revogação e abuso.
