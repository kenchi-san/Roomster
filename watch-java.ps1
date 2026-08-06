# A lancer dans un second terminal, en parallele de `mvnw spring-boot:run`.
# Recompile automatiquement vers target/classes des qu'un .java change,
# ce qui declenche le restart automatique de spring-boot-devtools.

$src = Join-Path $PSScriptRoot "src\main\java"
$lastRun = Get-Date

Write-Host "Surveillance de $src (Ctrl+C pour arreter)..."

while ($true) {
    $latest = Get-ChildItem -Path $src -Filter *.java -Recurse |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1

    if ($latest -and $latest.LastWriteTime -gt $lastRun) {
        Write-Host "Changement detecte : $($latest.FullName)"
        & "$PSScriptRoot\mvnw.cmd" compile -q
        $lastRun = Get-Date
    }

    Start-Sleep -Seconds 1
}
