# Caderno de testes (spec 05-040), roteiro 3: faz o login de um jogador como se fosse o
# dispositivo <número>, sem navegador. Mesmos passos e mesma saída de login-jogador.sh:
#   1. lê os cabeçalhos Sec-CH-UA* de ..\dispositivos\<número>.json;
#   2. pede o link de login (POST /login-requests);
#   3. lê o e-mail no SES do LocalStack (GET /_aws/ses) -- se ainda não chegou, tenta de novo a
#      cada 2 s, até 5 vezes;
#   4. faz o login com o token do link (GET /login-links/{token}) mandando os cabeçalhos do
#      dispositivo, e grava o cookie de sessão em ..\cookie<número>.
#
# Uso:     .\login-jogador.ps1 <número> <email>
# Exemplo: .\login-jogador.ps1 1 success+seed-c6-jogador-1@simulator.amazonses.com
# Funciona no Windows PowerShell 5.1 e no PowerShell 7. As variáveis de ambiente API_BASE_URL e
# LOCALSTACK_URL mudam os endereços padrão.
param(
    [Parameter(Mandatory = $true)][string]$Numero,
    [Parameter(Mandatory = $true)][string]$Email
)
$ErrorActionPreference = 'Stop'
# UTF-8 nos textos trocados com programas externos (docker); o padrão do PowerShell 5.1 é ASCII.
$OutputEncoding = [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding $false

$CadernoDir = Split-Path -Parent $PSScriptRoot
$Dispositivo = Join-Path $CadernoDir "dispositivos/$Numero.json"
$Cookie = Join-Path $CadernoDir "cookie$Numero"
$Api = if ($env:API_BASE_URL) { $env:API_BASE_URL } else { 'http://localhost:8080/api' }
$LocalStack = if ($env:LOCALSTACK_URL) { $env:LOCALSTACK_URL } else { 'http://localhost:4566' }

if (-not (Test-Path $Dispositivo)) { Write-Error "Dispositivo $Numero não existe ($Dispositivo)." }

function Get-EmailsDoJogador {
    $resposta = Invoke-RestMethod -UseBasicParsing -Uri "$LocalStack/_aws/ses"
    @($resposta.messages | Where-Object { $_.Destination.ToAddresses -contains $Email })
}

Write-Host "==> 1. Lendo os cabeçalhos do dispositivo $Numero ($Dispositivo)"
$json = Get-Content -Raw -Encoding UTF8 $Dispositivo | ConvertFrom-Json
$Cabecalhos = @{}
foreach ($propriedade in $json.PSObject.Properties) {
    Write-Host "    $($propriedade.Name): $($propriedade.Value)"
    $Cabecalhos[$propriedade.Name] = $propriedade.Value
}

$Anteriores = @(Get-EmailsDoJogador | ForEach-Object { $_.Id })

Write-Host "==> 2. Pedindo o link de login de $Email (POST $Api/login-requests)"
$corpo = @{ email = $Email } | ConvertTo-Json -Compress
$resposta = Invoke-WebRequest -UseBasicParsing -Method Post -Uri "$Api/login-requests" `
    -ContentType 'application/json' -Body $corpo
Write-Host "    resposta: $($resposta.StatusCode)"
if ($resposta.StatusCode -ne 202) { Write-Error 'Esperava 202.' }

Write-Host "==> 3. Lendo o e-mail no LocalStack (GET $LocalStack/_aws/ses)"
$Link = $null
foreach ($tentativa in 1..5) {
    $novo = Get-EmailsDoJogador | Where-Object { $Anteriores -notcontains $_.Id } | Select-Object -Last 1
    if ($novo -and $novo.TemplateData) {
        $Link = ($novo.TemplateData | ConvertFrom-Json).link
    }
    if ($Link) {
        Write-Host "    tentativa ${tentativa}: e-mail encontrado, link $Link"
        break
    }
    Write-Host "    tentativa ${tentativa}: o e-mail ainda não chegou, tentando de novo em 2 s"
    Start-Sleep -Seconds 2
}
if (-not $Link) { Write-Error "Nenhum e-mail novo para $Email depois de 5 tentativas." }
$Token = ($Link -split '/login-links/')[1].Split('/')[0]

Write-Host "==> 4. Fazendo o login como dispositivo $Numero (GET $Api/login-links/$Token)"
$resposta = Invoke-WebRequest -UseBasicParsing -Uri "$Api/login-links/$Token" -Headers $Cabecalhos
$sessao = @($resposta.Headers['Set-Cookie']) -join ';' -split ';' |
    ForEach-Object { $_.Trim() } | Where-Object { $_ -like 'SESSION=*' } | Select-Object -Last 1
Write-Host "    resposta: $($resposta.StatusCode) $($resposta.Content)"
if ($resposta.StatusCode -ne 200 -or -not $sessao) { Write-Error 'O login não abriu sessão.' }
Set-Content -Path $Cookie -Value $sessao -Encoding ASCII
Write-Host "    cookie de sessão gravado em $Cookie"
