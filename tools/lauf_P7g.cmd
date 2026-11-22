@echo off
rem P7g (F63 A) und F64 A auf dem Ryzen: bauen, FlaecheCheck, WaterCheck, BildCheck, ReferenzCheck, SaeuleCheck.
rem Ausgaben: HalligHooge\docs\status\Status_Phase7g*.txt (UTF-8), Uebersicht in Status_Phase7g.txt.
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0.."

set "ST=C:\Users\Dan40810\Documents\NetBeansProjects\HalligHooge\docs\status"
set "G=D:\Daten\HalligHooge\gebiet"
set "A6=D:\Daten\HalligHooge\_p6\archiv"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
set "R=java %U% --enable-native-access=ALL-UNNAMED -Xmx12g -cp dist\Renderer.jar"
set "V=-Dcore.gpu.validierung=true"
set "UE=%ST%\Status_Phase7g.txt"
set "L0=%ST%\Status_Phase7g1.txt"
set "L1=%ST%\Status_Phase7g2.txt"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"

>"%UE%" echo Lauf P7g und F64 A, gestartet %DATE% %TIME%
>"%L0%" echo Status_Phase7g1: Bauen (ant clean jar)
>"%L1%" echo Status_Phase7g2: FlaecheCheck, WaterCheck, BildCheck, ReferenzCheck (64 Pfade, ohne Attest), SaeuleCheck

set "N=1 Bauen (clean jar)"
set "L=%L0%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" goto :abbruch

set "N=2 FlaecheCheck"
set "L=%L1%"
set "C=java %U% -cp dist\Renderer.jar com.dan.render.reference.demo.FlaecheCheck"
call :lauf

set "N=3 WaterCheck (Validierung)"
set "L=%L1%"
set "C=%R% %V% com.dan.render.water.demo.WaterCheck --gebiet %G%"
call :lauf

set "N=4 BildCheck --nur 2,3"
set "L=%L1%"
set "C=%R% com.dan.lauf.demo.BildCheck --gebiet %G% --archiv %A6% --nur 2,3"
call :lauf

set "N=5 ReferenzCheck (64 Pfade, Teile des Wassers)"
set "L=%L1%"
set "C=java %U% --enable-native-access=ALL-UNNAMED -Xmx8g -cp dist\Renderer.jar com.dan.lauf.demo.ReferenzCheck --gebiet %G% --pfade 64 --ohne-attest"
call :lauf

set "N=6 SaeuleCheck"
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
