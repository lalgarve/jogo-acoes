# language: pt-br
Funcionalidade: Solicitar entrada competição pública
  Como jogador
  Eu quero solicitar entrada em uma competição

  Cenário: jogador não cadastrado solicita entrada em competição pública
    Dado que o jogador é não cadastrado
    E clicou em Entrar em uma Competição
    E clicou em uma competição pública
    E digitou um e-mail válido
    E passou no teste de não ser robô
    Então o sistema adiciona o e-mail na lista de solicitantes e envia o link para finalização de cadastro por e-mail

  Cenário: jogador cadastrado não logado solicita entrada em competição pública
    Dado que o jogador é cadastrado e não logado
    E clicou em Entrar em uma Competição
    E clicou em uma competição pública
    E digitou um e-mail válido
    E passou no teste de não ser robô
    Então o sistema adiciona o e-mail personalizado com o nome na lista de solicitantes e envia o link para finalização de cadastro por e-mail

  Cenário: jogador cadastrado logado solicita entrada em competição pública
    Dado que o jogador é cadastrado e está logado
    E clicou em Entrar em uma Competição
    E clicou em uma competição pública
    E confirmou a entrada na competição
    Então o sistema adiciona o jogador na competição pública e o redireciona para a página da competição
