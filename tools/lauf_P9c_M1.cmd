@echo off
rem P9c, Lauf M1 (Feinplan P9c, F85 A): mittleres Wetter 14. bis 16.10.2026 mit Faktor 120 (zaehlt als 360 Tage).
rem Baut nicht. Vorlauf 13.10. (nur Tide und Wind), ab 14.10. 00 UTC Seegang (ab 13.10. 21 UTC), Sediment mit Rueckwirkung und
rem Archiv nach _p9c\archiv_m1. Optionen (etwa --rauheit) als Argumente dieses Skripts.
rem Mit der Rauheit aus dem Sediment (Probelaeufe in Lauf A: Strom im Median +14 %%, unter der Grenze von 15 %%; Pruefstein 15 entscheidet).
rem Attest: Renderer\docs\status\P9c_M1.md.
rem Ausgaben: HalligHooge\docs\status\Status_Phase9cM1*.txt (UTF-8), Uebersicht in Status_Phase9cM1.txt.
rem Anhalten zwischen zwei Schritten: eine Datei STOPP.txt neben dieses Skript legen.
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0.."

set "HH=C:\Users\Dan40810\Documents\NetBeansProjects\HalligHooge"
set "ST=%HH%\docs\status"
set "G=D:\Daten\HalligHooge\gebiet"
set "A9=D:\Daten\HalligHooge\_p9b\archiv"
set "A9R=D:\Daten\HalligHooge\_p9b\archiv_ruhig"
set "M1=D:\Daten\HalligHooge\_p9c\archiv_m1"
set "M2=D:\Daten\HalligHooge\_p9c\archiv_m2"
set "F3=400,850,700,400"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
set "R=java %U% --enable-native-access=ALL-UNNAMED -Xmx12g -cp dist\Renderer.jar"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "SEE=--stuetzen 20,18 --seegang-gitter 1000,200"
set "UE=%ST%\Status_Phase9cM1.txt"
set "L1=%ST%\Status_Phase9cM11.txt"

>"%UE%" echo Lauf P9c M1, gestartet %DATE% %TIME%
>"%L1%" echo Status_Phase9cM11: SedimentLaufCheck, mittleres Wetter, Faktor 120, nach %M1%

set "N=1 SedimentLaufCheck M1 (mittleres Wetter, Faktor 120)"
set "L=%L1%"
set "C=%R% com.dan.lauf.demo.SedimentLaufCheck --gebiet %G% --archiv %M1% --archiv-ruhig - --lauf sturm --nur 1 --fenster3 %F3% --start 2026-10-13T00:00:00Z --ab 2026-10-14T00:00:00Z --sediment-ab 2026-10-14T00:00:00Z --ende 2026-10-17T00:00:00Z --vorlauf 3 --faktor 120 %SEE% --rauheit --attest P9c_M1 %*"
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
