# language: pt-br
Funcionalidade: Criação de Competição
  Como administrador do sistema
  Eu quero poder criar uma nova competição

  Contexto:
    Dado que o usuário é o administrador do sistema
    E está logado no sistema
    E está na tela de criação de competição

  Cenário: Administrador cria uma competição pública com sucesso
    Dado que escolhe a opção de competição pública
    E define a data de início
    E define a duração
    E define se é recorrente ou não
    E define a taxa de corretagem de compra
    E define a taxa de corretagem de venda
    Quando clicar no botão "criar"
    Então o sistema deve criar uma nova competição pública
    E mostrar a tela de sucesso de criação

  Esquema do Cenário: Administrador cria uma competição privada e decide sobre o envio dos convites
    Dado que escolhe a opção de competição privada
    E entra com uma lista de e-mails
    E define a data de início
    E define a duração
    E define se é recorrente ou não
    E define a taxa de corretagem de compra
    E define a taxa de corretagem de venda
    E clica no botão "criar"
    E o sistema cria a nova competição privada e mostra a tela de sucesso com a pergunta se deve enviar os emails convite agora ou não
    Quando o administrador escolhe <opção>
    Então <resultado>

    Exemplos:
      | opção                     | resultado                                                                          |
      | enviar os convites agora  | o sistema envia os e-mails de convite para a lista informada                      |
      | enviar os convites depois | o sistema não envia os e-mails de convite e mantém a competição aguardando o envio |
