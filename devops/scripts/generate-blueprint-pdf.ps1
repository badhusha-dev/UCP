# Compile Business Blueprint HTML into Universal_Commerce_Business_Blueprint.pdf
$projectRoot = "D:\practical"
$htmlPath = "$projectRoot\ecommerce-platform\docs\business_blueprint.html"
$pdfDest1 = "$projectRoot\Universal_Commerce_Business_Blueprint.pdf"
$pdfDest2 = "$projectRoot\ecommerce-platform\docs\Universal_Commerce_Business_Blueprint.pdf"

$edgePath = "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
if (-not (Test-Path $edgePath)) {
    $edgePath = "C:\Program Files\Microsoft\Edge\Application\msedge.exe"
}

$fileUri = "file:///" + ($htmlPath.Replace("\", "/"))
Write-Host "Compiling $fileUri to PDF..."

$args = @(
    "--headless",
    "--disable-gpu",
    "--no-sandbox",
    "--run-all-compositor-stages-before-draw",
    "--print-to-pdf=$pdfDest1",
    $fileUri
)

$process = Start-Process -FilePath $edgePath -ArgumentList $args -Wait -PassThru
Start-Sleep -Seconds 2

if (Test-Path $pdfDest1) {
    Copy-Item -Path $pdfDest1 -Destination $pdfDest2 -Force
    $item = Get-Item $pdfDest1
    Write-Host "[SUCCESS] Generated PDF: $($item.FullName)"
    Write-Host "Size: $($item.Length) bytes, Last Modified: $($item.LastWriteTime)"
} else {
    Write-Error "Failed to generate $pdfDest1"
    exit 1
}
