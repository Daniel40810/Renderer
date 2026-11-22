@echo off
rem P12a, Lauf A2 (Werkbuch HalligHooge Teil A, 12.13, Datei 67): nach Lauf A - Zeiten der Haeuser (Pruefstein 6),
rem Maske fuer Spiegelbilder (Pruefstein 9), dunklerer Backstein.
rem 1 Renderer bauen; 2 Aufbereitung, Schritt gebaeude; 3 GebaeudeCheck; 4 HausCheck.
rem Ausgaben: HalligHooge\docs\status\Status_Phase12aA2_*.txt; Atteste Renderer\docs\status\P12a_Gebaeude.md,
rem P12a_Haeuser.md; Bilder Renderer\docs\status\P12a_Warften*.png.
rem Erwartete Dauer: rund 4 Minuten.
rem Anhalten zwischen zwei Schritten: eine Datei STOPP.txt neben dieses Skript legen.
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0.."

set "HH=C:\Users\Dan40810\Documents\NetBeansProjects\HalligHooge"
set "ST=%HH%\docs\status"
set "SZ=%HH%\src\szenen\Hooge.szenen"
set "G=D:\Daten\HalligHooge\gebiet"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
set "R=java %U% --enable-native-access=ALL-UNNAMED -Xmx16g -cp dist\Renderer.jar"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"

set "UE=%ST%\Status_Phase12aA2_.txt"
set "L1=%ST%\Status_Phase12aA2_1.txt"
set "L2=%ST%\Status_Phase12aA2_2.txt"
set "L3=%ST%\Status_Phase12aA2_3.txt"
set "L4=%ST%\Status_Phase12aA2_4.txt"
set "E=0"
set "EF=0"

for /f %%t in ('powershell -NoProfile -Command "[DateTime]::Now.Ticks"') do set "T0=%%t"
>"%UE%" echo Lauf P12a A2, gestartet %DATE% %TIME%
>"%L1%" echo Status_Phase12aA2_1: Renderer bauen (clean jar, check-packages)
>"%L2%" echo Status_Phase12aA2_2: Aufbereitung, Schritt gebaeude
>"%L3%" echo Status_Phase12aA2_3: GebaeudeCheck
>"%L4%" echo Status_Phase12aA2_4: HausCheck

set "N=1 Renderer bauen (clean jar, check-packages)"
set "L=%L1%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" goto :abbruch
powershell -NoProfile -Command "if ((Get-Item 'dist\Renderer.jar').LastWriteTime.Ticks -gt %T0%) { exit 0 } else { exit 1 }"
if errorlevel 1 (
  >>"%UE%" echo dist\Renderer.jar ist nicht neuer als der Start des Laufs
  set "N=1 Renderer bauen (jar nicht neu)"
  goto :abbruch
)
>>"%UE%" echo dist\Renderer.jar ist neu

set "N=2 Aufbereitung, Schritt gebaeude"
set "L=%L2%"
set "C=java %U% -Xmx8g -cp dist\Renderer.jar com.dan.aufbereitung.Aufbereitung ..\HalligHooge\gebiet\Hooge.rezept --schritte gebaeude"
call :lauf
if not "%E%"=="0" goto :abbruch

set "N=3 GebaeudeCheck"
set "L=%L3%"
set "C=java %U% -Xmx4g -cp dist\Renderer.jar com.dan.aufbereitung.demo.GebaeudeCheck --gebiet %G%"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=4 HausCheck"
set "L=%L4%"
set "C=%R% com.dan.lauf.demo.HausCheck --szenen %SZ%"
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
