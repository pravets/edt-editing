# Builds the self test bundle and installs it into the EDT installation.
#
#   .\build.ps1 -Edt "C:\Program Files\1C\1CE\components\1c-edt-2026.2.2+21-x86_64" -Install
#
# Without -Install the jar is only built into target\.

param(
    [string]$Edt = "C:\Program Files\1C\1CE\components\1c-edt-2026.2.2+21-x86_64",
    [switch]$Install
)

$ErrorActionPreference = "Stop"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$target = Join-Path $here "target"
$jdk = "C:\Program Files\1C\1CE\components\axiom-jdk-full-25.0.4+5-x86_64\bin"

Remove-Item -Recurse -Force $target -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $target, "$target\classes" | Out-Null

# Class path: EDT bundles plus the editing plugin under test.
subst X: "$Edt\plugins" 2>$null
$patterns = "*com._1c.g5*", "*org.eclipse*", "*xtext*", "*snakeyaml*", "*jgit*", "*guava*", "*inject*", "*e4*", "*slf4j*"
$jars = Get-ChildItem X:\ -Filter *.jar | Where-Object {
    $name = $_.Name
    ($patterns | Where-Object { $name -like $_ })
} | ForEach-Object FullName

$pool = Get-ChildItem "$env:USERPROFILE\.eclipse" -Directory | ForEach-Object {
    $plugins = Join-Path $_.FullName "plugins"
    if (Test-Path $plugins)
    {
        Get-ChildItem $plugins -Filter "org.mard.dt.editing_0.7.*.jar" -ErrorAction SilentlyContinue
    }
} | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $pool)
{
    throw "The editing plugin 0.7.* is not installed in the user Eclipse pool (%~\.eclipse). Install build 0.7.1 first."
}
$jars += $pool.FullName

$classPath = ($jars -join ";")
# The installation contains old fragments of the same packages: put the current
# platform bundles first so that javac does not pick their outdated classes.
$preferred = Get-ChildItem X:\ -Filter "org.eclipse.core.resources_3.*.jar" | Select-Object -First 1
if ($preferred)
{
    $classPath = $preferred.FullName + ";" + $classPath
}
$javacArgs = @("-cp", "`"$($classPath.Replace('\','/'))`"", "-d", "`"$($target.Replace('\','/'))/classes`"")
$javacArgs += Get-ChildItem "$here\src" -Recurse -Filter *.java | ForEach-Object { "`"$($_.FullName.Replace('\','/'))`"" }

# The class path is too long for the command line: use an argument file.
$argsFile = Join-Path $target "javac.args"
[System.IO.File]::WriteAllLines($argsFile, $javacArgs)
& "$jdk\javac.exe" "@$argsFile"
if ($LASTEXITCODE -ne 0)
{
    throw "javac failed"
}

Copy-Item "$here\META-INF\MANIFEST.MF" "$target\MANIFEST.MF" -Force
$jar = Join-Path $target "org.mard.dt.editing.selftest_1.0.8.jar"
& "$jdk\jar.exe" cfm $jar "$target\MANIFEST.MF" -C "$target\classes" .
subst X: /D
Write-Host "built $jar"

if ($Install)
{
    $dropins = Join-Path $Edt "dropins"
    if (-not (Test-Path $dropins))
    {
        # The installation folder is protected: ask for elevation once.
        $dropinsPath = $dropins.Replace("'", "''")
        $jarPath = $jar.Replace("'", "''")
        $script = "New-Item -ItemType Directory -Force '$dropinsPath' | Out-Null; " +
            "Copy-Item '$jarPath' '$dropinsPath' -Force"
        Start-Process powershell -Verb RunAs -ArgumentList "-NoProfile", "-Command", $script -Wait
    }
    else
    {
        Copy-Item $jar $dropins -Force
    }
    Write-Host "installed into $dropins - restart EDT to activate"
}
