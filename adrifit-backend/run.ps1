# Arranca el backend.
#   .\run.ps1           -> PostgreSQL (base de datos "adrifit")
#   .\run.ps1 -Local    -> H2 en fichero + datos de demo (no necesita PostgreSQL)
param([switch]$Local)

if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    $jdk = Get-ChildItem 'C:\Program Files\Java', 'C:\Program Files\Eclipse Adoptium' -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match '^(jdk-?)?21' -and (Test-Path "$($_.FullName)\bin\java.exe") } |
        Select-Object -First 1
    if (-not $jdk) { Write-Error 'No se encuentra un JDK 21. Instálalo o define JAVA_HOME.'; exit 1 }
    $env:JAVA_HOME = $jdk.FullName
}
Write-Host "Usando JAVA_HOME=$env:JAVA_HOME"

Set-Location $PSScriptRoot

# Private settings (DB_PASSWORD, JWT_SECRET, TRAINER_PASSWORD...) from .env — never committed.
# Copy .env.example to .env and fill it in. Variables already set in the environment win.
$envFile = Join-Path $PSScriptRoot '.env'
if (Test-Path $envFile) {
    Get-Content $envFile | Where-Object { $_ -match '^\s*[A-Za-z_][A-Za-z0-9_]*\s*=' } | ForEach-Object {
        $name, $value = $_ -split '=', 2
        $name = $name.Trim()
        if (-not [Environment]::GetEnvironmentVariable($name)) {
            [Environment]::SetEnvironmentVariable($name, $value.Trim().Trim('"'), 'Process')
        }
    }
    Write-Host 'Variables cargadas de .env'
} else {
    Write-Warning 'No hay .env: copia .env.example a .env y rellena DB_PASSWORD y JWT_SECRET.'
}
if ($Local) {
    & .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
} else {
    & .\mvnw.cmd spring-boot:run
}
