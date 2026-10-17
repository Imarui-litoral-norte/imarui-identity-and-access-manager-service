param([string]$JavaHome = $env:JAVA_HOME, [string]$Tests = "")
$ErrorActionPreference = 'Stop'
$repoPath = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$containerName = 'iiams-identity-tests-' + [guid]::NewGuid().ToString('N').Substring(0, 10)
$priorDbUrl = $env:IDENTITY_TEST_DB_URL
$priorJavaHome = $env:JAVA_HOME
$containerId = $null
$buildExit = 1
try {
    if ($JavaHome) { $env:JAVA_HOME = $JavaHome }
    $containerId = & docker run --detach --rm --name $containerName --env POSTGRES_USER=identity_test --env POSTGRES_PASSWORD=identity_test --env POSTGRES_DB=identity_test --publish 127.0.0.1::5432 postgres:17
    if ($LASTEXITCODE -ne 0) { throw 'Could not start isolated PostgreSQL.' }
    $ready = $false
    for ($attempt = 0; $attempt -lt 40; $attempt++) {
        & docker exec $containerName pg_isready -U identity_test -d identity_test *> $null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Milliseconds 500
    }
    if (!$ready) { throw 'PostgreSQL was not ready within 20 seconds.' }
    $mapping = (& docker port $containerName 5432/tcp).Trim()
    $port = $mapping.Split(':')[-1]
    $env:IDENTITY_TEST_DB_URL = "jdbc:postgresql://127.0.0.1:$port/identity_test"
    Push-Location $repoPath
    try {
        $mavenArguments = @('-B', 'clean', 'verify')
        if ($Tests) { $mavenArguments += "-Dtest=$Tests" }
        & .\mvnw.cmd @mavenArguments
        $buildExit = $LASTEXITCODE
    }
    finally { Pop-Location }
}
finally {
    if ($containerId) { & docker rm --force $containerName *> $null }
    $env:IDENTITY_TEST_DB_URL = $priorDbUrl
    $env:JAVA_HOME = $priorJavaHome
}
exit $buildExit
