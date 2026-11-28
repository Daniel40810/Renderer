@echo off
rem Seegang Lauf L7g (Seegang Werkbuch, Datei 107): S9b-5 - was wird im laufenden Bild nach rund 10 min teurer?
rem L7f: das stehende Bild (Thermometer) bleibt 35,5-36,8 ms, das laufende springt nach rund 6,5 min Szenenzeit von 38 auf
rem 50 ms und bleibt dort. Nicht die Waerme der Karte, sondern ein Zustand der Szene. Jetzt je 30 s: Bau der Flaeche, Zahl
rem der Baue, Phase, Art der Flotte und die Abschnitte gegen das erste Fenster.
rem 1 Renderer bauen; 2 WaermeCheck --bloecke 5 --ohne-ruhe (anleger_see).
rem Ausgaben: HalligHooge\docs\status\Status_L7g_*.txt, S9b_Waerme_L7g.md.
rem Erwartete Dauer: rund 18 min, hoechstens 25 min [Bau 2, Vorlauf 1, Last 15].
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
set "UE=%ST%\Status_L7g_.txt"
set "L1=%ST%\Status_L7g_1.txt"
set "L2=%ST%\Status_L7g_2.txt"
set "E=0"
set "EF=0"
>"%UE%" echo Lauf L7g (S9b-5 Karte unter Dauerlast), gestartet %DATE% %TIME%
>"%L1%" echo Status_L7g_1: Renderer bauen
>"%L2%" echo Status_L7g_2: WaermeCheck

set "N=1 Renderer bauen (clean jar, SPIR-V, check-packages)"
set "L=%L1%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" goto :abbruch
copy /y "%RE%\dist\Renderer.jar" "%SH%\lib\Renderer.jar" >>"%L1%" 2>&1
copy /y "%RE%\dist\Renderer.jar" "%HH%\lib\Renderer.jar" >>"%L1%" 2>&1
set "N=2 WaermeCheck --bloecke 5 --ohne-ruhe (anleger_see: Zustand je 30 s)"
set "L=%L2%"
set "C=%R% com.dan.lauf.demo.WaermeCheck --szenen %SZ% --bloecke 5 --ohne-ruhe"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"
copy /y "%RE%\docs\status\S9b_Waerme.md" "%ST%\S9b_Waerme_L7g.md" >>"%L2%" 2>&1

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
