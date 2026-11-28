@echo off
rem Seegang Lauf L6c (Seegang Werkbuch, Datei 104): S8e - das Video zum Regiebuch "Seegang" (2:30 min, 1080p, 24 Bilder/s,
rem 8 Teilbilder, Korn, mit Ton: Wind und Brandung als Teppich, dazu die Ereignisse - Brecher, Ueberlauf, Bugschlag, Typhon,
rem Maschinen). Danach die Pruefung der Datei (AusgabeCheck --datei: Bilder, Groesse, Bildrate) und der Tonspur (ffprobe).
rem Vorher die letzten Aenderungen aus L6b2 nachgeprueft: Tropfen nur ueber der Wasserlinie, Kopie unter Wasser nur im Band des
rem Meniskus (SeegangS8cdCheck).
rem 1 Renderer, Steuerhaus, HalligHooge bauen (wie L6b2); 2 SeegangS8cdCheck Teil 1-3 mit Validierung; 3 Ausgabe --regie
rem "Seegang"; 4 AusgabeCheck --datei; 5 Tonspur.
rem Ausgaben: HalligHooge\docs\status\Status_L6c_*.txt; Attest Renderer\docs\status\S8cd_Seegang.md;
rem HalligHooge\docs\video\S8e_Seegang.mp4 (und .txt).
rem Erwartete Dauer: rund 45 min, hoechstens 60 min [geschaetzt: Xaver brauchte 0,53 s je Filmbild]. Gestartet von Claude per Explorer.
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
set "UE=%ST%\Status_L6c_.txt"
set "L1=%ST%\Status_L6c_1.txt"
set "L2=%ST%\Status_L6c_2.txt"
set "L3=%ST%\Status_L6c_3.txt"
set "L4=%ST%\Status_L6c_4.txt"
set "E=0"
set "EF=0"
>"%UE%" echo Lauf L6c (S8e Video), gestartet %DATE% %TIME%
>"%L1%" echo Status_L6c_1: Renderer, Steuerhaus, HalligHooge bauen
>"%L2%" echo Status_L6c_2: SeegangS8cdCheck mit Validierung
>"%L3%" echo Status_L6c_3: Video Seegang
>"%L4%" echo Status_L6c_4: Pruefung der Datei und der Tonspur

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

set "N=2 SeegangS8cdCheck Teil 1-3 (mit Validierung)"
set "L=%L2%"
set "C=%R% %V% com.dan.lauf.demo.SeegangS8cdCheck --szenen %SZ%"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

pushd "%HH%"
set "N=3 Video: Seegang (1080p, 24 B/s, 8 Teilbilder, mit Ton)"
set "L=%L3%"
set "C=%J% com.dan.steuerhaus.ausgabe.Ausgabe --manifest src\szenen\Hooge.steuerhaus --regie "Seegang" --groesse 1080p --ziel %VI%\S8e_Seegang.mp4"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=4 AusgabeCheck --datei (Bilder, Groesse, Bildrate)"
set "L=%L4%"
set "C=%J% com.dan.steuerhaus.demo.AusgabeCheck --datei %VI%\S8e_Seegang.mp4"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"
set "N=5 Tonspur (ffprobe)"
set "C=ffprobe -v error -select_streams a:0 -show_entries stream=codec_name,sample_rate,channels,duration -of default=noprint_wrappers=1 %VI%\S8e_Seegang.mp4"
call :lauf
set "E=0"
popd
type "%VI%\S8e_Seegang.mp4.txt" >>"%L4%" 2>&1

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
