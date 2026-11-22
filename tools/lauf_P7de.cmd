@echo off
rem Lauf auf dem Ryzen fuer P7d und P7e (Werkbuch HalligHooge 12.8, Stand P7e/F59: Schritte 2-7).
rem Schritt 1 (Clean and Build mit check-packages) laeuft vorher in NetBeans.
rem Ausgaben: HalligHooge\docs\status\Status_Phase7e*.txt (UTF-8), Uebersicht in Status_Phase7e.txt.
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
set "UE=%ST%\Status_Phase7e.txt"
set "L1=%ST%\Status_Phase7e1.txt"
set "L2=%ST%\Status_Phase7e2.txt"
set "L3=%ST%\Status_Phase7e3.txt"
set "L4=%ST%\Status_Phase7e4.txt"

>"%UE%" echo Lauf auf dem Ryzen fuer P7d und P7e, gestartet %DATE% %TIME%
>>"%UE%" echo Verzeichnis: %CD%
for %%F in (dist\Renderer.jar) do >>"%UE%" echo Renderer.jar: %%~tF, %%~zF Bytes
java -version >>"%UE%" 2>&1
if exist "%USERPROFILE%\.hallighooge\luft" (>>"%UE%" echo J: Ordner %USERPROFILE%\.hallighooge\luft vorhanden) else (>>"%UE%" echo J: wird beim ersten Mal gerechnet)
>>"%UE%" echo.
>"%L1%" echo Status_Phase7e1: SaeuleCheck, LuftCheck
>"%L2%" echo Status_Phase7e2: AirCheck, WaterCheck, TerrainCheck, FrameCheck
>"%L3%" echo Status_Phase7e3: BildCheck ohne und mit Validierung
>"%L4%" echo Status_Phase7e4: ReferenzCheck

rem Attest und Bild des Stands F56 fuer den Vergleich vorher/nachher aufheben
if exist docs\status\P7_Referenz.md if not exist docs\status\P7_Referenz_F56.md copy /y docs\status\P7_Referenz.md docs\status\P7_Referenz_F56.md >nul
if exist docs\status\P7_Referenz.png if not exist docs\status\P7_Referenz_F56.png copy /y docs\status\P7_Referenz.png docs\status\P7_Referenz_F56.png >nul

set "N=2 SaeuleCheck"
set "L=%L1%"
set "C=%J% com.dan.render.reference.demo.SaeuleCheck"
call :lauf

set "N=3 LuftCheck (1 000 000 Pfade)"
set "L=%L1%"
set "C=%J% com.dan.render.reference.demo.LuftCheck --pfade 1000000"
call :lauf

set "N=4 AirCheck (Validierung)"
set "L=%L2%"
set "C=%R% %V% com.dan.render.air.demo.AirCheck"
call :lauf

set "N=5a WaterCheck (Validierung)"
set "L=%L2%"
set "C=%R% %V% com.dan.render.water.demo.WaterCheck --gebiet %G%"
call :lauf

set "N=5b TerrainCheck (Validierung)"
set "L=%L2%"
set "C=%R% %V% com.dan.render.terrain.demo.TerrainCheck --gebiet %G%"
call :lauf

set "N=5c FrameCheck (Validierung)"
set "L=%L2%"
set "C=%R% %V% com.dan.render.frame.demo.FrameCheck"
call :lauf

set "N=6a BildCheck --nur 2,3"
set "L=%L3%"
set "C=%R% com.dan.lauf.demo.BildCheck --gebiet %G% --archiv %A6% --nur 2,3"
call :lauf

set "N=6b BildCheck --nur 2,3 --bilder 60 (Validierung)"
set "L=%L3%"
set "C=%R% %V% com.dan.lauf.demo.BildCheck --gebiet %G% --archiv %A6% --nur 2,3 --bilder 60"
call :lauf

set "N=7 ReferenzCheck (256 Pfade)"
set "L=%L4%"
set "C=java %U% --enable-native-access=ALL-UNNAMED -Xmx8g -cp dist\Renderer.jar com.dan.lauf.demo.ReferenzCheck --gebiet %G%"
call :lauf

>>"%UE%" echo.
>>"%UE%" echo FERTIG %DATE% %TIME%
echo.
echo Fertig. Uebersicht: %UE%
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
