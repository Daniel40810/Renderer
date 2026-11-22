@echo off
rem P12c, Lauf C1 (Werkbuch HalligHooge Teil A, 12.13, Datei 76): bDOM (20 cm) fuer Hooge laden (F136 A).
rem 1 bDOM laden (Kartenwerkzeug, 22 Kacheln zu 1 km, Befliegung Mai 2024); 2 Pruefung der Kacheln (Kartenwerkzeug plan).
rem Ausgaben: HalligHooge\docs\status\Status_Phase12cC1_*.txt; Daten D:\Daten\HalligHooge\hoehe\bdom\.
rem Erwartete Dauer: rund 10 bis 20 Minuten (rund 1 bis 2 GB, je nach Leitung).
rem Anhalten zwischen zwei Schritten: eine Datei STOPP.txt neben dieses Skript legen.
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0.."

set "HH=C:\Users\Dan40810\Documents\NetBeansProjects\HalligHooge"
set "ST=%HH%\docs\status"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
set "PY=python"
where python >nul 2>nul || set "PY=py"

set "UE=%ST%\Status_Phase12cC1_.txt"
set "L1=%ST%\Status_Phase12cC1_1.txt"
set "L2=%ST%\Status_Phase12cC1_2.txt"
set "E=0"
set "EF=0"

>"%UE%" echo Lauf P12c C1, gestartet %DATE% %TIME%
>"%L1%" echo Status_Phase12cC1_1: bDOM laden (Kartenwerkzeug)
>"%L2%" echo Status_Phase12cC1_2: Pruefung der Kacheln

set "N=1 bDOM laden (Kartenwerkzeug)"
set "L=%L1%"
pushd "%HH%"
set "C=%PY% -u tools\kartenmaterial\kartenmaterial.py laden --quellen bdom --gebiet kern --ziel D:\Daten\HalligHooge"
call :lauf
popd
if not "%E%"=="0" goto :abbruch

set "N=2 Pruefung der Kacheln"
set "L=%L2%"
pushd "%HH%"
set "C=%PY% -u tools\kartenmaterial\kartenmaterial.py plan --quellen bdom --gebiet kern --ziel D:\Daten\HalligHooge"
call :lauf
popd
dir /s D:\Daten\HalligHooge\hoehe\bdom >>"%L2%" 2>&1
if not "%E%"=="0" set "EF=1"

>>"%UE%" echo.
if not "%EF%"=="0" >>"%UE%" echo Mindestens ein Schritt mit Exit ungleich 0 - siehe die Statusdateien
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
exit /b 1

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
