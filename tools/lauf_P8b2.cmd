@echo off
rem P8b, zweiter kurzer Lauf (nach dem ersten Lauf vom 16.11.2026, 17:26-19:38): Renderer bauen, SeegangCheck Teil 2
rem und 3 auf den vorhandenen Archiven (Pruefstein 5 mit der Tiefe, die der Seegang sah; Pruefstein 10 mit begrenzten
rem langen Wellen; Status-Bild bei Tag), WaterCheck, HalligHooge bauen (Auswahllisten als eigenes Fenster), Anwendung.
rem Ausgaben: HalligHooge\docs\status\Status_Phase8b5.txt bis 7.txt (UTF-8), Uebersicht in Status_Phase8b_2.txt.
rem Atteste: Renderer\docs\status\P8b_Seegang.md (Teil 2 und 3), Status-Bilder P8b_Seegang.png und P8b_Bild.png.
rem Die Ausgaben des ersten Laufs liegen als P8b_Seegang_Lauf1.md und P8b_Bild_Lauf1.png daneben.
rem Anhalten zwischen zwei Schritten: eine Datei STOPP.txt neben dieses Skript legen.
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0.."

set "HH=C:\Users\Dan40810\Documents\NetBeansProjects\HalligHooge"
set "ST=%HH%\docs\status"
set "G=D:\Daten\HalligHooge\gebiet"
set "A8=D:\Daten\HalligHooge\_p8\archiv"
set "SZ=%HH%\szenen\Hooge.szenen"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
set "R=java %U% --enable-native-access=ALL-UNNAMED -Xmx12g -cp dist\Renderer.jar"
set "APP=java %U% --enable-native-access=ALL-UNNAMED -Xmx12g -jar dist\HalligHooge.jar --szenen szenen\Hooge.szenen"
set "UE=%ST%\Status_Phase8b_2.txt"
set "L1=%ST%\Status_Phase8b5.txt"
set "L2=%ST%\Status_Phase8b6.txt"
set "L3=%ST%\Status_Phase8b7.txt"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"

>"%UE%" echo Lauf P8b, zweiter kurzer Lauf, gestartet %DATE% %TIME%
>"%L1%" echo Status_Phase8b5: Renderer bauen (ant clean jar, mit check-packages)
>"%L2%" echo Status_Phase8b6: SeegangCheck --nur 2,3 (aus den Archiven des ersten Laufs)
>"%L3%" echo Status_Phase8b7: WaterCheck, HalligHooge bauen, Anwendung mit Seegang

set "N=1 Renderer bauen (clean jar, check-packages)"
set "L=%L1%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" goto :abbruch

set "N=2 SeegangCheck --nur 2,3 (Pruefsteine 2 bis 6, 8 bis 10, Status-Bild bei Tag)"
set "L=%L2%"
set "C=%R% com.dan.lauf.demo.SeegangCheck --gebiet %G% --archiv %A8% --nur 2,3"
call :lauf

set "N=3 WaterCheck (Wasser Stufe 1 unveraendert)"
set "L=%L3%"
set "C=%R% com.dan.render.water.demo.WaterCheck --gebiet %G% --ohne-attest"
call :lauf

set "N=4 Renderer.jar ins Projekt, HalligHooge bauen (clean jar, Importe und Umfang)"
set "L=%L3%"
copy /y dist\Renderer.jar "%HH%\lib\Renderer.jar" >>"%L3%" 2>&1
cd /d "%HH%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" goto :abbruch

set "N=5 Anwendung: Sturmflut mit Seegang, 40 s"
set "L=%L3%"
set "C=%APP% --szenario seegang --sekunden 40"
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
