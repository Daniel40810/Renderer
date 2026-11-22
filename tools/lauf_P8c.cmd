@echo off
rem P8c (Werkbuch HalligHooge Teil A, 12.9, Feinplan P8c, Werkbuch 22): der eine gebuendelte Lauf ueber Nacht.
rem 1 Renderer bauen (check-packages); 2 StauCheck (Pruefsteine 1, 2: Stau am Strand, Kraft Karte gegen Referenz);
rem 3 WavesKarteCheck kurz (Karte gegen Referenz, mit der Kappung W7); 4 SeegangCheck: Sturm und ruhiger Tag mit Seegang
rem und Rueckwirkung neu ins Archiv D:\Daten\HalligHooge\_p8c (rund 2,2 h), Feldvergleich gegen EasyGSH 2015, Bild;
rem 5 TideCheck: Sturmflut ohne und mit Seegang gegen die Pegel (Pruefstein 4 von P5, rund 1,5 h);
rem 6 WaterCheck, BuehneCheck; 7 HalligHooge bauen, Anwendung 40 s; 8 ReferenzCheck mit Seegang bis Rauschen 1 (rund 9 h).
rem Ausgaben: HalligHooge\docs\status\Status_Phase8c*.txt (UTF-8), Uebersicht in Status_Phase8c.txt.
rem Atteste: Renderer\docs\status\P8c_Stau.md, P8c_Seegang.md (Teil 1 bis 3 mit den neuen Archiven), P8c_Tide.md,
rem P8c_Referenz.md; Bilder P8c_Seegang.png, P8c_Bild.png, P8c_Feld.png, P8c_Tide.png.
rem Anhalten zwischen zwei Schritten: eine Datei STOPP.txt neben dieses Skript legen.
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0.."

set "HH=C:\Users\Dan40810\Documents\NetBeansProjects\HalligHooge"
set "ST=%HH%\docs\status"
set "G=D:\Daten\HalligHooge\gebiet"
set "A8=D:\Daten\HalligHooge\_p8c\archiv"
set "A8R=D:\Daten\HalligHooge\_p8c\archiv_ruhig"
set "SZ=%HH%\szenen\Hooge.szenen"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
set "R=java %U% --enable-native-access=ALL-UNNAMED -Xmx12g -cp dist\Renderer.jar"
set "APP=java %U% --enable-native-access=ALL-UNNAMED -Xmx12g -jar dist\HalligHooge.jar --szenen szenen\Hooge.szenen"
set "UE=%ST%\Status_Phase8c.txt"
set "L1=%ST%\Status_Phase8c1.txt"
set "L2=%ST%\Status_Phase8c2.txt"
set "L3=%ST%\Status_Phase8c3.txt"
set "L4=%ST%\Status_Phase8c4.txt"
set "L5=%ST%\Status_Phase8c5.txt"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"

>"%UE%" echo Lauf P8c, gestartet %DATE% %TIME%
>"%L1%" echo Status_Phase8c1: Renderer bauen (ant clean jar, mit check-packages), StauCheck, WavesKarteCheck kurz
>"%L2%" echo Status_Phase8c2: SeegangCheck (Laeufe mit Seegang und Rueckwirkung ins Archiv _p8c, Auswertung, Feldvergleich, Bild)
>"%L3%" echo Status_Phase8c3: TideCheck --nur sturm --zerlegen --seegang (Sturmflut ohne und mit Seegang gegen die Pegel)
>"%L4%" echo Status_Phase8c4: WaterCheck, BuehneCheck, HalligHooge bauen, Anwendung
>"%L5%" echo Status_Phase8c5: ReferenzCheck --pfade 256 --bis-rauschen 1, sechs Ansichten, mit Seegang

set "N=1 Renderer bauen (clean jar, check-packages: Pruefstein 11)"
set "L=%L1%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" goto :abbruch

set "N=2 StauCheck (Pruefsteine 1 und 2)"
set "L=%L1%"
set "C=%R% com.dan.lauf.demo.StauCheck"
call :lauf

set "N=3 WavesKarteCheck --teil 6 --kurz (Karte gegen Referenz, mit Kappung W7)"
set "L=%L1%"
set "C=%R% com.dan.physics.waves.demo.WavesKarteCheck --teil 6 --kurz"
call :lauf

set "N=4 SeegangCheck mit Rueckwirkung (Pruefsteine 3, 4, 7, 8, 9)"
set "L=%L2%"
set "C=%R% com.dan.lauf.demo.SeegangCheck --gebiet %G% --archiv %A8% --archiv-ruhig %A8R% --phase P8c"
call :lauf

set "N=5 TideCheck Sturmflut ohne und mit Seegang (Pruefsteine 5 und 6)"
set "L=%L3%"
set "C=%R% com.dan.lauf.demo.TideCheck --gebiet %G% --karte --nur sturm --zerlegen --seegang --name P8c_Tide"
call :lauf

set "N=6 WaterCheck und BuehneCheck"
set "L=%L4%"
set "C=%R% com.dan.render.water.demo.WaterCheck --gebiet %G% --ohne-attest"
call :lauf
set "C=%R% com.dan.lauf.demo.BuehneCheck --szenen %SZ% --ohne-attest"
call :lauf

set "N=7 Renderer.jar ins Projekt, HalligHooge bauen, Anwendung 40 s"
set "L=%L4%"
copy /y dist\Renderer.jar "%HH%\lib\Renderer.jar" >>"%L4%" 2>&1
cd /d "%HH%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" goto :abbruch
set "C=%APP% --szenario seegang --sekunden 40"
call :lauf
cd /d "%~dp0.."

set "N=8 ReferenzCheck mit Seegang (Pruefstein 10, sechs Ansichten, bis Rauschen 1)"
set "L=%L5%"
set "C=java %U% --enable-native-access=ALL-UNNAMED -Xmx12g -cp dist\Renderer.jar com.dan.lauf.demo.ReferenzCheck --gebiet %G% --archiv %A8% --archiv-ruhig %A8R% --pfade 256 --bis-rauschen 1 --nur 123456 --name P8c_Referenz"
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
