@echo off
rem Seegang Lauf L6c3b (Seegang Werkbuch, Datei 104): S8e2 - wo ist der Adler-Express?
rem Befund Probe L6c3: Seegang und Brandung sind jetzt im Video; im Abschnitt "adler" (13:19 MEZ) faehrt der Adler-Express aber
rem nicht durchs Bild. Diese Diagnose spielt das Regiebuch klein (640 x 360, 1 Teilbild, ohne Ton) bis 1:17 und schreibt je
rem Filmsekunde Szenenzeit, Auge und die Schiffe mit Lotse (Phase, Ort, Abstand, Richtung): -Dausgabe.flotte=an (neu in Ausgabe).
rem 1 Steuerhaus, HalligHooge bauen (Renderer unveraendert seit L6c3); 2 Diagnose.
rem Ausgaben: HalligHooge\docs\status\Status_L6c3b_*.txt
rem Erwartete Dauer: rund 9 min, hoechstens 15 min [geschaetzt: die Schnitte warten auf die Seegangsflaeche]. Gestartet von Claude per Explorer.
rem Anhalten zwischen zwei Schritten: eine Datei STOPP.txt neben dieses Skript legen.
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0.."

set "P=C:\Users\Dan40810\Documents\NetBeansProjects"
set "HH=%P%\HalligHooge"
set "SH=%P%\Steuerhaus"
set "RE=%P%\Renderer"
set "ST=%HH%\docs\status"
set "VI=%HH%\docs\video"
set "SZ=%HH%\src\szenen\Hooge.szenen"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
set "R=java %U% --enable-native-access=ALL-UNNAMED -Xmx16g -cp dist\Renderer.jar"
set "V=-Dcore.gpu.validierung=true"
set "J=java %U% --enable-native-access=ALL-UNNAMED -Xmx12g -cp dist\HalligHooge.jar"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"

if not exist "%ST%" mkdir "%ST%"
if not exist "%VI%" mkdir "%VI%"
set "UE=%ST%\Status_L6c3b_.txt"
set "L1=%ST%\Status_L6c3b_1.txt"
set "L2=%ST%\Status_L6c3b_2.txt"
set "L3=%ST%\Status_L6c3b_3.txt"
set "L4=%ST%\Status_L6c3b_4.txt"
set "E=0"
set "EF=0"
>"%UE%" echo Lauf L6c3b (S8e2 Diagnose Adler), gestartet %DATE% %TIME%
>"%L1%" echo Status_L6c3b_1: Steuerhaus, HalligHooge bauen
>"%L2%" echo Status_L6c3b_2: Diagnose Flotte

set "L=%L1%"
pushd "%SH%"
set "N=1b Steuerhaus bauen (clean jar)"
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
where ffmpeg >>"%L1%" 2>&1
where ffprobe >>"%L1%" 2>&1

pushd "%HH%"
set "N=2 Diagnose: Seegang bis 1:17 (640 x 360, 1 Teilbild, ohne Ton, Flotte je Filmsekunde)"
set "L=%L2%"
set "C=java %U% -Dausgabe.flotte=an --enable-native-access=ALL-UNNAMED -Xmx12g -cp dist\HalligHooge.jar com.dan.steuerhaus.ausgabe.Ausgabe --manifest src\szenen\Hooge.steuerhaus --regie "Seegang" --groesse 640x360 --teilbilder 1 --ohne-ton --bis 1850 --ziel %VI%\S8e_diag.mp4"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"
popd

>>"%UE%" echo.
if not "%EF%"=="0" >>"%UE%" echo Mindestens ein Schritt mit Exit ungleich 0 - siehe die Statusdateien
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
