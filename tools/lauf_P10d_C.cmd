@echo off
rem P10d, Lauf C (Werkbuch HalligHooge Teil A, 12.11, Datei 45): ReferenzCheck mit 256 Pfaden je Bildpunkt (F101 A: Ansichten 1, 3, 4, 5)
rem und die Ansicht mit Sediment (F102 A, Pruefstein 17: zweimal, mit Truebung am Ort und mit ueberall gleicher, ein Viertel der Pfade).
rem 1 ReferenzCheck, Archive _p8c (wie P8c), Ansichten 1345; 2 ReferenzCheck --sediment _p9b\archiv (Prielmuendung, 25.10., 15:00 UTC).
rem Ausgaben: HalligHooge\docs\status\Status_Phase10dC_*.txt (UTF-8), Uebersicht in Status_Phase10dC_.txt.
rem Atteste: Renderer\docs\status\P10d_Referenz.md, P10d_ReferenzSediment.md (je mit .png).
rem Erwartete Dauer: rund 80 Minuten (65 + 15 [S]). Anhalten zwischen zwei Schritten: eine Datei STOPP.txt neben dieses Skript legen.
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0.."

set "HH=C:\Users\Dan40810\Documents\NetBeansProjects\HalligHooge"
set "ST=%HH%\docs\status"
set "G=D:\Daten\HalligHooge\gebiet"
set "A8=D:\Daten\HalligHooge\_p8c\archiv"
set "A8R=D:\Daten\HalligHooge\_p8c\archiv_ruhig"
set "A9=D:\Daten\HalligHooge\_p9b\archiv"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
set "R=java %U% --enable-native-access=ALL-UNNAMED -Xmx12g -cp dist\Renderer.jar"

set "UE=%ST%\Status_Phase10dC_.txt"
set "L1=%ST%\Status_Phase10dC_1.txt"
set "L2=%ST%\Status_Phase10dC_2.txt"

>"%UE%" echo Lauf P10d C, gestartet %DATE% %TIME%
>"%L1%" echo Status_Phase10dC_1: ReferenzCheck, Ansichten 1, 3, 4, 5 (256 Pfade)
>"%L2%" echo Status_Phase10dC_2: ReferenzCheck, Prielmuendung mit Sediment

set "N=1 ReferenzCheck, Ansichten 1, 3, 4, 5"
set "L=%L1%"
set "C=%R% com.dan.lauf.demo.ReferenzCheck --gebiet %G% --archiv %A8% --archiv-ruhig %A8R% --pfade 256 --nur 1345 --name P10d_Referenz"
call :lauf

set "N=2 ReferenzCheck, Prielmuendung mit Sediment"
set "L=%L2%"
set "C=%R% com.dan.lauf.demo.ReferenzCheck --gebiet %G% --sediment %A9% --pfade 256 --name P10d_ReferenzSediment"
call :lauf

>>"%UE%" echo.
>>"%UE%" echo FERTIG %DATE% %TIME%
echo.
echo Fertig. Uebersicht: %UE%
timeout /t 20 >nul
goto :eof

:abbruch

set "N=2 SonnenfarbeCheck"
set "L=%L2%"
set "C=%R% com.dan.render.air.demo.SonnenfarbeCheck"
call :lauf

set "N=3 AirCheck (mit Teil 5, tiefe Sonne)"
set "L=%L3%"
set "C=%R% com.dan.render.air.demo.AirCheck"
call :lauf

set "N=4 TerrainCheck"
set "L=%L4%"
set "C=%R% com.dan.render.terrain.demo.TerrainCheck --gebiet %G%"
call :lauf

set "N=5 WaterCheck"
set "L=%L5%"
set "C=%R% com.dan.render.water.demo.WaterCheck --gebiet %G%"
call :lauf

set "N=6 BildCheck"
set "L=%L6%"
set "C=%R% com.dan.lauf.demo.BildCheck --gebiet %G% --archiv %A6% --nur 2,3"
call :lauf

set "N=7 KimmungKarteCheck"
set "L=%L7%"
set "C=%R% com.dan.lauf.demo.KimmungKarteCheck --gebiet %G% --archiv %AK% --zeit %ZEIT% --blick %BLICK% --name P10d_KimmungKarte"
call :lauf

>>"%UE%" echo.
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
goto :eof

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
