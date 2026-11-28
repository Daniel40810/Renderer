@echo off
rem Seegang Lauf L4d (Seegang Werkbuch, Datei 68): S6b-2 Antrieb und Rechteck - der Auflauf in der Anwendung
rem (lauf.Auflauf am Blick, Antrieb aus der Synthese ueber render.seegang.Randwelle, Einbau ueber Flaechenwart und
rem Seegangbild.Anhang). Im Container auf lavapipe: RandwelleCheck 5/5, SwashKarteCheck 12/12, SeegangKarteCheck Teil 2
rem 7/7, ein synthetischer Strand 1:30 ueber 40 s; die Daten von Hooge gibt es nur hier.
rem 1 Renderer bauen (clean jar mit SPIR-V und Paketpruefung); Renderer.jar nach Steuerhaus\lib und HalligHooge\lib;
rem   Steuerhaus und HalligHooge bauen; 2 RandwelleCheck, SwashKarteCheck, SeegangKarteCheck mit Validierung;
rem   3 AuflaufCheck mit Validierung (Attest S6b_Auflauf.md); 4 SeegangAppCheck (Bildschleife des Warts, 3 bekannte
rem   Verstoesse wie L3a); 5 SteuerhausCheck.
rem Ausgaben: HalligHooge\docs\status\Status_L4d_*.txt; Atteste in Renderer\docs\status (S6b_Randwelle, S6b_SwashKarte,
rem S3b_SeegangKarte, S6b_Auflauf, S3d_App) und HalligHooge\docs\status (SteuerhausCheck).
rem Erwartete Dauer: rund 25 min, hoechstens 45 min [geschaetzt].
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
set "UE=%ST%\Status_L4d_.txt"
set "L1=%ST%\Status_L4d_1.txt"
set "L2=%ST%\Status_L4d_2.txt"
set "L3=%ST%\Status_L4d_3.txt"
set "L4=%ST%\Status_L4d_4.txt"
set "L5=%ST%\Status_L4d_5.txt"
set "V=-Dcore.gpu.validierung=true"
set "E=0"
set "EF=0"
for /f %%t in ('powershell -NoProfile -Command "[DateTime]::Now.Ticks"') do set "T0=%%t"
>"%UE%" echo Lauf L4d (S6b-2), gestartet %DATE% %TIME%
>"%L1%" echo Status_L4d_1: Renderer, Steuerhaus, HalligHooge bauen
>"%L2%" echo Status_L4d_2: RandwelleCheck, SwashKarteCheck, SeegangKarteCheck mit Validierung
>"%L3%" echo Status_L4d_3: AuflaufCheck mit Validierung
>"%L4%" echo Status_L4d_4: SeegangAppCheck
>"%L5%" echo Status_L4d_5: SteuerhausCheck

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

set "N=2a RandwelleCheck (Antrieb aus der Synthese, Karte gegen Referenz, Kosten auf der 680M; mit Validierung)"
set "L=%L2%"
set "C=%R% %V% com.dan.render.seegang.demo.RandwelleCheck"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=2b SwashKarteCheck (Karte gegen Referenz, seit S6b-2 Tempo im geteilten Speicher; mit Validierung)"
set "L=%L2%"
set "C=%R% %V% com.dan.physics.swash.demo.SwashKarteCheck"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=2c SeegangKarteCheck (Synthese unveraendert ohne TIEFENMITTEL; mit Validierung)"
set "L=%L2%"
set "C=%R% %V% com.dan.render.seegang.demo.SeegangKarteCheck"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=3 AuflaufCheck (Auflauf in der Anwendung: Sturm Kante, Sturm Ansicht, Xaver Kante; mit Validierung)"
set "L=%L3%"
set "C=%R% %V% com.dan.lauf.demo.AuflaufCheck --szenen %SZ%"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=4 SeegangAppCheck (Bildschleife des Warts mit dem Auflauf am Bild; 3 bekannte Verstoesse wie L3a)"
set "L=%L4%"
set "C=%R% com.dan.lauf.demo.SeegangAppCheck --szenen %SZ%"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

pushd "%HH%"
set "N=5 SteuerhausCheck"
set "L=%L5%"
set "C=java %U% --enable-native-access=ALL-UNNAMED -Xmx12g -cp dist\HalligHooge.jar com.dan.steuerhaus.demo.SteuerhausCheck --manifest src\szenen\Hooge.steuerhaus --quellen ..\Steuerhaus\src --bilder docs\status"
call :lauf
popd
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
