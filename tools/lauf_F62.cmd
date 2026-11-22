@echo off
rem F62 A auf dem Ryzen: bauen, AirCheck, BildCheck (Himmelsansicht ueber fuenf Bilder verteilt).
rem Ausgaben: HalligHooge\docs\status\Status_Phase7f.3*.txt (UTF-8), Uebersicht in Status_Phase7f.3.txt.
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0.."

set "ST=C:\Users\Dan40810\Documents\NetBeansProjects\HalligHooge\docs\status"
set "G=D:\Daten\HalligHooge\gebiet"
set "A6=D:\Daten\HalligHooge\_p6\archiv"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
set "R=java %U% --enable-native-access=ALL-UNNAMED -Xmx12g -cp dist\Renderer.jar"
set "V=-Dcore.gpu.validierung=true"
set "UE=%ST%\Status_Phase7f.3.txt"
set "L0=%ST%\Status_Phase7f.3a.txt"
set "L1=%ST%\Status_Phase7f.3b.txt"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"

>"%UE%" echo Lauf F62 A, gestartet %DATE% %TIME%
>"%L0%" echo Status_Phase7f.3a: Bauen (ant clean jar)
>"%L1%" echo Status_Phase7f.3b: AirCheck, BildCheck ohne und mit Validierung

set "N=1 Bauen (clean jar)"
set "L=%L0%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" goto :abbruch

set "N=2 AirCheck (Validierung)"
set "L=%L1%"
set "C=%R% %V% com.dan.render.air.demo.AirCheck"
call :lauf

set "N=3 BildCheck --nur 2,3"
set "L=%L1%"
set "C=%R% com.dan.lauf.demo.BildCheck --gebiet %G% --archiv %A6% --nur 2,3"
call :lauf

set "N=4 BildCheck --nur 2,3 --bilder 60 (Validierung)"
set "L=%L1%"
set "C=%R% %V% com.dan.lauf.demo.BildCheck --gebiet %G% --archiv %A6% --nur 2,3 --bilder 60"
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
