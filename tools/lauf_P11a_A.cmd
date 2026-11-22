@echo off
rem P11a, Lauf A (Werkbuch HalligHooge Teil A, 12.12, Dateien 50, 51): Referenz Stufe 2 auf der Karte, ohne Gebiet.
rem 1 Renderer bauen (ant clean jar, mit check-packages und SPIR-V); danach Pruefung, ob dist\Renderer.jar neu ist.
rem 2 ReferenceKarteCheck --schnell mit Validierungsschicht (ein Zehntel der Pfade, ohne Attest).
rem 3 ReferenceKarteCheck (Pruefsteine 1-8; Attest Renderer\docs\status\P11a_ReferenzKarte.md).
rem 4 ReferenceCheck von Stufe 1 (unveraendert, nur Lesezugriffe dazu), ohne Attest.
rem Ausgaben: HalligHooge\docs\status\Status_Phase11aA_*.txt (UTF-8), Uebersicht in Status_Phase11aA_.txt.
rem Erwartete Dauer: 15 bis 25 Minuten.
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

set "UE=%ST%\Status_Phase11aA_.txt"
set "L1=%ST%\Status_Phase11aA_1.txt"
set "L2=%ST%\Status_Phase11aA_2.txt"
set "L3=%ST%\Status_Phase11aA_3.txt"
set "L4=%ST%\Status_Phase11aA_4.txt"

for /f %%t in ('powershell -NoProfile -Command "[DateTime]::Now.Ticks"') do set "T0=%%t"
>"%UE%" echo Lauf P11a A, gestartet %DATE% %TIME%
>"%L1%" echo Status_Phase11aA_1: Renderer bauen (clean jar, check-packages)
>"%L2%" echo Status_Phase11aA_2: ReferenceKarteCheck --schnell mit Validierung
>"%L3%" echo Status_Phase11aA_3: ReferenceKarteCheck
>"%L4%" echo Status_Phase11aA_4: ReferenceCheck (Stufe 1)

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

set "N=2 ReferenceKarteCheck --schnell mit Validierung"
set "L=%L2%"
set "C=java %U% -Dcore.gpu.validierung=true --enable-native-access=ALL-UNNAMED -Xmx12g -cp dist\Renderer.jar com.dan.render.reference.gpu.demo.ReferenceKarteCheck --schnell"
call :lauf

set "N=3 ReferenceKarteCheck"
set "L=%L3%"
set "C=%R% com.dan.render.reference.gpu.demo.ReferenceKarteCheck"
call :lauf

set "N=4 ReferenceCheck (Stufe 1)"
set "L=%L4%"
set "C=%R% com.dan.render.reference.demo.ReferenceCheck --ohne-attest"
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
