@echo off
rem P10c, Lauf B3 (Werkbuch HalligHooge Teil A, 12.11): Nachmessung zu Lauf B2. Die Kosten des Sandfegens (Pruefstein 17) schwanken
rem von Messung zu Messung (B: +0,40 ms, B2: +0,82 ms). Neu: einmal warm, sieben Runden im Wechsel ohne/mit, der Median zaehlt.
rem 1 Renderer bauen (ant clean jar, mit check-packages); 2 SandfegenKarteCheck;
rem 3 Renderer.jar ins Projekt, HalligHooge bauen (damit Projekt und Renderer denselben Stand haben).
rem Ausgaben: HalligHooge\docs\status\Status_Phase10cB3_*.txt (UTF-8), Uebersicht in Status_Phase10cB3_.txt.
rem Attest: Renderer\docs\status\P10c_Sandfegen.md (mit .png).
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0.."

set "HH=C:\Users\Dan40810\Documents\NetBeansProjects\HalligHooge"
set "ST=%HH%\docs\status"
set "G=D:\Daten\HalligHooge\gebiet"
set "AK=D:\Daten\HalligHooge\_p9c\archiv_m1b"
set "ZEIT=2026-10-17T16:00:00Z"
set "ORT=465850,6042890"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
set "R=java %U% --enable-native-access=ALL-UNNAMED -Xmx12g -cp dist\Renderer.jar"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "SZ=%HH%\szenen\Hooge.szenen"
set "APP=java %U% --enable-native-access=ALL-UNNAMED -Xmx12g -jar dist\HalligHooge.jar --szenen szenen\Hooge.szenen"

set "UE=%ST%\Status_Phase10cB3_.txt"
set "L1=%ST%\Status_Phase10cB3_1.txt"
set "L2=%ST%\Status_Phase10cB3_2.txt"
set "L3=%ST%\Status_Phase10cB3_3.txt"

>"%UE%" echo Lauf P10c B3, gestartet %DATE% %TIME%
>"%L1%" echo Status_Phase10cB3_1: Renderer bauen (ant clean jar, mit check-packages)
>"%L2%" echo Status_Phase10cB3_2: SandfegenKarteCheck
>"%L3%" echo Status_Phase10cB3_3: HalligHooge bauen

set "N=1 Renderer bauen (clean jar, check-packages)"
set "L=%L1%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" goto :abbruch

set "N=2 SandfegenKarteCheck (Karte, Gebiet, Kosten)"
set "L=%L2%"
set "C=%R% com.dan.lauf.demo.SandfegenKarteCheck --gebiet %G% --archiv %AK% --zeit %ZEIT% --ort %ORT% --richtung 20"
call :lauf

set "N=3 Renderer.jar ins Projekt, HalligHooge bauen"
set "L=%L3%"
copy /y dist\Renderer.jar "%HH%\lib\Renderer.jar" >>"%L3%" 2>&1
cd /d "%HH%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" goto :abbruch
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
