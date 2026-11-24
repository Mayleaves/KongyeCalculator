param([string]$Sdk='D:\Program\Android\Sdk',[string]$Jdk='D:\Program\Android\Android Studio\jbr')
$ErrorActionPreference='Stop'
$env:JAVA_HOME=$Jdk
$project=$PSScriptRoot
$out=Join-Path $project 'build\direct'
$bt=Join-Path $Sdk 'build-tools\36.0.0'
$android=Join-Path $Sdk 'platforms\android-37.0\android.jar'
function Run([string]$Exe,[string[]]$Arguments){ & $Exe @Arguments; if($LASTEXITCODE -ne 0){throw "Build command failed: $Exe"} }
New-Item -ItemType Directory -Force $out,"$out\gen","$out\classes","$out\dex" | Out-Null
$manifest=[IO.File]::ReadAllText("$project\app\src\main\AndroidManifest.xml").Replace('<manifest xmlns:android=','<manifest package="com.kongye.calculator" xmlns:android=')
[IO.File]::WriteAllText("$out\AndroidManifest.xml",$manifest,(New-Object Text.UTF8Encoding $false))
Run "$bt\aapt2.exe" @('compile','--dir',"$project\app\src\main\res",'-o',"$out\resources.zip")
Run "$bt\aapt2.exe" @('link','-o',"$out\unsigned.apk",'-I',$android,'--manifest',"$out\AndroidManifest.xml",'-A',"$project\app\src\main\assets",'--java',"$out\gen",'--min-sdk-version','26','--target-sdk-version','36','--version-code','37','--version-name','1.0.3',"$out\resources.zip")
$sources=@(Get-ChildItem "$project\app\src\main\java","$out\gen" -Filter '*.java' -Recurse | ForEach-Object FullName)
Run "$Jdk\bin\javac.exe" (@('-encoding','UTF-8','-source','8','-target','8','-classpath',$android,'-d',"$out\classes")+$sources)
$classes=@(Get-ChildItem "$out\classes" -Filter '*.class' -Recurse | ForEach-Object FullName)
Run "$bt\d8.bat" (@('--min-api','26','--lib',$android,'--output',"$out\dex")+$classes)
Run "$Jdk\bin\jar.exe" @('uf',"$out\unsigned.apk",'-C',"$out\dex",'classes.dex')
Run "$bt\zipalign.exe" @('-f','-p','4',"$out\unsigned.apk","$out\aligned.apk")
$key=Join-Path $project 'build\test-signing.p12'
if(!(Test-Path $key)){Run "$Jdk\bin\keytool.exe" @('-genkeypair','-keystore',$key,'-storepass','android','-keypass','android','-alias','kongye-test','-keyalg','RSA','-keysize','2048','-validity','10000','-dname','CN=Kongye Calculator Test')}
$delivery=Join-Path $project 'build\apk-delivery'
New-Item -ItemType Directory -Force -Path $delivery | Out-Null
$apk=Join-Path $delivery 'KongyeCalculator-1.0.0.apk'
Run "$bt\apksigner.bat" @('sign','--ks',$key,'--ks-key-alias','kongye-test','--ks-pass','pass:android','--key-pass','pass:android','--out',$apk,"$out\aligned.apk")
Run "$bt\apksigner.bat" @('verify','--verbose',$apk)
Write-Output "APK: $apk"







