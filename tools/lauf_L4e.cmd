@echo off
rem Seegang Lauf L4e (Seegang Werkbuch, Datei 70): S6b-2b nach SF65-SF67 A - Courant 0,4 (Swash 0.2), Rechteck 128 m
rem laengs, Sicherheit 1,2, hoechstens 8 Schritte je Bild, landwaerts ab der landwaertigsten Stelle der Wasserlinie
rem (Auflauf 0.2); AuflaufCheck mit 120 s je Fall und Diagnose (Tempo je Bild mit Ort, Zeitreihe des Wassers, Hs am Rand).
rem 1 Renderer bauen (clean jar mit SPIR-V und Paketpruefung); Renderer.jar nach Steuerhaus\lib und HalligHooge\lib;
rem   Steuerhaus und HalligHooge bauen; 2 SeegangKarteCheck zuerst (Zeit der Synthese vor den schweren Pruefungen),
rem   SwashCheck (Referenz mit Courant 0,4; 1 rot wie bisher: Zunge A6), SwashKarteCheck; 3 AuflaufCheck.
rem Ausgaben: HalligHooge\docs\status\Status_L4e_*.txt; Atteste in Renderer\docs\status (S3b_SeegangKarte, S6a_Swash,
rem S6b_SwashKarte, S6b_Auflauf).
rem Erwartete Dauer: rund 10 min, hoechstens 20 min [geschaetzt].
rem Anhalten zwischen zwei Schritten: eine Datei STOPP.txt neben dieses Skript legen.
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0.."

set "P=C:\Users\Dan40810\Documents\NetBeansProjects"
set "HH=%P%\HalligHooge"
set "SH=%P%\Steuerhaus"
set "RE=%P%\Renderer"
set "ST=%HH%\docs\status"
set "SZ=%HH%\src\szenen\Hooge.szenen"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
set "R=java %U% --enable-native-access=ALL-UNNAMED -Xmx16g -cp dist\Renderer.jar"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"

if not exist "%ST%" mkdir "%ST%"
set "UE=%ST%\Status_L4e_.txt"
set "L1=%ST%\Status_L4e_1.txt"
set "L2=%ST%\Status_L4e_2.txt"
set "L3=%ST%\Status_L4e_3.txt"
set "V=-Dcore.gpu.validierung=true"
set "E=0"
set "EF=0"
for /f %%t in ('powershell -NoProfile -Command "[DateTime]::Now.Ticks"') do set "T0=%%t"
>"%UE%" echo Lauf L4e (S6b-2b), gestartet %DATE% %TIME%
>"%L1%" echo Status_L4e_1: Renderer, Steuerhaus, HalligHooge bauen
>"%L2%" echo Status_L4e_2: SeegangKarteCheck, SwashCheck, SwashKarteCheck
>"%L3%" echo Status_L4e_3: AuflaufCheck mit Validierung

set "N=1 Renderer bauen (clean jar, SPIR-V, check-packages)"
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
copy /y "%RE%\dist\Renderer.jar" "%SH%\lib\Renderer.jar" >>"%L1%" 2>&1
copy /y "%RE%\dist\Renderer.jar" "%HH%\lib\Renderer.jar" >>"%L1%" 2>&1
pushd "%SH%"
set "N=1b Steuerhaus bauen (clean jar)"
set "L=%L1%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
popd
if not "%E%"=="0" goto :abbruch
pushd "%HH%"
set "N=1c HalligHooge bauen (clean jar)"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
popd
if not "%E%"=="0" goto :abbruch

set "N=2a SeegangKarteCheck (zuerst, vor den schweren Pruefungen; mit Validierung)"
set "L=%L2%"
set "C=%R% %V% com.dan.render.seegang.demo.SeegangKarteCheck"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=2b SwashCheck (Referenz mit Courant 0,4; 1 rot wie bisher: Teil 6 Zunge, A6)"
set "L=%L2%"
set "C=%R% com.dan.physics.swash.demo.SwashCheck"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=2c SwashKarteCheck (Karte gegen Referenz mit Courant 0,4; mit Validierung)"
set "L=%L2%"
set "C=%R% %V% com.dan.physics.swash.demo.SwashKarteCheck"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=3 AuflaufCheck (Auflauf 0.2, 120 s je Fall, Diagnose; mit Validierung)"
set "L=%L3%"
set "C=%R% %V% com.dan.lauf.demo.AuflaufCheck --szenen %SZ%"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

>>"%UE%" echo.
if not "%EF%"=="0" >>"%UE%" echo Mindestens ein Schritt mit Exit ungleich 0 - siehe die Statusdateien und Atteste
>>"%UE%" echo FERTIG %DATE% %TIME%
echo.
echo Fertig. Uebersicht: %UE%
timeout /t 5 >nul
goto :eof

:abbruch
>>"%UE%" echo.
>>"%UE%" echo ABBRUCH %DATE% %TIME% nach: %N%
echo.
echo Abbruch nach: %N%  - siehe %L%
timeout /t 60 >nul
exit /b 1

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
