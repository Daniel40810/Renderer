@echo off
rem P10c, Lauf A2 (Werkbuch HalligHooge Teil A, 12.11): Nachlauf zu Lauf A. Archivquelle hielt den neuen Ordner aeolian_G3 im
rem Archiv fuer ein Wassergitter; damit oeffnete kein Szenario auf archiv_m1b mehr. Behoben: Wassergitter ist, was water.eta haelt.
rem 1 Renderer bauen (ant clean jar, mit check-packages); 2 KimmungKarteCheck (Kimmung mit der neuen Luft); 3 BuehneCheck;
rem 4 Renderer.jar ins Projekt, HalligHooge bauen, Anwendung 40 s mit --szenario kimmung.
rem Ausgaben: HalligHooge\docs\status\Status_Phase10cA2_*.txt (UTF-8), Uebersicht in Status_Phase10cA2_.txt.
rem Attest: Renderer\docs\status\P10c_KimmungKarte.md (mit .png).
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
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "SZ=%HH%\szenen\Hooge.szenen"
set "APP=java %U% --enable-native-access=ALL-UNNAMED -Xmx12g -jar dist\HalligHooge.jar --szenen szenen\Hooge.szenen"

set "UE=%ST%\Status_Phase10cA2_.txt"
set "L1=%ST%\Status_Phase10cA2_1.txt"
set "L2=%ST%\Status_Phase10cA2_2.txt"
set "L3=%ST%\Status_Phase10cA2_3.txt"
set "L4=%ST%\Status_Phase10cA2_4.txt"

>"%UE%" echo Lauf P10c A2, gestartet %DATE% %TIME%
>"%L1%" echo Status_Phase10cA2_1: Renderer bauen (ant clean jar, mit check-packages)
>"%L2%" echo Status_Phase10cA2_2: KimmungKarteCheck
>"%L3%" echo Status_Phase10cA2_3: BuehneCheck
>"%L4%" echo Status_Phase10cA2_4: HalligHooge bauen, Anwendung

set "N=1 Renderer bauen (clean jar, check-packages)"
set "L=%L1%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" goto :abbruch

set "N=2 KimmungKarteCheck (Karte, Gebiet)"
set "L=%L2%"
set "C=%R% com.dan.lauf.demo.KimmungKarteCheck --gebiet %G% --archiv %AK% --zeit %ZEIT% --blick %BLICK% --name P10c_KimmungKarte"
call :lauf

set "N=3 BuehneCheck"
set "L=%L3%"
set "C=%R% com.dan.lauf.demo.BuehneCheck --szenen %SZ% --ohne-attest"
call :lauf

set "N=4 Renderer.jar ins Projekt, HalligHooge bauen, Anwendung 40 s"
set "L=%L4%"
copy /y dist\Renderer.jar "%HH%\lib\Renderer.jar" >>"%L4%" 2>&1
cd /d "%HH%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" goto :abbruch
set "C=%APP% --szenario kimmung --sekunden 40"
call :lauf
cd /d "%~dp0.."

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
