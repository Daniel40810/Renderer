@echo off
rem P10b, Lauf C (Werkbuch HalligHooge Teil A, 12.11): die Ansicht der Kimmung gegen die Referenz mit gekruemmten Strahlen (Pruefstein 13).
rem 1 Renderer bauen; 2 ReferenzCheck: 480 x 136 Bildpunkte mit Bildwinkel 2,82 Grad (derselbe Massstab wie die Ansicht mit 5,6 Grad auf 1080 Zeilen,
rem der Streifen um den Horizont ganz im Bild), 128 Pfade je Bildpunkt und Runde, bis das Rauschen in Bloecken 1 haelt, hoechstens 256.
rem Ausgaben: HalligHooge\docs\status\Status_Phase10bC.txt (Uebersicht), Status_Phase10bC1.txt. Attest Renderer\docs\status\P10b_Referenz.md, Bild P10b_Referenz.png.
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

set "UE=%ST%\Status_Phase10bC.txt"
set "L1=%ST%\Status_Phase10bC1.txt"

>"%UE%" echo Lauf P10b C, gestartet %DATE% %TIME%
>"%L1%" echo Status_Phase10bC1: Renderer bauen, ReferenzCheck --kimmung

set "N=1 Renderer bauen (clean jar, check-packages)"
set "L=%L1%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" goto :abbruch

set "N=2 ReferenzCheck, Ansicht der Kimmung"
set "L=%L1%"
set "C=%R% com.dan.lauf.demo.ReferenzCheck --gebiet %G% --kimmung %AK% --kimmung-zeit %ZEIT% --kimmung-blick 462600,6046900,4,325,0,2.82 --breite 480 --hoehe 136 --pfade 128 --bis-rauschen 1 --pfade-max 256 --name P10b_Referenz"
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
