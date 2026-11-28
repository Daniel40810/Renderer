@echo off
rem Seegang S0 und S1, Lauf L1 Teil 1 (Seegang Werkbuch, Datei 2; SF1-SF13 A):
rem 1 Renderer bauen (Waves 0.3 mit Diagnose, WavesModul 0.2, SeegangNachlauf, Katalog mit 53 Feldern);
rem 2 CoreCheck (Katalog) und DiagnoseCheck (Windsee und Duenung, Senken, Anfangsspektrum; ohne Karte);
rem 3 SeegangBilder S0: 13 feste Bilder von heute (Sturmflut 25.10., Xaver, ruhiger Tag) mit den Zeiten der Karte;
rem 4 Probe des Nachlaufs auf der Karte (Sturmflut 25.10., 1 h, ohne Ablage) - bricht der Lauf hier ab, laeuft nichts Langes;
rem 5 Stroemung zu Xaver (hydro_G3, fehlt bisher); 6 Nachlauf des Seegangs zur Sturmflut 25.10. (seegang_G2w, seegang_G3w).
rem Ausgaben: HalligHooge\docs\status\Status_S1_*.txt; Atteste Renderer\docs\status\S1_Diagnose.md, S0_Bilder.md,
rem S1_Probe.md, S1_Stroemung_Xaver.md, S1_Nachlauf_Sturm.md; Bilder Renderer\docs\status\seegang\S0_01 ... 13.png, S0_Uebersicht.png.
rem Neu in den Daten: daten\_p13\archiv_xaver\hydro_G3, daten\_p8c\archiv\seegang_G2w und seegang_G3w (nicht fuer GitHub).
rem Erwartete Dauer: rund 45 min (Bau 3, Pruefungen 1, Bilder 5, Probe 3, Stroemung Xaver 11, Nachlauf 21).
rem Teil 2 (ruhiger Tag und Xaver, rund 75 min, reine Rechnung): lauf_S1b.cmd.
rem Anhalten zwischen zwei Schritten: eine Datei STOPP.txt neben dieses Skript legen.
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0.."

set "HH=C:\Users\Dan40810\Documents\NetBeansProjects\HalligHooge"
set "ST=%HH%\docs\status"
set "SZ=%HH%\src\szenen\Hooge.szenen"
set "D=%HH%\daten"
set "G=%D%\gebiet"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
set "R=java %U% --enable-native-access=ALL-UNNAMED -Xmx16g -cp dist\Renderer.jar"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"

if not exist "%ST%" mkdir "%ST%"
set "UE=%ST%\Status_S1_.txt"
set "L1=%ST%\Status_S1_1.txt"
set "L2=%ST%\Status_S1_2.txt"
set "L3=%ST%\Status_S1_3.txt"
set "L4=%ST%\Status_S1_4.txt"
set "L5=%ST%\Status_S1_5.txt"
set "L6=%ST%\Status_S1_6.txt"
set "E=0"
set "EF=0"

for /f %%t in ('powershell -NoProfile -Command "[DateTime]::Now.Ticks"') do set "T0=%%t"
>"%UE%" echo Lauf S1 Teil 1 (Seegang S0 und S1), gestartet %DATE% %TIME%
>"%L1%" echo Status_S1_1: Renderer bauen (clean jar, check-packages)
>"%L2%" echo Status_S1_2: CoreCheck und DiagnoseCheck
>"%L3%" echo Status_S1_3: SeegangBilder S0
>"%L4%" echo Status_S1_4: Probe des Nachlaufs auf der Karte
>"%L5%" echo Status_S1_5: Stroemung zu Xaver
>"%L6%" echo Status_S1_6: Nachlauf des Seegangs, Sturmflut 25.10.

set "N=1 Renderer bauen (clean jar, check-packages)"
set "L=%L1%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" goto :abbruch
powershell -NoProfile -Command "if ((Get-Item 'dist\Renderer.jar').LastWriteTime.Ticks -gt %T0%) { exit 0 } else { exit 1 }"
if errorlevel 1 (
  >>"%UE%" echo dist\Renderer.jar ist nicht neuer als der Start des Laufs
  set "N=1 Renderer bauen (jar nicht neu)"
  goto :abbruch
)
>>"%UE%" echo dist\Renderer.jar ist neu

set "N=2a CoreCheck (Katalog mit 53 Feldern)"
set "L=%L2%"
set "C=%R% com.dan.core.demo.CoreCheck"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"
set "N=2b DiagnoseCheck (Windsee und Duenung, Senken, Anfangsspektrum)"
set "C=%R% com.dan.physics.waves.demo.DiagnoseCheck"
call :lauf
if not "%E%"=="0" goto :abbruch

set "N=3 SeegangBilder S0 (13 Bilder von heute, Zeiten der Karte)"
set "L=%L3%"
set "C=%R% com.dan.lauf.demo.SeegangBilder --szenen %SZ% --kennung S0"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=4 Probe des Nachlaufs auf der Karte (Sturmflut 25.10., 1 h, ohne Ablage)"
set "L=%L4%"
set "C=%R% com.dan.lauf.demo.SeegangNachlaufLauf --gebiet %G% --archive %D%\_p8c\archiv --hoechstens 1 --ohne-ablage --probe --attest S1_Probe"
call :lauf
if not "%E%"=="0" goto :abbruch

set "N=5 Stroemung zu Xaver (hydro_G3)"
set "L=%L5%"
set "C=%R% com.dan.lauf.demo.StroemungLauf --gebiet %G% --archive %D%\_p13\archiv_xaver --probe --attest S1_Stroemung_Xaver"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=6 Nachlauf des Seegangs, Sturmflut 25.10. (seegang_G2w, seegang_G3w)"
set "L=%L6%"
set "C=%R% com.dan.lauf.demo.SeegangNachlaufLauf --gebiet %G% --archive %D%\_p8c\archiv --attest S1_Nachlauf_Sturm"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

>>"%UE%" echo.
if not "%EF%"=="0" >>"%UE%" echo Mindestens ein Schritt mit Exit ungleich 0 - siehe die Statusdateien und Atteste
>>"%UE%" echo FERTIG %DATE% %TIME%
echo.
echo Fertig. Uebersicht: %UE%
echo Bilder: Renderer\docs\status\seegang\S0_Uebersicht.png
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
