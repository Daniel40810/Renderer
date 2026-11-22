@echo off
rem P13c, Lauf C4 (Werkbuch HalligHooge Teil A, 12.14, Datei 102): Diagnose A35 - die Westspitze am 5.12. 14:30 MEZ
rem zwoelfmal, je mit einem Teil aus (Bewuchs, Gestalt, Haeuser, Brandung, Seegang, Kraeuselung, Spiegelung des
rem Gelaendes, Teile des Wassers). 1 Renderer bauen; 2 BrandungBildCheck --teil diagnose.
rem Ausgaben: HalligHooge\docs\status\Status_Phase13cC4_*.txt; Attest Renderer\docs\status\P13c_Diagnose.md;
rem Bild Renderer\docs\status\P13c_Diagnose.png. Erwartete Dauer: rund 2 min.
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0.."

set "HH=C:\Users\Dan40810\Documents\NetBeansProjects\HalligHooge"
set "ST=%HH%\docs\status"
set "SZ=%HH%\src\szenen\Hooge.szenen"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
set "R=java %U% --enable-native-access=ALL-UNNAMED -Xmx16g -cp dist\Renderer.jar"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"

set "UE=%ST%\Status_Phase13cC4_.txt"
set "L1=%ST%\Status_Phase13cC4_1.txt"
set "L2=%ST%\Status_Phase13cC4_2.txt"
set "E=0"
set "EF=0"

for /f %%t in ('powershell -NoProfile -Command "[DateTime]::Now.Ticks"') do set "T0=%%t"
>"%UE%" echo Lauf P13c C4, gestartet %DATE% %TIME%
>"%L1%" echo Status_Phase13cC4_1: Renderer bauen (clean jar, check-packages)
>"%L2%" echo Status_Phase13cC4_2: Diagnose A35

set "N=1 Renderer bauen (clean jar, check-packages)"
set "L=%L1%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" goto :abbruch

set "N=2 Diagnose A35 (Westspitze, je ein Teil aus)"
set "L=%L2%"
set "C=%R% com.dan.lauf.demo.BrandungBildCheck --szenen %SZ% --teil diagnose"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

>>"%UE%" echo.
if not "%EF%"=="0" >>"%UE%" echo Mindestens ein Schritt mit Exit ungleich 0 - siehe die Statusdateien und Atteste
>>"%UE%" echo FERTIG %DATE% %TIME%
echo.
echo Fertig. Uebersicht: %UE%
timeout /t 20 >nul
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
