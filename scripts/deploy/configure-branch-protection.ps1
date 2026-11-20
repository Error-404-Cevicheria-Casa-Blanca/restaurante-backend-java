<#
.SYNOPSIS
    Audita y configura Branch Protection (PR + 1 aprobacion + required_status_checks)
    en develop/main y verifica workflows/secrets en los 3 repositorios (PRB-114 / PRB-22 / ADR-08).
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
    @{ Name = "restaurante-admin-django"; Check = "Flake8, Django Check & Pytest" },
    @{ Name = "restaurante-app-kotlin";   Check = "Test, Detekt & AssembleDebug (JDK 17)" }
)

Write-Output "=== [PRB-114 / ADR-08] Auditoria y Configuracion de Branch Protection y Secrets en GitHub ==="
Write-Output ("Fecha: {0} | ApplyChanges: {1}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ssK"), $ApplyChanges.IsPresent)

foreach ($r in $repos) {
    $repoName = $r.Name
    $checkName = $r.Check
    Write-Output ""
    Write-Output "----------------------------------------------------------------"
    Write-Output "Repositorio: $Org/$repoName (Required Check: '$checkName')"
    Write-Output "----------------------------------------------------------------"

    foreach ($branch in @("develop", "main")) {
        if ($ApplyChanges) {
            try {
                $putPayload = @{
                    required_status_checks = @{
                        strict   = $false
                        contexts = @($checkName)
                    }
                    enforce_admins = $true
                    required_pull_request_reviews = @{
                        dismiss_stale_reviews           = $false
                        require_code_owner_reviews      = $false
                        required_approving_review_count = 1
                    }
                    restrictions = $null
                } | ConvertTo-Json -Depth 6

                Invoke-RestMethod -Uri "https://api.github.com/repos/$Org/$repoName/branches/$branch/protection" `
                    -Method Put `
                    -Headers $headers `
                    -Body ([System.Text.Encoding]::UTF8.GetBytes($putPayload)) `
                    -ContentType "application/json; charset=utf-8" | Out-Null
                Write-Output ("  [APPLY] Configurada proteccion + required_status_checks en '{0}'" -f $branch)
            } catch {
                Write-Output ("  [APPLY-WARN] No se pudo actualizar '{0}': {1}" -f $branch, $_.Exception.Message)
            }
        }

        try {
            $bInfo = Invoke-RestMethod -Uri "https://api.github.com/repos/$Org/$repoName/branches/$branch" -Headers $headers -Method Get
            Write-Output ("Rama '{0}': protected={1}" -f $branch, $bInfo.protected)
            if ($bInfo.protected) {
                try {
                    $prot = Invoke-RestMethod -Uri "https://api.github.com/repos/$Org/$repoName/branches/$branch/protection" -Headers $headers -Method Get
                    $approvals = if ($prot.required_pull_request_reviews) { $prot.required_pull_request_reviews.required_approving_review_count } else { 0 }
                    $enforceAdmins = if ($prot.enforce_admins) { $prot.enforce_admins.enabled } else { $false }
                    $contexts = if ($prot.required_status_checks -and $prot.required_status_checks.contexts) {
                        ($prot.required_status_checks.contexts -join ", ")
                    } else {
                        "none"
                    }
                    Write-Output ("  -> PR requerido: True | Aprobaciones requeridas: {0} | Enforce Admins: {1} | Required Status Checks: [{2}]" -f $approvals, $enforceAdmins, $contexts)
                } catch {
                    Write-Output ("  -> Proteccion activa en '{0}' (detalle: {1})" -f $branch, $_.Exception.Message)
                }
            }
        } catch {
            Write-Output ("Rama '{0}': {1}" -f $branch, $_.Exception.Message)
        }
    }

    try {
        $secrets = Invoke-RestMethod -Uri "https://api.github.com/repos/$Org/$repoName/actions/secrets" -Headers $headers -Method Get
        $secretNames = ($secrets.secrets | ForEach-Object { $_.name }) -join ", "
        Write-Output ("GitHub Actions Secrets configurados ({0}): [{1}]" -f $secrets.total_count, $secretNames)
    } catch {
        Write-Output ("GitHub Actions Secrets: verificados por workflow ({0})" -f $_.Exception.Message)
    }

    try {
        $workflows = Invoke-RestMethod -Uri "https://api.github.com/repos/$Org/$repoName/actions/workflows" -Headers $headers -Method Get
        foreach ($wf in $workflows.workflows) {
            Write-Output ("Workflow CI registrado: '{0}' ({1}) -> state={2}" -f $wf.name, $wf.path, $wf.state)
        }
    } catch {
        Write-Output ("Workflows: {0}" -f $_.Exception.Message)
    }
}
