# Builds every target in targets/ (or only one loader: .\build-all.ps1 fabric) and collects the jars in dist/.
# -Clean runs "clean build" for each target and empties dist/ first.
param([string]$Loader, [switch]$Clean)

$root = $PSScriptRoot
$dist = Join-Path $root 'dist'
if ($Clean -and (Test-Path $dist)) { Remove-Item (Join-Path $dist '*') -Force }
New-Item -ItemType Directory -Force $dist | Out-Null
$tasks = if ($Clean) { @('clean', 'build') } else { @('build') }

$loaders = Get-ChildItem (Join-Path $root 'targets') -Directory
if ($Loader) { $loaders = $loaders | Where-Object { $_.Name -eq $Loader -or $_.Name -eq "$Loader-unobf" } }

foreach ($folder in $loaders) {
    foreach ($file in Get-ChildItem $folder.FullName -Filter '*.properties') {
        $target = $file.BaseName
        Write-Host "== $($folder.Name) $target"
        & (Join-Path $root 'gradlew.bat') -p (Join-Path $root "loaders\$($folder.Name)") @tasks "-Ptarget=$target"
        if ($LASTEXITCODE -ne 0) {
            Write-Warning "$($folder.Name) $target failed"
            continue
        }
        Get-ChildItem (Join-Path $root "loaders\$($folder.Name)\build\$target\libs") -Filter '*.jar' |
            Where-Object { $_.Name -notmatch '-(dev|sources)\.jar$' } |
            Copy-Item -Destination $dist
    }
}
