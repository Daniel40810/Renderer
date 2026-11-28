@echo off
rem Seegang Lauf L7i (Seegang Werkbuch, Datei 108): S9c-0 (SF177 A) - was aendert sich in anleger_see um 14:37 UTC?
rem Die Ansicht beginnt 14:35 (+300 s); je 300 Bilder laufend: Median, Schwimmer im Umkreis von 3 km (Abstand, Richtung,
rem Phase des Lotsen), Abschnitte gegen die erste Runde; PNG am Anfang und nach dem Sprung mit dem stehenden Bild je Abschnitt.
rem 1 Renderer bauen; 2 WaermeCheck --sprung --versatz 300 (anleger_see).
rem Ausgaben: HalligHooge\docs\status\Status_L7i_*.txt, S9c0_Sprung.md, S9c0_*.png.
rem Erwartete Dauer: rund 8 min, hoechstens 15 min.
rem Gestartet von Claude per Explorer. Anhalten zwischen zwei Schritten: eine Datei STOPP.txt neben dieses Skript legen.
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
set "UE=%ST%\Status_L7i_.txt"
set "L1=%ST%\Status_L7i_1.txt"
set "L2=%ST%\Status_L7i_2.txt"
set "E=0"
set "EF=0"
>"%UE%" echo Lauf L7i (S9b-5 Karte unter Dauerlast), gestartet %DATE% %TIME%
>"%L1%" echo Status_L7i_1: Renderer bauen
>"%L2%" echo Status_L7i_2: WaermeCheck

set "N=1 Renderer bauen (clean jar, SPIR-V, check-packages)"
set "L=%L1%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" goto :abbruch
copy /y "%RE%\dist\Renderer.jar" "%SH%\lib\Renderer.jar" >>"%L1%" 2>&1
copy /y "%RE%\dist\Renderer.jar" "%HH%\lib\Renderer.jar" >>"%L1%" 2>&1
set "N=2 WaermeCheck --sprung --versatz 300 (anleger_see)"
set "L=%L2%"
set "C=%R% com.dan.lauf.demo.WaermeCheck --szenen %SZ% --sprung --versatz 300"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"
copy /y "%RE%\docs\status\S9b_Waerme.md" "%ST%\S9c0_Sprung.md" >>"%L2%" 2>&1
copy /y "%RE%\docs\status\S9c0_*.png" "%ST%\" >>"%L2%" 2>&1

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
