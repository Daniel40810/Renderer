@echo off
rem Zweiter Lauf P7f (F60 A) und F61 A auf dem Ryzen: bauen, Saeule backen (32 Tiefenstufen), bauen, messen.
rem Ausgaben: HalligHooge\docs\status\Status_Phase7f.2*.txt (UTF-8), Uebersicht in Status_Phase7f.2.txt.
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0.."

set "ST=C:\Users\Dan40810\Documents\NetBeansProjects\HalligHooge\docs\status"
set "G=D:\Daten\HalligHooge\gebiet"
set "A6=D:\Daten\HalligHooge\_p6\archiv"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
set "J=java %U% -cp dist\Renderer.jar"
set "R=java %U% --enable-native-access=ALL-UNNAMED -Xmx12g -cp dist\Renderer.jar"
set "V=-Dcore.gpu.validierung=true"
set "UE=%ST%\Status_Phase7f.2.txt"
set "L0=%ST%\Status_Phase7f.2a.txt"
set "L1=%ST%\Status_Phase7f.2b.txt"
set "L2=%ST%\Status_Phase7f.2c.txt"
set "L3=%ST%\Status_Phase7f.2d.txt"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"

>"%UE%" echo Zweiter Lauf P7f und F61 A (Tabelle mit 1 200 000 und 200 000 Photonen), gestartet %DATE% %TIME%
>>"%UE%" echo Ant: %ANT%
>"%L0%" echo Status_Phase7f.2a: Bauen (ant clean jar, ant jar)
>"%L1%" echo Status_Phase7f.2b: Saeulentabelle (Backen), SaeuleCheck
>"%L2%" echo Status_Phase7f.2c: ReferenzCheck mit den Teilen des Wassers (64 Pfade, ohne Attest)
>"%L3%" echo Status_Phase7f.2d: WaterCheck, BildCheck

set "N=1 Bauen (clean jar)"
set "L=%L0%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" goto :abbruch

set "N=2 Saeule backen (32 Tiefenstufen)"
set "L=%L1%"
set "C=%J% com.dan.render.reference.Saeulentabelle src\com\dan\core\szene\saeule.bin 1200000 200000"
call :lauf
if not "%E%"=="0" goto :abbruch

set "N=3 Bauen (jar mit der neuen Tabelle)"
set "L=%L0%"
set "C=call "%ANT%" -f build.xml jar"
call :lauf
if not "%E%"=="0" goto :abbruch

set "N=4 ReferenzCheck (64 Pfade, Teile des Wassers)"
set "L=%L2%"
set "C=java %U% --enable-native-access=ALL-UNNAMED -Xmx8g -cp dist\Renderer.jar com.dan.lauf.demo.ReferenzCheck --gebiet %G% --pfade 64 --ohne-attest"
call :lauf

set "N=5 SaeuleCheck"
set "L=%L1%"
set "C=%J% com.dan.render.reference.demo.SaeuleCheck"
call :lauf

set "N=6 WaterCheck (Validierung)"
set "L=%L3%"
set "C=%R% %V% com.dan.render.water.demo.WaterCheck --gebiet %G%"
call :lauf

>>"%UE%" echo.
>>"%UE%" echo FERTIG %DATE% %TIME%
echo.
echo Fertig. Uebersicht: %UE%
pause
goto :eof

:abbruch
>>"%UE%" echo.
>>"%UE%" echo ABBRUCH %DATE% %TIME% nach: %N%
echo.
echo Abbruch nach: %N%  - siehe %L%
pause
goto :eof

:lauf
echo [%DATE% %TIME%] Start  %N%
>>"%UE%" echo [%DATE% %TIME%] Start  %N%
>>"%L%" echo.
>>"%L%" echo ^>%C%
%C% >>"%L%" 2>&1
set "E=%ERRORLEVEL%"
echo [%DATE% %TIME%] Ende   %N%  - Exit %E%
>>"%UE%" echo [%DATE% %TIME%] Ende   %N%  - Exit %E%
goto :eof
