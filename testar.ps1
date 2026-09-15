$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    New-Item -ItemType Directory -Force -Path out/test | Out-Null
    $fontes = @(Get-ChildItem -Path src,test -Filter *.java -Recurse | ForEach-Object { $_.FullName })
    & javac -encoding UTF-8 -d out/test @fontes
    if ($LASTEXITCODE -ne 0) { throw 'A compilação dos testes falhou.' }
    & java -cp out/test modelo.TabuleiroTeste
    if ($LASTEXITCODE -ne 0) { throw 'Os testes falharam.' }
} finally {
    Pop-Location
}
