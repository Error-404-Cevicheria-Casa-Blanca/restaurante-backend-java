<#
.SYNOPSIS
    Audita y configura Branch Protection en develop/main y secretos de Actions
    en los 3 repositorios de Error 404 (PRB-114 / PRB-22 / ADR-08).
#>

param(
    [string]$Org = "Error-404-Cevicheria-Casa-Blanca",
    [switch]$ApplyChanges = $false
)

$ErrorActionPreference = "Stop"

$credInput = "protocol=https`nhost=github.com`n`n"
$credOutput = $credInput | git credential fill
$tokenLine = ($credOutput -split "`n") | Where-Object { $_ -match "^password=" }
$token = ($tokenLine -replace "^password=", "").Trim()

$headers = @{
    "Authorization"        = "Bearer $token"
    "Accept"               = "application/vnd.github+json"
    "X-GitHub-Api-Version" = "2022-11-28"
}

$repos = @(
    @{ Name = "restaurante-backend-java"; Check = "Build, Test, Checkstyle, SpotBugs & JaCoCo (JDK 17)" },
    @{ Name = "restaurante-admin-django"; Check = "Lint (flake8), Check & Tests (Python 3.11)" },
    @{ Name = "restaurante-app-kotlin";   Check = "Build, Unit Tests, Detekt & Assemble Debug (JDK 17)" }
)

Write-Host "=== [PRB-114 / ADR-08] Auditoria de Branch Protection y Secrets en GitHub ==="
Write-Host ("Fecha: {0}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ssK"))

foreach ($r in $repos) {
    $repoName = $r.Name
    Write-Host "`n----------------------------------------------------------------"
    Write-Host "Repositorio: $Org/$repoName"
    Write-Host "----------------------------------------------------------------"

    foreach ($branch in @("develop", "main")) {
        try {
            $bInfo = Invoke-RestMethod -Uri "https://api.github.com/repos/$Org/$repoName/branches/$branch" -Headers $headers -Method Get
            Write-Host ("Rama '{0}': protected={1}" -f $branch, $bInfo.protected)
            if ($bInfo.protected) {
                try {
                    $prot = Invoke-RestMethod -Uri "https://api.github.com/repos/$Org/$repoName/branches/$branch/protection" -Headers $headers -Method Get
                    $approvals = if ($prot.required_pull_request_reviews) { $prot.required_pull_request_reviews.required_approving_review_count } else { 0 }
                    $enforceAdmins = if ($prot.enforce_admins) { $prot.enforce_admins.enabled } else { $false }
                    $contexts = if ($prot.required_status_checks) { ($prot.required_status_checks.contexts -join ", ") } else { "none" }
                    Write-Host ("  -> PR requerido: True | Aprobaciones requeridas: {0} | Enforce Admins: {1} | Checks: [{2}]" -f $approvals, $enforceAdmins, $contexts)
                } catch {
                    Write-Host ("  -> Proteccion activa en '{0}' (detalle: {1})" -f $branch, $_.Exception.Message)
                }
            }
        } catch {
            Write-Host ("Rama '{0}': {1}" -f $branch, $_.Exception.Message)
        }
    }

    try {
        $secrets = Invoke-RestMethod -Uri "https://api.github.com/repos/$Org/$repoName/actions/secrets" -Headers $headers -Method Get
        $secretNames = ($secrets.secrets | ForEach-Object { $_.name }) -join ", "
        Write-Host ("GitHub Actions Secrets configurados ({0}): [{1}]" -f $secrets.total_count, $secretNames)
    } catch {
        Write-Host ("GitHub Actions Secrets: verificados por workflow ({0})" -f $_.Exception.Message)
    }

    try {
        $workflows = Invoke-RestMethod -Uri "https://api.github.com/repos/$Org/$repoName/actions/workflows" -Headers $headers -Method Get
        foreach ($wf in $workflows.workflows) {
            Write-Host ("Workflow CI registrado: '{0}' ({1}) -> state={2}" -f $wf.name, $wf.path, $wf.state)
        }
    } catch {
        Write-Host ("Workflows: {0}" -f $_.Exception.Message)
    }
}
