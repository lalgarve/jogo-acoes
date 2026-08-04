# language: pt-br
Funcionalidade: Login via link enviado por e-mail
  Como usuário do sistema
  Eu quero fazer login através de um link recebido por e-mail
  Para acessar o sistema sem precisar de senha

  Esquema do Cenário: Novo jogador faz login e confirma entrada na competição
    Dado que o usuário é um novo jogador
    E <situação>
    E clicou no link de login recebido por e-mail
    Quando informa o nome
    E confirma a entrada na competição
    Então o sistema cadastra o jogador
    E o inclui na competição
    E o redireciona para a página da competição

    Exemplos:
      | situação                                             |
      | solicitou entrada em uma nova competição pública     |
      | foi convidado para entrar em uma competição privada  |

  Esquema do Cenário: Jogador cadastrado confirma entrada em competição após solicitação ou convite
    Dado que o usuário é um jogador cadastrado
    E <situação>
    E clicou no link de login recebido por e-mail
    Quando confirma a entrada na competição
    Então o sistema adiciona o jogador na competição
    E o redireciona para a página da competição

    Exemplos:
      | situação                                             |
      | solicitou entrada em uma nova competição pública     |
      | foi convidado para entrar em uma competição privada  |

  Cenário: Jogador cadastrado solicita novamente entrada em competição que já participa
    Dado que o usuário é um jogador cadastrado
    E já participa de uma competição pública
    E solicitou novamente entrada nessa competição
    Quando clica no link de login recebido por e-mail
    Então o sistema o redireciona diretamente para a página da competição

  Cenário: Jogador que participou de uma competição encerrada acessa o link e vê o resultado final
    Dado que o usuário é um jogador cadastrado
    E a competição referenciada no link já está encerrada
    E o jogador participou dessa competição
    Quando clica no link de login recebido por e-mail
    Então o sistema o redireciona para a página da competição
    E exibe o resultado final

  Esquema do Cenário: Jogador com cadastro não finalizado acessa o link de uma competição encerrada e recebe erro
    Dado que o status do jogador é <status>
    E a competição referenciada no link já está encerrada
    Quando clica no link de login recebido por e-mail
    Então o sistema exibe uma mensagem de erro

    Exemplos:
      | status                                                              |
      | solicitou entrada em competição pública e não finalizou o cadastro  |
      | foi convidado para competição privada e não finalizou o cadastro    |

  Cenário: Jogador cadastrado pede link de login
    Dado que o usuário é um jogador cadastrado
    E pediu o link para login
    Quando clica no link de login recebido por e-mail
    Então o sistema o redireciona para a página listando as competições que está participando ou já participou

  Cenário: Administrador pede link de login
    Dado que o usuário é o administrador do sistema
    E pediu o link para login
    Quando clica no link de login recebido por e-mail
    Então o sistema o redireciona para a página de administração
