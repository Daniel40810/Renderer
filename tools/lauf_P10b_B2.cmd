@echo off
rem P10b, Lauf B2 (Werkbuch HalligHooge Teil A, 12.11): Nachlauf zu Lauf B mit dem Blick nach Amrum.
rem 1 Renderer bauen (ant clean jar, mit check-packages); 2 KimmungKarteCheck (Pruefsteine 9, 11, 12, 14; Status-Bild).
rem Ausgaben: HalligHooge\docs\status\Status_Phase10bB2_*.txt (UTF-8), Uebersicht in Status_Phase10bB2_.txt.
rem Attest: Renderer\docs\status\P10b_KimmungKarte.md (mit .png).
rem Anhalten zwischen zwei Schritten: eine Datei STOPP.txt neben dieses Skript legen.
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0.."

set "HH=C:\Users\Dan40810\Documents\NetBeansProjects\HalligHooge"
set "ST=%HH%\docs\status"
set "G=D:\Daten\HalligHooge\gebiet"
set "AK=D:\Daten\HalligHooge\_p9c\archiv_m1b"
set "ZEIT=2026-10-18T08:00:00Z"
set "BLICK=462600,6046900,4,325,0,5.6"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
set "R=java %U% --enable-native-access=ALL-UNNAMED -Xmx12g -cp dist\Renderer.jar"
set "RV=java %U% -Dcore.gpu.validierung=true --enable-native-access=ALL-UNNAMED -Xmx12g -cp dist\Renderer.jar"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "SZ=%HH%\szenen\Hooge.szenen"
set "APP=java %U% --enable-native-access=ALL-UNNAMED -Xmx12g -jar dist\HalligHooge.jar --szenen szenen\Hooge.szenen"

set "UE=%ST%\Status_Phase10bB2_.txt"
set "L1=%ST%\Status_Phase10bB2_1.txt"
set "L2=%ST%\Status_Phase10bB2_2.txt"
set "L3=%ST%\Status_Phase10bB2_3.txt"
set "L4=%ST%\Status_Phase10bB2_4.txt"

>"%UE%" echo Lauf P10b B2, gestartet %DATE% %TIME%
>"%L1%" echo Status_Phase10bB2_1: Renderer bauen (ant clean jar, mit check-packages)
>"%L2%" echo Status_Phase10bB2_2: KimmungKarteCheck

set "N=1 Renderer bauen (clean jar, check-packages)"
set "L=%L1%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" goto :abbruch

set "N=3 KimmungKarteCheck (Karte, Gebiet, Kosten)"
set "L=%L2%"
set "C=%R% com.dan.lauf.demo.KimmungKarteCheck --gebiet %G% --archiv %AK% --zeit %ZEIT% --blick %BLICK%"
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
