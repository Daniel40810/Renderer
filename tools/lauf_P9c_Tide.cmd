@echo off
rem P9c, Nachlauf zur Tide (Daniel, 18.11.2026, 07:10: ja): woher kommen die -8,5 cm Tidenhub von M2 gegen das Archiv von P9b (S13)?
rem Zwei Laeufe ueber dieselbe Sturmtide wie M2 (Start 24.10. 00 UTC, Ablage 25.10. 06 UTC bis 26.10. 00 UTC), ohne den Boden des Modelljahrs,
rem Faktor 1, Seegang 20 x 18 auf 1000 m und 200 m, Deckel 0.01:
rem 1 T1 ohne Rauheit aus dem Sediment nach _p9c\archiv_t1; 2 T2 mit Rauheit aus dem Sediment nach _p9c\archiv_t2.
rem T1 gegen P9b: Startzeit und Seegangsgitter; T2 gegen T1: Rauheit; M2 gegen T2: Boden nach dem Modelljahr.
rem Kein Bau: es laeuft das Renderer.jar der Kette ueber Nacht. Atteste: Renderer\docs\status\P9c_T1.md, P9c_T2.md.
rem Ausgaben: HalligHooge\docs\status\Status_Phase9cT*.txt (UTF-8), Uebersicht in Status_Phase9cT.txt.
rem Anhalten zwischen zwei Schritten: eine Datei STOPP.txt neben dieses Skript legen.
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0.."

set "HH=C:\Users\Dan40810\Documents\NetBeansProjects\HalligHooge"
set "ST=%HH%\docs\status"
set "G=D:\Daten\HalligHooge\gebiet"
set "T1=D:\Daten\HalligHooge\_p9c\archiv_t1"
set "T2=D:\Daten\HalligHooge\_p9c\archiv_t2"
set "F3=400,850,700,400"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
set "R=java %U% --enable-native-access=ALL-UNNAMED -Xmx12g -cp dist\Renderer.jar"
set "SEE=--stuetzen 20,18 --seegang-gitter 1000,200"
set "UE=%ST%\Status_Phase9cT.txt"
set "MO=--gebiet %G% --archiv-ruhig - --lauf sturm --nur 1 --fenster3 %F3% --vorlauf 3 --deckel 0.01 %SEE% --start 2026-10-24T00:00:00Z --ab 2026-10-25T06:00:00Z --sediment-ab 2026-10-25T06:00:00Z --ende 2026-10-26T00:00:00Z --faktor 1"
set "L1=%ST%\Status_Phase9cT1.txt"
set "L2=%ST%\Status_Phase9cT2.txt"

>"%UE%" echo Lauf P9c Tide, gestartet %DATE% %TIME%
>"%L1%" echo Status_Phase9cT1: T1, Sturmtide ohne Rauheit aus dem Sediment, nach %T1%
>"%L2%" echo Status_Phase9cT2: T2, Sturmtide mit Rauheit aus dem Sediment, nach %T2%

set "N=1 T1 (Sturmtide, Faktor 1, ohne Rauheit aus dem Sediment)"
set "L=%L1%"
set "C=%R% com.dan.lauf.demo.SedimentLaufCheck %MO% --archiv %T1% --attest P9c_T1"
call :lauf
findstr /c:"FEHLT  Ablauf" "%L1%" >nul && goto :abbruch

set "N=2 T2 (Sturmtide, Faktor 1, mit Rauheit aus dem Sediment)"
set "L=%L2%"
set "C=%R% com.dan.lauf.demo.SedimentLaufCheck %MO% --rauheit --archiv %T2% --attest P9c_T2"
call :lauf
findstr /c:"FEHLT  Ablauf" "%L2%" >nul && goto :abbruch

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
