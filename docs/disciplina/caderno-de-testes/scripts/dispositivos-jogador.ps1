# Caderno de testes (spec 05-040), roteiro 3: lista os dispositivos em que o jogador está
# logado (GET /sessions), usando o cookie que login-jogador gravou para o dispositivo <número>.
# Mesma saída de dispositivos-jogador.sh.
#
# Uso: .\dispositivos-jogador.ps1 <número>
# Funciona no Windows PowerShell 5.1 e no PowerShell 7. API_BASE_URL muda o endereço padrão.
param(
    [Parameter(Mandatory = $true)][string]$Numero
)
$ErrorActionPreference = 'Stop'
# UTF-8 nos textos trocados com programas externos (docker); o padrão do PowerShell 5.1 é ASCII.
$OutputEncoding = [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding $false

$CadernoDir = Split-Path -Parent $PSScriptRoot
$Cookie = Join-Path $CadernoDir "cookie$Numero"
$Api = if ($env:API_BASE_URL) { $env:API_BASE_URL } else { 'http://localhost:8080/api' }

if (-not (Test-Path $Cookie)) { Write-Error "$Cookie não existe -- rode antes .\login-jogador.ps1 $Numero <email>." }

Write-Host "==> Listando os dispositivos com o cookie do dispositivo $Numero (GET $Api/sessions)"
try {
    $resposta = Invoke-WebRequest -UseBasicParsing -Uri "$Api/sessions" `
        -Headers @{ Cookie = (Get-Content -Raw $Cookie).Trim() }
} catch {
    $status = [int]$_.Exception.Response.StatusCode
    Write-Host "    resposta: $status"
    Write-Host "$($_.ErrorDetails.Message)"
    Write-Host '    a sessão deste dispositivo não está mais ativa'
    exit 1
}
Write-Host "    resposta: $($resposta.StatusCode)"
$sessoes = @([System.Text.Encoding]::UTF8.GetString($resposta.RawContentStream.ToArray()) | ConvertFrom-Json)
Write-Host "    logado em $($sessoes.Count) dispositivo(s):"
foreach ($s in $sessoes) {
    $este = if ($s.current) { ' (este)' } else { '' }
    # O PowerShell 7 converte a data do JSON em DateTime; o 5.1 deixa o texto como veio.
    $desde = if ($s.createdAt -is [datetime]) { $s.createdAt.ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ss.ffffffZ') } else { $s.createdAt }
    Write-Host "    - sessão $($s.id): $($s.deviceLabel), desde $desde$este"
}
