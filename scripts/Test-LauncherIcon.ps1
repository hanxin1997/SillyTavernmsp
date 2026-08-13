$ErrorActionPreference = 'Stop'

# Keep launcher-icon validation independent from the Android SDK so it can run
# both in CI and in this repository's lightweight local development environment.
$projectRoot = Split-Path -Parent $PSScriptRoot
$mainSource = Join-Path $projectRoot 'app/src/main'
$resourceRoot = Join-Path $mainSource 'res'
$manifestPath = Join-Path $mainSource 'AndroidManifest.xml'
$androidNamespace = 'http://schemas.android.com/apk/res/android'

function Read-XmlFile {
    param(
        [Parameter(Mandatory)]
        [string]$Path
    )

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Required launcher icon resource is missing: $Path"
    }

    [xml](Get-Content -LiteralPath $Path -Raw -Encoding UTF8)
}

function Assert-AndroidAttribute {
    param(
        [Parameter(Mandatory)]
        [System.Xml.XmlElement]$Element,

        [Parameter(Mandatory)]
        [string]$Name,

        [Parameter(Mandatory)]
        [string]$ExpectedValue,

        [Parameter(Mandatory)]
        [string]$SourcePath
    )

    $actualValue = $Element.GetAttribute($Name, $androidNamespace)
    if ($actualValue -ne $ExpectedValue) {
        throw "$SourcePath must set android:$Name to '$ExpectedValue'; found '$actualValue'."
    }
}

function Assert-AdaptiveIcon {
    param(
        [Parameter(Mandatory)]
        [string]$Path,

        [Parameter(Mandatory)]
        [bool]$RequireMonochrome
    )

    $document = Read-XmlFile -Path $Path
    $root = $document.DocumentElement
    if ($root.LocalName -ne 'adaptive-icon') {
        throw "$Path must contain an adaptive-icon root element."
    }

    $requiredLayers = [ordered]@{
        background = '@color/launcher_background'
        foreground = '@drawable/ic_launcher_foreground'
    }
    if ($RequireMonochrome) {
        $requiredLayers.monochrome = '@drawable/ic_launcher_monochrome'
    }

    foreach ($layerEntry in $requiredLayers.GetEnumerator()) {
        $layerName = $layerEntry.Key
        $layer = $root.SelectSingleNode("*[local-name()='$layerName']")
        if ($null -eq $layer) {
            throw "$Path is missing the <$layerName> layer."
        }

        $drawableReference = $layer.GetAttribute('drawable', $androidNamespace)
        if ($drawableReference -ne $layerEntry.Value) {
            throw "$Path must reference '$($layerEntry.Value)' from <$layerName>; found '$drawableReference'."
        }
    }
}

$manifest = Read-XmlFile -Path $manifestPath
$application = $manifest.manifest.application
if ($null -eq $application) {
    throw "$manifestPath has no <application> element."
}

Assert-AndroidAttribute -Element $application -Name 'icon' -ExpectedValue '@mipmap/ic_launcher' -SourcePath $manifestPath
Assert-AndroidAttribute -Element $application -Name 'roundIcon' -ExpectedValue '@mipmap/ic_launcher_round' -SourcePath $manifestPath

$adaptiveIcons = @(
    @{ Path = 'mipmap-anydpi-v26/ic_launcher.xml'; Monochrome = $false },
    @{ Path = 'mipmap-anydpi-v26/ic_launcher_round.xml'; Monochrome = $false },
    @{ Path = 'mipmap-anydpi-v33/ic_launcher.xml'; Monochrome = $true },
    @{ Path = 'mipmap-anydpi-v33/ic_launcher_round.xml'; Monochrome = $true }
)

foreach ($adaptiveIcon in $adaptiveIcons) {
    $path = Join-Path $resourceRoot $adaptiveIcon.Path
    Assert-AdaptiveIcon -Path $path -RequireMonochrome $adaptiveIcon.Monochrome
}

$foregroundPath = Join-Path $resourceRoot 'drawable/ic_launcher_foreground.xml'
$monochromePath = Join-Path $resourceRoot 'drawable/ic_launcher_monochrome.xml'
foreach ($vectorPath in @($foregroundPath, $monochromePath)) {
    $vector = Read-XmlFile -Path $vectorPath
    if ($vector.DocumentElement.LocalName -ne 'vector') {
        throw "$vectorPath must contain a vector root element."
    }

    Assert-AndroidAttribute -Element $vector.DocumentElement -Name 'width' -ExpectedValue '108dp' -SourcePath $vectorPath
    Assert-AndroidAttribute -Element $vector.DocumentElement -Name 'height' -ExpectedValue '108dp' -SourcePath $vectorPath
    Assert-AndroidAttribute -Element $vector.DocumentElement -Name 'viewportWidth' -ExpectedValue '108' -SourcePath $vectorPath
    Assert-AndroidAttribute -Element $vector.DocumentElement -Name 'viewportHeight' -ExpectedValue '108' -SourcePath $vectorPath
    if ($vector.SelectNodes("/*[local-name()='vector']/*[local-name()='path']").Count -eq 0) {
        throw "$vectorPath must contain at least one vector path."
    }
}

$colorsPath = Join-Path $resourceRoot 'values/colors.xml'
$colors = Read-XmlFile -Path $colorsPath
$launcherBackground = $colors.SelectSingleNode("/*[local-name()='resources']/*[local-name()='color'][@name='launcher_background']")
if ($null -eq $launcherBackground) {
    throw "$colorsPath must define launcher_background."
}

$backgroundValue = $launcherBackground.InnerText.Trim()
if ($backgroundValue -notmatch '^(#[0-9A-Fa-f]{6}|#[Ff]{2}[0-9A-Fa-f]{6})$') {
    throw "launcher_background must be a fully opaque RGB or ARGB color; found '$backgroundValue'."
}

Write-Output 'Launcher icon resources are complete and parseable.'
