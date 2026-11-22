@echo off
rem P9b, Lauf B2 (Werkbuch HalligHooge Teil A, 12.10): ruhiger Tag 28. bis 31.10.2026, abgelegt der 30.10., nach _p9b\archiv_ruhig.
rem Lauf mit Seegang und Sediment auf der Karte, G3 als Fenster 400 850 700 400 (F82 A); baut nicht, nimmt dist\Renderer.jar, wie es liegt.
rem Solange der Lauf rechnet, den Renderer nicht neu bauen.
rem Ausgaben: HalligHooge\docs\status\Status_Phase9bB2.txt (Uebersicht), Status_Phase9bB21.txt (Lauf); Attest Renderer\docs\status\P9b_Sediment_Lauf_ruhig.md.
rem Anhalten zwischen zwei Schritten: eine Datei STOPP.txt neben dieses Skript legen.
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0.."

set "HH=C:\Users\Dan40810\Documents\NetBeansProjects\HalligHooge"
set "ST=%HH%\docs\status"
set "G=D:\Daten\HalligHooge\gebiet"
set "A9=D:\Daten\HalligHooge\_p9b\archiv"
set "A9R=D:\Daten\HalligHooge\_p9b\archiv_ruhig"
set "F3=400,850,700,400"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
set "R=java %U% --enable-native-access=ALL-UNNAMED -Xmx12g -cp dist\Renderer.jar"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "UE=%ST%\Status_Phase9bB2.txt"
set "L1=%ST%\Status_Phase9bB21.txt"

>"%UE%" echo Lauf P9b B2, gestartet %DATE% %TIME%
>"%L1%" echo Status_Phase9bB21: SedimentLaufCheck --nur 1 --lauf ruhig

set "N=1 SedimentLaufCheck, Lauf ruhig (Pruefsteine 1, 2, 3, 9)"
set "L=%L1%"
set "C=%R% com.dan.lauf.demo.SedimentLaufCheck --gebiet %G% --archiv %A9% --archiv-ruhig %A9R% --fenster3 %F3% --nur 1 --lauf ruhig"
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
