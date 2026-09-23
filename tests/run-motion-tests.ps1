$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
$classes = Join-Path $project 'build/motion-tests'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$jdk = 'C:/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot/bin'
$ui = Join-Path $project 'app/src/main/java/com/xingyu/music/ui'
& "$jdk/javac.exe" -encoding UTF-8 -d $classes "$ui/PlaybackHighlightState.java" "$ui/VinylStackGeometry.java" "$PSScriptRoot/MotionRegressionTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Regression compilation failed' }
& "$jdk/java.exe" -cp $classes com.xingyu.music.ui.MotionRegressionTest
if ($LASTEXITCODE -ne 0) { throw 'Motion regression failed' }
