@echo off
rem P10d, Lauf D3 (Werkbuch HalligHooge Teil A, 12.11, Datei 48): BuehneCheck noch einmal; in Lauf D2 zeigte der Pfad
rem der Szenen ins Leere (szenen statt src\szenen). F106 B - der Himmel wieder mit Albedo 0,23 fest (Mischung
rem nach dem Anteil des Wassers aus); die Mischung je Kanal (F105 A) bleibt im Code und wird in AirCheck geprueft.
rem 1 BuehneCheck (mit dem jar aus Lauf D2).
rem Ausgaben: HalligHooge\docs\status\Status_Phase10dD3_*.txt (UTF-8), Uebersicht in Status_Phase10dD3_.txt.

rem Erwartete Dauer: unter einer Minute.
rem Anhalten zwischen zwei Schritten: eine Datei STOPP.txt neben dieses Skript legen.
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0.."

set "HH=C:\Users\Dan40810\Documents\NetBeansProjects\HalligHooge"
set "ST=%HH%\docs\status"
set "G=D:\Daten\HalligHooge\gebiet"
set "A6=D:\Daten\HalligHooge\_p6\archiv"
set "A8=D:\Daten\HalligHooge\_p8c\archiv"
set "A8R=D:\Daten\HalligHooge\_p8c\archiv_ruhig"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
set "R=java %U% --enable-native-access=ALL-UNNAMED -Xmx12g -cp dist\Renderer.jar"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "SZ=%HH%\src\szenen\Hooge.szenen"

set "UE=%ST%\Status_Phase10dD3_.txt"
set "L1=%ST%\Status_Phase10dD3_1.txt"

>"%UE%" echo Lauf P10d D3, gestartet %DATE% %TIME%
>"%L1%" echo Status_Phase10dD3_1: BuehneCheck

set "N=1 BuehneCheck"
set "L=%L1%"
set "C=%R% com.dan.lauf.demo.BuehneCheck --szenen %SZ%"
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
