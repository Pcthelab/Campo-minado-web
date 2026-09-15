param([int]$Porta = 8080)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    if (-not (Get-Command javac -ErrorAction SilentlyContinue)) {
        throw 'Instale um JDK 21 ou superior e disponibilize java e javac no PATH.'
    }
    New-Item -ItemType Directory -Force -Path out | Out-Null
    $fontes = @(Get-ChildItem -Path src -Filter *.java -Recurse | ForEach-Object { $_.FullName })
    & javac -encoding UTF-8 -d out @fontes
    if ($LASTEXITCODE -ne 0) { throw 'A compilação falhou.' }
    & java -cp out visao.TelaPrincipal $Porta
    if ($LASTEXITCODE -ne 0) { throw 'Não foi possível iniciar o jogo. Confira se a porta está livre.' }
} finally {
    Pop-Location
}
