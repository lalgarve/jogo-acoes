# language: pt-br
Funcionalidade: Criação de Competição
  Como administrador do sistema
  Eu quero poder criar uma nova competição

  Cenário: Administrador cria uma competição pública com sucesso
    Dado que o usuário é o administrador do sistema
    E está logado no sistema
    E está na tela de criação de competição
    E escolhe a opção de competição pública
    E define a data de início
    E define a duração
    E define se é recorrente ou não
    E define a taxa de corretagem de compra
    E define a taxa de corretagem de venda
    Quando clicar no botão "criar"
    Então o sistema deve criar uma nova competição pública
    E mostrar a tela de sucesso de criação

  Cenário: Criação de Competição Privada
    Dado que o usuário é o administrador do sistema
    E está logado no sistema
    E está na tela de criação de competição
    E escolhe a opção de competição privada
    E entra com uma lista de e-mails
    E define a data de início
    E define a duração
    E define se é recorrente ou não
    E define a taxa de corretagem de compra
    E define a taxa de corretagem de venda
    Quando clicar no botão "criar"
    Então o sistema deve criar uma nova competição privada
    E mostrar tela de sucesso de criação com a pergunta se deve enviar os emails convite agora ou não
