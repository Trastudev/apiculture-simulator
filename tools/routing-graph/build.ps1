param(
    [ValidateSet("valencia", "iberia", "madagascar", "za")]
    [string]$Region = "valencia"
)

$ErrorActionPreference = "Stop"
$Root = $PSScriptRoot
$Data = Join-Path $Root "data"
New-Item -ItemType Directory -Force -Path $Data | Out-Null

$GhJar = Join-Path $Data "graphhopper-web-10.2.jar"
$GhUrl = "https://repo1.maven.org/maven2/com/graphhopper/graphhopper-web/10.2/graphhopper-web-10.2.jar"
$HighwayFilter = "w/highway=motorway,motorway_link,trunk,trunk_link,primary,primary_link,secondary,secondary_link,tertiary,tertiary_link,unclassified w/route=ferry"

function Get-File($Url, $Dest) {
    Write-Host "Descargando $Url"
    curl.exe -L --fail --retry 5 --retry-all-errors -C - -o $Dest $Url
    if ($LASTEXITCODE -ne 0) {
        throw "Fallo al descargar $Url"
    }
}

function Test-Docker {
    docker info --format "{{.ServerVersion}}" 2>$null | Out-Null
    return ($LASTEXITCODE -eq 0)
}

function Invoke-Osmium([string[]]$OsmiumArgs) {
    docker run --rm -v "${Data}:/data" iboates/osmium:latest $OsmiumArgs
    if ($LASTEXITCODE -ne 0) {
        throw "osmium fallo: $($OsmiumArgs -join ' ')"
    }
}

if (-not (Test-Docker)) {
    throw "Docker no esta en marcha. Abre Docker Desktop y vuelve a lanzar build.ps1"
}

Get-File $GhUrl $GhJar

$Pbfs = @()
if ($Region -eq "valencia") {
    $Pbfs += @{ Name = "valencia-latest.osm.pbf"; Url = "https://download.geofabrik.de/europe/spain/valencia-latest.osm.pbf" }
} elseif ($Region -eq "madagascar") {
    $Pbfs += @{ Name = "madagascar-latest.osm.pbf"; Url = "https://download.geofabrik.de/africa/madagascar-latest.osm.pbf" }
} elseif ($Region -eq "za") {
    $Pbfs += @{ Name = "south-africa-latest.osm.pbf"; Url = "https://download.geofabrik.de/africa/south-africa-latest.osm.pbf" }
    $Pbfs += @{ Name = "lesotho-latest.osm.pbf"; Url = "https://download.geofabrik.de/africa/lesotho-latest.osm.pbf" }
    $Pbfs += @{ Name = "swaziland-latest.osm.pbf"; Url = "https://download.geofabrik.de/africa/swaziland-latest.osm.pbf" }
} else {
    $Pbfs += @{ Name = "spain-latest.osm.pbf"; Url = "https://download.geofabrik.de/europe/spain-latest.osm.pbf" }
    $Pbfs += @{ Name = "portugal-latest.osm.pbf"; Url = "https://download.geofabrik.de/europe/portugal-latest.osm.pbf" }
    $Pbfs += @{ Name = "andorra-latest.osm.pbf"; Url = "https://download.geofabrik.de/europe/andorra-latest.osm.pbf" }
}

foreach ($p in $Pbfs) {
    Get-File $p.Url (Join-Path $Data $p.Name)
}

$Filtered = @()
$useFilter = $true
try {
    docker image inspect iboates/osmium:latest 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Bajando imagen osmium..."
        docker pull iboates/osmium:latest
    }
} catch {
    $useFilter = $false
}

if ($useFilter) {
    foreach ($p in $Pbfs) {
        $inName = $p.Name
        $outName = $inName -replace "\.osm\.pbf$", "-hw.osm.pbf"
        $outPath = Join-Path $Data $outName
        if (-not (Test-Path $outPath)) {
            Write-Host "Filtrando carreteras $inName"
            Invoke-Osmium @("tags-filter", "/data/$inName", "w/highway=motorway,motorway_link,trunk,trunk_link,primary,primary_link,secondary,secondary_link,tertiary,tertiary_link,unclassified", "w/route=ferry", "-o", "/data/$outName", "--overwrite")
        }
        $Filtered += $outName
    }
} else {
    Write-Host "Sin osmium: GraphHopper ignorara residencial/aceras al importar"
    $Filtered = $Pbfs | ForEach-Object { $_.Name }
}

$Merged = Join-Path $Data "highways.osm.pbf"
if ($Filtered.Count -eq 1) {
    Copy-Item (Join-Path $Data $Filtered[0]) $Merged -Force
} else {
    Write-Host "Mezclando extractos"
    $inputs = $Filtered | ForEach-Object { "/data/$_" }
    Invoke-Osmium (@("merge") + $inputs + @("-o", "/data/highways.osm.pbf", "--overwrite"))
}

$GraphDir = Join-Path $Data "graph-cache"
if (Test-Path $GraphDir) {
    Remove-Item -Recurse -Force $GraphDir
}

Write-Host "Importando GraphHopper (Java 21 en Docker)..."
docker image inspect eclipse-temurin:21-jre 2>$null | Out-Null
if ($LASTEXITCODE -ne 0) {
    docker pull eclipse-temurin:21-jre
}
docker run --rm `
    -v "${Data}:/data" `
    -v "${Root}:/cfg" `
    -e JAVA_TOOL_OPTIONS="-Xmx6g" `
    eclipse-temurin:21-jre `
    java -jar /data/graphhopper-web-10.2.jar import /cfg/config.yml
if ($LASTEXITCODE -ne 0) {
    throw "GraphHopper import fallo"
}

$Ghz = Join-Path $Data "$Region-car-lite.tar.gz"
if (Test-Path $Ghz) {
    Remove-Item $Ghz
}
Write-Host "Comprimiendo $Ghz"
Push-Location $GraphDir
try {
    tar -czf $Ghz *
} finally {
    Pop-Location
}

$pbfSize = (Get-Item $Merged).Length / 1MB
$graphSize = ((Get-ChildItem $GraphDir -Recurse -File | Measure-Object Length -Sum).Sum) / 1MB
$ghzSize = (Get-Item $Ghz).Length / 1MB
Write-Host ""
Write-Host "=== $Region ==="
Write-Host ("PBF de entrada: {0:N1} MB" -f $pbfSize)
Write-Host ("Grafo en disco: {0:N1} MB" -f $graphSize)
Write-Host ("Paquete .ghz: {0:N1} MB" -f $ghzSize)
Write-Host "Archivo: $Ghz"
