@echo off
rem Seegang Lauf L7a2 (Seegang Werkbuch, Datei 106): S9a Leistung - Messung je Teil der Schattierung (SF164, SF168-SF170 A).
rem Neu gegen L7a (abgebrochen): Die Zeit driftete nach dem Vorlauf (erste Ansicht 55 -> 37 ms); jetzt misst LeistungCheck die
rem Schalter an einem stehenden Bild, je Schalter drei Bloecke ohne und mit im Wechsel, und wartet vorher auch auf die
rem Koerperflaeche. Am Anfang 5 min Pause, damit die Karte kalt ist.
rem Neu: Messschalter je Teil im Netz des Wassers (Wasserbild.mess: Clipmap, Weisswasser, Schaumkoepfe, Schaumfeld, Muster,
rem Auflauf, Werte des Seegangs, Suche B47, Saeule, Schatten der Haeuser); mit 0 bitgleich zum Stand davor (im Container
rem geprueft: SeegangNetzCheck, 6 Ansichten). LeistungCheck misst die acht Ansichten der Abnahme in Hoch (1920 x 1080):
rem das ganze Bild, dann je einen Teil aus, dazu Karte (see_koerper) und Faden der Flotte; im Schwimmerblick je Bild unter
rem Wasser und Linse; am Ende die erste Ansicht noch einmal (kalt gegen warm). Danach SchwimmerCheck Teil 4 (A30, Vergleich).
rem 1 Renderer, Steuerhaus, HalligHooge bauen; 2 LeistungCheck; 3 SchwimmerCheck Teil 4 (ohne Validierung).
rem Ausgaben: HalligHooge\docs\status\Status_L7a2_*.txt; Renderer\docs\status\S9a_Leistung.md und .csv.
rem Erwartete Dauer: rund 45 min, hoechstens 60 min [geschaetzt: Pause 5 min, LeistungCheck rund 30 min, SchwimmerCheck Teil 4
rem 9 min]. Gestartet von Claude per Explorer. Der PC sollte vorher ruhen (kalt).
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
set "UE=%ST%\Status_L7a2_.txt"
set "L1=%ST%\Status_L7a2_1.txt"
set "L2=%ST%\Status_L7a2_2.txt"
set "L3=%ST%\Status_L7a2_3.txt"
set "L4=%ST%\Status_L7a2_4.txt"
set "E=0"
set "EF=0"
>"%UE%" echo Lauf L7a2 (S9a Leistung), gestartet %DATE% %TIME%
>"%L1%" echo Status_L7a2_1: Renderer, Steuerhaus, HalligHooge bauen
>"%L2%" echo Status_L7a2_2: LeistungCheck
>"%L3%" echo Status_L7a2_3: SchwimmerCheck Teil 4

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

set "N=2 LeistungCheck (acht Ansichten, je Teil aus; kalt gegen warm)"
set "L=%L2%"
set "C=%R% com.dan.lauf.demo.LeistungCheck --szenen %SZ% --pause 300"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"
copy /y "%RE%\docs\status\S9a_Leistung.md" "%ST%\S9a_Leistung.md" >>"%L2%" 2>&1
copy /y "%RE%\docs\status\S9a_Leistung.csv" "%ST%\S9a_Leistung.csv" >>"%L2%" 2>&1

set "N=3 SchwimmerCheck Teil 4 (A30: Karte und Faden; ohne Validierung)"
set "L=%L3%"
set "C=%R% com.dan.lauf.demo.SchwimmerCheck --szenen %SZ% --teil 4"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

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
