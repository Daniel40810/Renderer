@echo off
rem Seegang Lauf L7m (Seegang Werkbuch, Datei 110): S9d-2 messen (SF188 A), Abnahme mit Bildzeit als Waechter (SF187 A)
rem und festem Messbeginn (SF189 A), Mitschrift der Maschine dabei.
rem 0 Mitschrift der Maschine im Hintergrund (mitschrift_L7k.ps1, nur lesen, alle 2 s) bis zum Ende des Laufs;
rem 1 Renderer bauen; 2 DurchgangDiagnose (Bord Kutter an 12 Zeiten, Xaver Hanswarft im Wechsel); 3 SeegangAbnahme.
rem Ausgaben: HalligHooge\docs\status\Status_L7m_*.txt, S9m_Maschine.csv, S9m_Durchgaenge.md/.csv, S9m_bord_*.png, S9m_Abnahme.md, S9m_*.png.
rem Erwartete Dauer: rund 25 min, hoechstens 40 min [Bau 2, Diagnose 6 bis 9, Abnahme 12 bis 16].
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
set "UE=%ST%\Status_L7m_.txt"
set "L1=%ST%\Status_L7m_1.txt"
set "L2=%ST%\Status_L7m_2.txt"
set "L3=%ST%\Status_L7m_3.txt"
set "MS=%ST%\S9m_Maschine.csv"
set "MX=%ST%\S9m_Maschine.stopp"
set "E=0"
set "EF=0"
>"%UE%" echo Lauf L7m (S9d-2 Durchgaenge, Abnahme mit festem Messbeginn), gestartet %DATE% %TIME%
>"%L1%" echo Status_L7m_1: Renderer bauen
>"%L2%" echo Status_L7m_2: DurchgangDiagnose (S9d-2)
>"%L3%" echo Status_L7m_3: SeegangAbnahme (fester Messbeginn)

if exist "%MX%" del "%MX%"
start "Mitschrift L7m" /min powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0mitschrift_L7k.ps1" -Aus "%MS%" -Stopp "%MX%" -Minuten 60
>>"%UE%" echo [%DATE% %TIME%] Mitschrift der Maschine gestartet: %MS%

set "N=1 Renderer bauen (clean jar, SPIR-V, check-packages)"
set "L=%L1%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" goto :abbruch
copy /y "%RE%\dist\Renderer.jar" "%SH%\lib\Renderer.jar" >>"%L1%" 2>&1
copy /y "%RE%\dist\Renderer.jar" "%HH%\lib\Renderer.jar" >>"%L1%" 2>&1

set "N=2 DurchgangDiagnose (S9d-2: Bord Kutter am Kurs, Xaver laufend)"
set "L=%L2%"
set "C=%R% com.dan.lauf.demo.DurchgangDiagnose --szenen %SZ%"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"
copy /y "%RE%\docs\status\S9d2_Durchgaenge.md" "%ST%\S9m_Durchgaenge.md" >>"%L2%" 2>&1
copy /y "%RE%\docs\status\S9d2_Durchgaenge.csv" "%ST%\S9m_Durchgaenge.csv" >>"%L2%" 2>&1
for %%F in ("%RE%\docs\status\S9d2_bord_*.png") do copy /y "%%F" "%ST%\S9m_%%~nxF" >>"%L2%" 2>&1

set "N=3 SeegangAbnahme (Bildzeit als Waechter, fester Messbeginn)"
set "L=%L3%"
set "C=%R% com.dan.lauf.demo.SeegangAbnahme --szenen %SZ% --aus S9d2"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"
copy /y "%RE%\docs\status\S9d2_Abnahme.md" "%ST%\S9m_Abnahme.md" >>"%L3%" 2>&1
for %%F in ("%RE%\docs\status\S9d2_*_*.png") do copy /y "%%F" "%ST%\S9m_%%~nxF" >>"%L3%" 2>&1

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
