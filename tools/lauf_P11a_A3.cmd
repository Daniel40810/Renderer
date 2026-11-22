@echo off
rem P11a, Lauf A3 (Werkbuch HalligHooge Teil A, 12.12, Datei 51): nach Lauf A2 (477 Pfade/s): der Kernel als Zustandsautomat,
rem ohne Auslagerung in den Scratch (Mesa/ACO fuer gfx1035: 206 statt 440 Register, 48 statt 245 KB Code).
rem 2 ReferenceKarteCheck --tempo: haelt an, wenn die Karte nicht das Zehnfache von Stufe 1 schafft (Pruefstein 7).
rem 1 Renderer bauen (ant clean jar, mit check-packages und SPIR-V); danach Pruefung, ob dist\Renderer.jar neu ist.
rem 3 ReferenceKarteCheck --schnell mit Validierungsschicht (ein Zehntel der Pfade, ohne Attest).
rem 4 ReferenceKarteCheck (Pruefsteine 1-8; Attest Renderer\docs\status\P11a_ReferenzKarte.md).
rem Ausgaben: HalligHooge\docs\status\Status_Phase11aA3_*.txt (UTF-8), Uebersicht in Status_Phase11aA3_.txt.
rem Erwartete Dauer: 5 bis 15 Minuten; mit Halt nach Schritt 2 rund 3 Minuten.
rem Anhalten zwischen zwei Schritten: eine Datei STOPP.txt neben dieses Skript legen.
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0.."

set "HH=C:\Users\Dan40810\Documents\NetBeansProjects\HalligHooge"
set "ST=%HH%\docs\status"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
set "R=java %U% --enable-native-access=ALL-UNNAMED -Xmx12g -cp dist\Renderer.jar"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"

set "UE=%ST%\Status_Phase11aA3_.txt"
set "L1=%ST%\Status_Phase11aA3_1.txt"
set "L2=%ST%\Status_Phase11aA3_2.txt"
set "L3=%ST%\Status_Phase11aA3_3.txt"
set "L4=%ST%\Status_Phase11aA3_4.txt"

for /f %%t in ('powershell -NoProfile -Command "[DateTime]::Now.Ticks"') do set "T0=%%t"
>"%UE%" echo Lauf P11a A3, gestartet %DATE% %TIME%
>"%L1%" echo Status_Phase11aA3_1: Renderer bauen (clean jar, check-packages)
>"%L2%" echo Status_Phase11aA3_2: ReferenceKarteCheck --tempo
>"%L3%" echo Status_Phase11aA3_3: ReferenceKarteCheck --schnell mit Validierung
>"%L4%" echo Status_Phase11aA3_4: ReferenceKarteCheck

set "N=1 Renderer bauen (clean jar, check-packages)"
set "L=%L1%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" goto :abbruch
powershell -NoProfile -Command "if ((Get-Item 'dist\Renderer.jar').LastWriteTime.Ticks -gt %T0%) { exit 0 } else { exit 1 }"
if errorlevel 1 (
  >>"%UE%" echo dist\Renderer.jar ist nicht neuer als der Start des Laufs
  set "N=1 Renderer bauen (jar nicht neu)"
  goto :abbruch
)
>>"%UE%" echo dist\Renderer.jar ist neu

set "N=2 ReferenceKarteCheck --tempo"
set "L=%L2%"
set "C=%R% com.dan.render.reference.gpu.demo.ReferenceKarteCheck --tempo"
call :lauf
if not "%E%"=="0" goto :abbruch

set "N=3 ReferenceKarteCheck --schnell mit Validierung"
set "L=%L3%"
set "C=java %U% -Dcore.gpu.validierung=true --enable-native-access=ALL-UNNAMED -Xmx12g -cp dist\Renderer.jar com.dan.render.reference.gpu.demo.ReferenceKarteCheck --schnell"
call :lauf

set "N=4 ReferenceKarteCheck"
set "L=%L4%"
set "C=%R% com.dan.render.reference.gpu.demo.ReferenceKarteCheck"
call :lauf

>>"%UE%" echo.
>>"%UE%" echo FERTIG %DATE% %TIME%
echo.
echo Fertig. Uebersicht: %UE%
timeout /t 20 >nul
goto :eof

:abbruch
>>"%UE%" echo.
>>"%UE%" echo ABBRUCH %DATE% %TIME% nach: %N%
echo.
echo Abbruch nach: %N%  - siehe %L%
timeout /t 60 >nul
goto :eof

:lauf
if not exist "%~dp0STOPP.txt" goto :lauf2
echo [%DATE% %TIME%] STOPP  %N%
>>"%UE%" echo [%DATE% %TIME%] STOPP  %N%  - uebersprungen
set "E=99"
goto :eof
:lauf2
echo [%DATE% %TIME%] Start  %N%
>>"%UE%" echo [%DATE% %TIME%] Start  %N%
>>"%L%" echo.
>>"%L%" echo ^>%C%
%C% >>"%L%" 2>&1
set "E=%ERRORLEVEL%"
echo [%DATE% %TIME%] Ende   %N%  - Exit %E%
>>"%UE%" echo [%DATE% %TIME%] Ende   %N%  - Exit %E%
goto :eof
