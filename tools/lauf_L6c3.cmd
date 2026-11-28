@echo off
rem Seegang Lauf L6c3 (Seegang Werkbuch, Datei 104): S8e2 - Seegang und Schiffe im Video.
rem Befund L6c2 (Durchsicht der Standbilder): Im Video fehlten Seegang, Brandung und Schiffe. Buehne.filmbild (W4) fuehrte
rem weder die Flotte noch den Wart der Seegangsflaeche; beides kam erst nach W4 in die Bildschleife des Fensters. Jetzt wie
rem dort, auf der Filmuhr; nach einem Schnitt wartet das Filmbild, bis die Flaeche fuer den Blick steht.
rem 1 Renderer, Steuerhaus, HalligHooge bauen; 2 AusgabeCheck (Pruefsteine 1-5, 7 der Ausgabe: bleibt sie bitgleich?);
rem 3 Probe: die ersten 1:19 des Videos in 720p (Brandung, Westspitze, Adler, Anfang an Bord); 4 Standbilder daraus.
rem Das ganze Video in 1080p kommt danach als eigener Lauf (L6c4), wenn die Probe passt.
rem Ausgaben: HalligHooge\docs\status\Status_L6c3_*.txt; HalligHooge\docs\video\S8e_test.mp4; S8e_pruef\test_t*.png.
rem Erwartete Dauer: rund 25 min, hoechstens 40 min [geschaetzt]. Gestartet von Claude per Explorer.
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
set "UE=%ST%\Status_L6c3_.txt"
set "L1=%ST%\Status_L6c3_1.txt"
set "L2=%ST%\Status_L6c3_2.txt"
set "L3=%ST%\Status_L6c3_3.txt"
set "L4=%ST%\Status_L6c3_4.txt"
set "E=0"
set "EF=0"
>"%UE%" echo Lauf L6c3 (S8e2 Probe), gestartet %DATE% %TIME%
>"%L1%" echo Status_L6c3_1: Renderer, Steuerhaus, HalligHooge bauen
>"%L2%" echo Status_L6c3_2: AusgabeCheck
>"%L3%" echo Status_L6c3_3: Probe des Videos (720p, bis 1:19)
>"%L4%" echo Status_L6c3_4: Standbilder der Probe

set "N=1 Renderer bauen (clean jar, SPIR-V, check-packages)"
set "L=%L1%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" goto :abbruch
copy /y "%RE%\dist\Renderer.jar" "%SH%\lib\Renderer.jar" >>"%L1%" 2>&1
copy /y "%RE%\dist\Renderer.jar" "%HH%\lib\Renderer.jar" >>"%L1%" 2>&1
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
set "N=2 AusgabeCheck (Pruefsteine 1-5, 7)"
set "L=%L2%"
set "C=%J% com.dan.steuerhaus.demo.AusgabeCheck --manifest src\szenen\Hooge.steuerhaus"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=3 Probe: Seegang bis 1:19 (720p, 24 B/s, 8 Teilbilder, mit Ton)"
set "L=%L3%"
set "C=%J% com.dan.steuerhaus.ausgabe.Ausgabe --manifest src\szenen\Hooge.steuerhaus --regie "Seegang" --groesse 1280x720 --bis 1900 --ziel %VI%\S8e_test.mp4"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"
popd

set "PB=%VI%\S8e_pruef"
if not exist "%PB%" mkdir "%PB%"
>>"%UE%" echo [%DATE% %TIME%] Start  4 Standbilder der Probe
for %%T in (5 10 22 30 35 45 55 60 66 72 78) do (
  ffmpeg -v error -y -ss %%T -i "%VI%\S8e_test.mp4" -frames:v 1 -vf scale=960:-1 "%PB%\test_t%%T.png" >>"%L4%" 2>&1
  >>"%L4%" echo t=%%T
)
>>"%UE%" echo [%DATE% %TIME%] Ende   4 Standbilder der Probe
type "%VI%\S8e_test.mp4.txt" >>"%L4%" 2>&1

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
