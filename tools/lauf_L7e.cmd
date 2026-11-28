@echo off
rem Seegang Lauf L7e (Seegang Werkbuch, Datei 107): S9b-5 - wird die Karte unter Dauerlast langsamer?
rem Anlass: L7d ohne Warmlauf misst in Hoch rund 20 % schneller als L7b und L7c nach 3 min Warmlauf (brandung 38 gegen 45 ms,
rem westspitze 37 gegen 46, Xaver westspitze 35 gegen 47), auch in Ansichten, die nach dem Warmlauf mit ihrer eigenen Zeit
rem beginnen. WaermeCheck: das stehende Bild als Thermometer, dann 2 x 180 s Dauerlast, dann 120 s und 300 s Ruhe.
rem 1 Renderer bauen (Steuerhaus und HalligHooge unveraendert); 2 WaermeCheck (anleger_see).
rem Ausgaben: HalligHooge\docs\status\Status_L7e_*.txt, S9b_Waerme.md.
rem Erwartete Dauer: rund 17 min, hoechstens 25 min [Bau 2, Vorlauf 1, Last 6, Ruhe 7].
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
set "UE=%ST%\Status_L7e_.txt"
set "L1=%ST%\Status_L7e_1.txt"
set "L2=%ST%\Status_L7e_2.txt"
set "E=0"
set "EF=0"
>"%UE%" echo Lauf L7e (S9b-5 Karte unter Dauerlast), gestartet %DATE% %TIME%
>"%L1%" echo Status_L7e_1: Renderer bauen
>"%L2%" echo Status_L7e_2: WaermeCheck

set "N=1 Renderer bauen (clean jar, SPIR-V, check-packages)"
set "L=%L1%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" goto :abbruch
copy /y "%RE%\dist\Renderer.jar" "%SH%\lib\Renderer.jar" >>"%L1%" 2>&1
copy /y "%RE%\dist\Renderer.jar" "%HH%\lib\Renderer.jar" >>"%L1%" 2>&1
set "N=2 WaermeCheck (anleger_see: Thermometer, Dauerlast, Ruhe)"
set "L=%L2%"
set "C=%R% com.dan.lauf.demo.WaermeCheck --szenen %SZ%"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"
copy /y "%RE%\docs\status\S9b_Waerme.md" "%ST%\S9b_Waerme.md" >>"%L2%" 2>&1

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
