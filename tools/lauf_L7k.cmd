@echo off
rem Seegang Lauf L7k (Seegang Werkbuch, Datei 108): S9c-2 (SF180 A) Eichlast in der Abnahme, SF181 A Mitschrift der Maschine.
rem 0 Mitschrift der Maschine im Hintergrund (mitschrift_L7k.ps1, nur lesen, alle 2 s) bis zum Ende des Laufs;
rem 1 Renderer bauen; 2 SeegangAbnahme ohne Warmlauf mit Eichlast vor und nach jeder Stufe, gestoerte Stufen wiederholt;
rem 3 BildzeitDiagnose (Pruefstein 46 nach SF179).
rem Ausgaben: HalligHooge\docs\status\Status_L7k_*.txt, S9k_Maschine.csv, S9k_Abnahme.md, S9k_*.png, S9k_Bildzeit.md.
rem Erwartete Dauer: rund 25 min, hoechstens 40 min [Bau 2, Abnahme 13 bis 18, BildzeitDiagnose 7].
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
set "UE=%ST%\Status_L7k_.txt"
set "L1=%ST%\Status_L7k_1.txt"
set "L2=%ST%\Status_L7k_2.txt"
set "L3=%ST%\Status_L7k_3.txt"
set "MS=%ST%\S9k_Maschine.csv"
set "MX=%ST%\S9k_Maschine.stopp"
set "E=0"
set "EF=0"
>"%UE%" echo Lauf L7k (S9c-2 Eichlast, Mitschrift der Maschine), gestartet %DATE% %TIME%
>"%L1%" echo Status_L7k_1: Renderer bauen
>"%L2%" echo Status_L7k_2: SeegangAbnahme mit Eichlast
>"%L3%" echo Status_L7k_3: BildzeitDiagnose

if exist "%MX%" del "%MX%"
start "Mitschrift L7k" /min powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0mitschrift_L7k.ps1" -Aus "%MS%" -Stopp "%MX%" -Minuten 70
>>"%UE%" echo [%DATE% %TIME%] Mitschrift der Maschine gestartet: %MS%

set "N=1 Renderer bauen (clean jar, SPIR-V, check-packages)"
set "L=%L1%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" goto :abbruch
copy /y "%RE%\dist\Renderer.jar" "%SH%\lib\Renderer.jar" >>"%L1%" 2>&1
copy /y "%RE%\dist\Renderer.jar" "%HH%\lib\Renderer.jar" >>"%L1%" 2>&1

set "N=2 SeegangAbnahme ohne Warmlauf, mit Eichlast (SF180 A)"
set "L=%L2%"
set "C=%R% com.dan.lauf.demo.SeegangAbnahme --szenen %SZ%"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"
copy /y "%RE%\docs\status\S9c2_Abnahme.md" "%ST%\S9k_Abnahme.md" >>"%L2%" 2>&1
for %%F in ("%RE%\docs\status\S9c2_*_*.png") do copy /y "%%F" "%ST%\S9k_%%~nxF" >>"%L2%" 2>&1

set "N=3 BildzeitDiagnose (Pruefstein 46 nach SF179)"
set "L=%L3%"
set "C=%R% com.dan.lauf.demo.BildzeitDiagnose --szenen %SZ%"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"
copy /y "%RE%\docs\status\S9b_Bildzeit.md" "%ST%\S9k_Bildzeit.md" >>"%L3%" 2>&1

>"%MX%" echo stopp
timeout /t 5 >nul
>>"%UE%" echo [%DATE% %TIME%] Mitschrift der Maschine angehalten
>>"%UE%" echo.
if not "%EF%"=="0" >>"%UE%" echo Mindestens ein Schritt mit Exit ungleich 0 - siehe die Statusdateien
>>"%UE%" echo FERTIG %DATE% %TIME%
echo.
echo Fertig. Uebersicht: %UE%
timeout /t 5 >nul
goto :eof

:abbruch
>"%MX%" echo stopp
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
