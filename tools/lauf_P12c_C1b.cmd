@echo off
rem P12c, Lauf C1b (Werkbuch HalligHooge Teil A, 12.13, Datei 77): die Gehoelze aus bDOM, DGM1 und DOP20 (F136 A).
rem 1 Renderer bauen; 2 Aufbereitung, Schritt gehoelz; 3 GehoelzCheck (Pruefstein 17, Daten).
rem Ausgaben: HalligHooge\docs\status\Status_Phase12cC1b_*.txt; Attest Renderer\docs\status\P12c_Gehoelz.md;
rem Gehoelze D:\Daten\HalligHooge\gebiet\bau\gehoelz.bin, gehoelz.txt.
rem Erwartete Dauer: rund 3 Minuten.
rem Anhalten zwischen zwei Schritten: eine Datei STOPP.txt neben dieses Skript legen.
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0.."

set "HH=C:\Users\Dan40810\Documents\NetBeansProjects\HalligHooge"
set "ST=%HH%\docs\status"
set "SZ=%HH%\src\szenen\Hooge.szenen"
set "G=D:\Daten\HalligHooge\gebiet"
set "A6=D:\Daten\HalligHooge\_p6\archiv"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
set "R=java %U% --enable-native-access=ALL-UNNAMED -Xmx16g -cp dist\Renderer.jar"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"

set "UE=%ST%\Status_Phase12cC1b_.txt"
set "L1=%ST%\Status_Phase12cC1b_1.txt"
set "L2=%ST%\Status_Phase12cC1b_2.txt"
set "L3=%ST%\Status_Phase12cC1b_3.txt"
set "E=0"
set "EF=0"

for /f %%t in ('powershell -NoProfile -Command "[DateTime]::Now.Ticks"') do set "T0=%%t"
>"%UE%" echo Lauf P12c C1b, gestartet %DATE% %TIME%
>"%L1%" echo Status_Phase12cC1b_1: Renderer bauen (clean jar, check-packages)
>"%L2%" echo Status_Phase12cC1b_2: Aufbereitung, Schritt gehoelz
>"%L3%" echo Status_Phase12cC1b_3: GehoelzCheck (Pruefstein 17, Daten)

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

set "N=2 Aufbereitung, Schritt gehoelz"
set "L=%L2%"
set "C=java %U% -Xmx12g -cp dist\Renderer.jar com.dan.aufbereitung.Aufbereitung ..\HalligHooge\gebiet\Hooge.rezept --schritte gehoelz"
call :lauf
if not "%E%"=="0" goto :abbruch

set "N=3 GehoelzCheck (Pruefstein 17, Daten)"
set "L=%L3%"
set "C=java %U% -Xmx4g -cp dist\Renderer.jar com.dan.aufbereitung.demo.GehoelzCheck --gebiet %G%"
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
