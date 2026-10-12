# Caderno de testes (spec 05-040): carrega a massa de dados do caderno
# (..\dados\jogo-acoes-massa.sql) no banco do app. Mesmos passos de restaurar-massa.sh:
#   1. confere se o dump foi gerado com a última migration do repositório -- se o repositório
#      tem migration mais nova, recusa e manda regenerar (gerar-massa.sh, no WSL);
#   2. para o app (o Flyway e as sessões não podem estar usando o schema);
#   3. apaga o schema jogo_acoes e carrega o dump;
#   4. confere a massa com ..\dados\verificar-massa.sql.
# Depois, subir o app com:
#   docker compose -f docker-compose.yml -f docker-compose.blackbox.yml up -d app
#
# Uso: .\restaurar-massa.ps1
# Funciona no Windows PowerShell 5.1 e no PowerShell 7. Precisa do Docker Desktop.
$ErrorActionPreference = 'Stop'
# UTF-8 nos textos trocados com programas externos (docker); o padrão do PowerShell 5.1 é ASCII.
$OutputEncoding = [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding $false

$CadernoDir = Split-Path -Parent $PSScriptRoot
$Repo = (Resolve-Path (Join-Path $CadernoDir '../../..')).Path
$Dump = Join-Path $CadernoDir 'dados/jogo-acoes-massa.sql'
$Verificacao = Join-Path $CadernoDir 'dados/verificar-massa.sql'
$Migrations = if ($env:MIGRATIONS_DIR) { $env:MIGRATIONS_DIR } else { Join-Path $Repo 'app/src/main/resources/db/migration-jogo-acoes' }
$Compose = @('compose', '-f', (Join-Path $Repo 'docker-compose.yml'), '-f', (Join-Path $Repo 'docker-compose.blackbox.yml'))
$Psql = $Compose + @('exec', '-T', 'db', 'psql', '-U', 'jogo_acoes_admin', '-d', 'jogo_acoes', '-v', 'ON_ERROR_STOP=1', '-q')

function Invoke-Docker([string[]]$Argumentos, [string]$Entrada) {
    if ($Entrada) { Get-Content -Raw -Encoding UTF8 $Entrada | & docker @Argumentos } else { & docker @Argumentos }
    if ($LASTEXITCODE -ne 0) { throw "docker $($Argumentos -join ' ') falhou (código $LASTEXITCODE)" }
}

Write-Host "==> 1. Conferindo a versão do dump ($Dump)"
$cabecalho = Get-Content -TotalCount 5 -Encoding UTF8 $Dump
$versaoDump = ($cabecalho | Select-String '^-- versao-flyway: (\d+)$').Matches[0].Groups[1].Value
$dataGeracao = ($cabecalho | Select-String '^-- data-geracao: (.+)$').Matches[0].Groups[1].Value
$versaoRepo = Get-ChildItem $Migrations -Name | ForEach-Object { if ($_ -match '^V(\d+)__') { [int]$Matches[1] } } |
    Sort-Object | Select-Object -Last 1
Write-Host "    dump gerado na migration V$versaoDump ($dataGeracao); repositório na V$versaoRepo"
if ([int]$versaoDump -ne $versaoRepo) {
    Write-Host "Erro: o dump é da migration V$versaoDump, mas o repositório já está na V$versaoRepo."
    Write-Host '      Gere a massa de novo com gerar-massa.sh (no WSL).'
    exit 1
}

Write-Host '==> 2. Parando o app'
$apps = & docker ps -q --filter label=com.docker.compose.service=app
if ($apps) { Invoke-Docker (@('stop') + @($apps)) | Out-Null }
Invoke-Docker ($Compose + @('up', '-d', '--wait', 'db'))

Write-Host '==> 3. Apagando o schema jogo_acoes e carregando o dump'
Invoke-Docker ($Psql + @('-c', 'SET client_min_messages = warning; DROP SCHEMA IF EXISTS jogo_acoes CASCADE'))
Invoke-Docker ($Psql + @('-f', '-')) $Dump | Out-Null
Write-Host '    massa carregada'

Write-Host "==> 4. Conferindo a massa ($Verificacao)"
Invoke-Docker ($Psql + @('-f', '-')) $Verificacao
Write-Host 'Pronto. Suba o app com:'
Write-Host '  docker compose -f docker-compose.yml -f docker-compose.blackbox.yml up -d app'
