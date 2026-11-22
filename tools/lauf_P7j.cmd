@echo off
rem P7j (F67 A, Spektrum) auf dem Ryzen: bauen, WaterCheck, ReferenceCheck, ReferenzCheck, SaeuleCheck.
rem Ausgaben: HalligHooge\docs\status\Status_Phase7j*.txt (UTF-8), Uebersicht in Status_Phase7j.txt.
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0.."

set "ST=C:\Users\Dan40810\Documents\NetBeansProjects\HalligHooge\docs\status"
set "G=D:\Daten\HalligHooge\gebiet"
set "A6=D:\Daten\HalligHooge\_p6\archiv"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
set "R=java %U% --enable-native-access=ALL-UNNAMED -Xmx12g -cp dist\Renderer.jar"
set "V=-Dcore.gpu.validierung=true"
set "UE=%ST%\Status_Phase7j.txt"
set "L0=%ST%\Status_Phase7j1.txt"
set "L1=%ST%\Status_Phase7j2.txt"
set "L2=%ST%\Status_Phase7j3.txt"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"

>"%UE%" echo Lauf P7j (wirksame Absorption je Kanal), gestartet %DATE% %TIME%
>"%L0%" echo Status_Phase7j1: Bauen (ant clean jar)
>"%L1%" echo Status_Phase7j2: WaterCheck, ReferenceCheck, SaeuleCheck
>"%L2%" echo Status_Phase7j3: ReferenzCheck (64 Pfade, ohne Attest)

set "N=1 Bauen (clean jar)"
set "L=%L0%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" goto :abbruch

set "N=2 WaterCheck (Validierung)"
set "L=%L1%"
set "C=%R% %V% com.dan.render.water.demo.WaterCheck --gebiet %G%"
call :lauf

set "N=3 ReferenceCheck"
set "L=%L1%"
set "C=java %U% -Xmx8g -cp dist\Renderer.jar com.dan.render.reference.demo.ReferenceCheck --ohne-attest"
call :lauf

set "N=4 ReferenzCheck (64 Pfade)"
set "L=%L2%"
set "C=java %U% --enable-native-access=ALL-UNNAMED -Xmx8g -cp dist\Renderer.jar com.dan.lauf.demo.ReferenzCheck --gebiet %G% --pfade 64 --ohne-attest"
call :lauf

set "N=5 SaeuleCheck"
set "L=%L1%"
set "C=java %U% -cp dist\Renderer.jar com.dan.render.reference.demo.SaeuleCheck"
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
