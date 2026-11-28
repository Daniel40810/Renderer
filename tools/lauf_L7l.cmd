@echo off
rem Seegang Lauf L7l (Seegang Werkbuch, Datei 109): S9d-1 (SF185 A, Hoehe des Auges fuer die Himmelsansicht erst ab 10 m),
rem Eichlast mit Vorlast (SF184 A), Abnahme zweimal hintereinander ohne Warmlauf (SF186 A), Mitschrift der Maschine dabei.
rem 0 Mitschrift der Maschine im Hintergrund (mitschrift_L7k.ps1, nur lesen, alle 2 s) bis zum Ende des Laufs;
rem 1 Renderer bauen; 2 SeegangAbnahme, erste; 3 SeegangAbnahme, zweite (beide mit Eichlast, Vorlast, Pruefstein 47).
rem Ausgaben: HalligHooge\docs\status\Status_L7l_*.txt, S9l_Maschine.csv, S9l_Abnahme_1.md, S9l_Abnahme_2.md, S9l_1_*.png, S9l_2_*.png.
rem Erwartete Dauer: rund 30 min, hoechstens 45 min [Bau 2, je Abnahme 11 bis 14].
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
set "UE=%ST%\Status_L7l_.txt"
set "L1=%ST%\Status_L7l_1.txt"
set "L2=%ST%\Status_L7l_2.txt"
set "L3=%ST%\Status_L7l_3.txt"
set "MS=%ST%\S9l_Maschine.csv"
set "MX=%ST%\S9l_Maschine.stopp"
set "E=0"
set "EF=0"
>"%UE%" echo Lauf L7l (S9d-1 Hoehe fuer die Himmelsansicht, Eichlast mit Vorlast, zwei Abnahmen), gestartet %DATE% %TIME%
>"%L1%" echo Status_L7l_1: Renderer bauen
>"%L2%" echo Status_L7l_2: SeegangAbnahme, erste
>"%L3%" echo Status_L7l_3: SeegangAbnahme, zweite

if exist "%MX%" del "%MX%"
start "Mitschrift L7l" /min powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0mitschrift_L7k.ps1" -Aus "%MS%" -Stopp "%MX%" -Minuten 60
>>"%UE%" echo [%DATE% %TIME%] Mitschrift der Maschine gestartet: %MS%

set "N=1 Renderer bauen (clean jar, SPIR-V, check-packages)"
set "L=%L1%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" goto :abbruch
copy /y "%RE%\dist\Renderer.jar" "%SH%\lib\Renderer.jar" >>"%L1%" 2>&1
copy /y "%RE%\dist\Renderer.jar" "%HH%\lib\Renderer.jar" >>"%L1%" 2>&1

set "N=2 SeegangAbnahme, erste (ohne Warmlauf, Eichlast mit Vorlast)"
set "L=%L2%"
set "C=%R% com.dan.lauf.demo.SeegangAbnahme --szenen %SZ% --aus S9d1a"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"
copy /y "%RE%\docs\status\S9d1a_Abnahme.md" "%ST%\S9l_Abnahme_1.md" >>"%L2%" 2>&1
for %%F in ("%RE%\docs\status\S9d1a_*_*.png") do copy /y "%%F" "%ST%\S9l_1_%%~nxF" >>"%L2%" 2>&1

set "N=3 SeegangAbnahme, zweite (gleich danach)"
set "L=%L3%"
set "C=%R% com.dan.lauf.demo.SeegangAbnahme --szenen %SZ% --aus S9d1b"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"
copy /y "%RE%\docs\status\S9d1b_Abnahme.md" "%ST%\S9l_Abnahme_2.md" >>"%L3%" 2>&1
for %%F in ("%RE%\docs\status\S9d1b_*_*.png") do copy /y "%%F" "%ST%\S9l_2_%%~nxF" >>"%L3%" 2>&1

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
