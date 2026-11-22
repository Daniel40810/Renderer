@echo off
rem P12b, Lauf B4 (Werkbuch HalligHooge Teil A, 12.13, Datei 73): Pfaehle der Lahnungen als Gruppen am Ende der Koerper,
rem nur bis 400 m gezeichnet, Schatten nur in der naechsten Kaskade.
rem 1 Renderer bauen; 2 Aufbereitung, Schritt gestalt; 3 GestaltCheck; 4 BauCheck; 5 GestaltLaufCheck (Pruefsteine 15, 10).
rem Ausgaben: HalligHooge\docs\status\Status_Phase12bB4_*.txt; Atteste Renderer\docs\status\P12b_Gestalt.md, P12a_Bau.md,
rem P12b_GestaltLauf.md; Bilder Renderer\docs\status\P12b_Gestalt*.png.
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

set "UE=%ST%\Status_Phase12bB4_.txt"
set "L1=%ST%\Status_Phase12bB4_1.txt"
set "L2=%ST%\Status_Phase12bB4_2.txt"
set "L3=%ST%\Status_Phase12bB4_3.txt"
set "L4=%ST%\Status_Phase12bB4_4.txt"
set "L5=%ST%\Status_Phase12bB4_5.txt"
set "E=0"
set "EF=0"

for /f %%t in ('powershell -NoProfile -Command "[DateTime]::Now.Ticks"') do set "T0=%%t"
>"%UE%" echo Lauf P12b B4, gestartet %DATE% %TIME%
>"%L1%" echo Status_Phase12bB4_1: Renderer bauen (clean jar, check-packages)
>"%L2%" echo Status_Phase12bB4_2: Aufbereitung, Schritt gestalt
>"%L3%" echo Status_Phase12bB4_3: GestaltCheck (Pruefsteine 7, 11, 14)
>"%L4%" echo Status_Phase12bB4_4: BauCheck
>"%L5%" echo Status_Phase12bB4_5: GestaltLaufCheck (Pruefsteine 15, 10)

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

set "N=2 Aufbereitung, Schritt gestalt"
set "L=%L2%"
set "C=java %U% -Xmx12g -cp dist\Renderer.jar com.dan.aufbereitung.Aufbereitung ..\HalligHooge\gebiet\Hooge.rezept --schritte gestalt"
call :lauf
if not "%E%"=="0" goto :abbruch

set "N=3 GestaltCheck (Pruefsteine 7, 11, 14)"
set "L=%L3%"
set "C=java %U% -Xmx4g -cp dist\Renderer.jar com.dan.aufbereitung.demo.GestaltCheck --gebiet %G%"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=4 BauCheck"
set "L=%L4%"
set "C=%R% com.dan.render.bau.demo.BauCheck"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=5 GestaltLaufCheck (Pruefsteine 15, 10)"
set "L=%L5%"
set "C=%R% com.dan.lauf.demo.GestaltLaufCheck --szenen %SZ%"
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
