@echo off
rem Teil B, erste Fassung (B1 und B2): der eine gebuendelte Lauf zur Abnahme auf dem Ryzen (Werkbuch HalligHooge Teil B, B.6).
rem Baut den Renderer, prueft die Buehne, baut das Projekt HalligHooge und startet die Anwendung dreimal kurz (je Szenario).
rem Waehrend der Schritte 6 bis 8 oeffnet sich das Fenster der Anwendung und schliesst sich von selbst - bitte nicht schliessen.
rem Ausgaben: HalligHooge\docs\status\Status_TeilB*.txt (UTF-8), Uebersicht in Status_TeilB.txt.
rem Anhalten zwischen zwei Schritten: eine Datei STOPP.txt neben dieses Skript legen.
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0.."

set "HH=C:\Users\Dan40810\Documents\NetBeansProjects\HalligHooge"
set "ST=%HH%\docs\status"
set "G=D:\Daten\HalligHooge\gebiet"
set "A6=D:\Daten\HalligHooge\_p6\archiv"
set "SZ=%HH%\szenen\Hooge.szenen"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
set "R=java %U% --enable-native-access=ALL-UNNAMED -Xmx12g -cp dist\Renderer.jar"
set "APP=java %U% --enable-native-access=ALL-UNNAMED -Xmx12g -jar dist\HalligHooge.jar --szenen szenen\Hooge.szenen"
set "UE=%ST%\Status_TeilB.txt"
set "L1=%ST%\Status_TeilB1.txt"
set "L2=%ST%\Status_TeilB2.txt"
set "L3=%ST%\Status_TeilB3.txt"
set "L4=%ST%\Status_TeilB4.txt"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"

rem Die Abnahme P7 muss fertig sein: sie liest dist\Renderer.jar, das der Bau ersetzt.
:warte
findstr /C:"FERTIG" "%ST%\Status_Phase7l.txt" >nul 2>nul && goto :los
echo [%TIME%] Warte auf das Ende der Abnahme P7 (lauf_P7l_Abnahme.cmd) ...
timeout /t 30 /nobreak >nul
goto :warte

:los
>"%UE%" echo Lauf Teil B (B1 und B2), gestartet %DATE% %TIME%
>"%L1%" echo Status_TeilB1: Renderer bauen (ant clean jar, mit check-packages)
>"%L2%" echo Status_TeilB2: BuehneCheck, BildCheck --nur 2,3
>"%L3%" echo Status_TeilB3: HalligHooge bauen (ant clean jar), ImportPruefung
>"%L4%" echo Status_TeilB4: Anwendung HalligHooge je Szenario (Pruefsteine 1 und 2)

set "N=1 Renderer bauen (clean jar)"
set "L=%L1%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" goto :abbruch

set "N=2 BuehneCheck (Pruefsteine 3 und 6)"
set "L=%L2%"
set "C=%R% com.dan.lauf.demo.BuehneCheck --szenen %SZ%"
call :lauf

set "N=3 BildCheck --nur 2,3 (Pruefstein 7)"
set "L=%L2%"
set "C=%R% com.dan.lauf.demo.BildCheck --gebiet %G% --archiv %A6% --nur 2,3"
call :lauf

set "N=4 Renderer.jar und FStyle.jar ins Projekt, HalligHooge bauen (clean jar)"
set "L=%L3%"
copy /y dist\Renderer.jar "%HH%\lib\Renderer.jar" >>"%L3%" 2>&1
copy /y dist\lib\FStyle.jar "%HH%\lib\FStyle.jar" >>"%L3%" 2>&1
cd /d "%HH%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" goto :abbruch

set "N=5 ImportPruefung (Pruefsteine 4 und 5)"
set "L=%L3%"
set "C=java %U% tools\ImportPruefung.java src lib\FStyle.jar"
call :lauf

set "N=6 Anwendung: ruhiger Tag, 40 s"
set "L=%L4%"
set "C=%APP% --szenario ruhig --sekunden 40"
call :lauf

set "N=7 Anwendung: Sturmflut, 40 s"
set "L=%L4%"
set "C=%APP% --szenario sturm --sekunden 40"
call :lauf

set "N=8 Anwendung: live, 120 s"
set "L=%L4%"
set "C=%APP% --szenario live --sekunden 120"
call :lauf

>>"%UE%" echo.
>>"%UE%" echo FERTIG %DATE% %TIME%
echo.
echo Fertig. Uebersicht: %UE%
echo Die Anwendung startet jetzt mit: %HH%\HalligHooge.cmd
pause
goto :eof

:abbruch
>>"%UE%" echo.
>>"%UE%" echo ABBRUCH %DATE% %TIME% nach: %N%
echo.
echo Abbruch nach: %N%  - siehe %L%
pause
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
