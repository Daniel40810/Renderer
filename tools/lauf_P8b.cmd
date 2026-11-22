@echo off
rem P8b (Werkbuch HalligHooge Teil A, 12.9, Feinplan P8b): der eine gebuendelte Lauf zur Abnahme auf dem Ryzen.
rem Baut den Renderer, bereitet den Seegang aus ERA5 auf, prueft die Karte (kurz), rechnet Sturm und ruhigen Tag mit
rem Seegang ins Archiv D:\Daten\HalligHooge\_p8 (SeegangCheck, rund 1,5 h), prueft das Wasser (WaterCheck) und die
rem Buehne (BuehneCheck), baut das Projekt HalligHooge und startet die Anwendung kurz im Szenario mit Seegang.
rem Ausgaben: HalligHooge\docs\status\Status_Phase8b*.txt (UTF-8), Uebersicht in Status_Phase8b.txt.
rem Atteste: Renderer\docs\status\P8b_Seegang.md, Status-Bilder P8b_Seegang.png und P8b_Bild.png.
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
set "UE=%ST%\Status_Phase8b.txt"
set "L1=%ST%\Status_Phase8b1.txt"
set "L2=%ST%\Status_Phase8b2.txt"
set "L3=%ST%\Status_Phase8b3.txt"
set "L4=%ST%\Status_Phase8b4.txt"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"

>"%UE%" echo Lauf P8b, gestartet %DATE% %TIME%
>"%L1%" echo Status_Phase8b1: Renderer bauen (ant clean jar, mit check-packages), Aufbereitung seegang, WavesKarteCheck kurz
>"%L2%" echo Status_Phase8b2: SeegangCheck (Laeufe mit Seegang ins Archiv, Auswertung, Bild)
>"%L3%" echo Status_Phase8b3: WaterCheck, BuehneCheck
>"%L4%" echo Status_Phase8b4: HalligHooge bauen, ImportPruefung, Anwendung mit Seegang

set "N=1 Renderer bauen (clean jar, check-packages: Pruefstein 1 und Feldnamen)"
set "L=%L1%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" goto :abbruch

set "N=2 Aufbereitung, Schritt seegang (ERA5 nach gebiet\antrieb\seegang.*)"
set "L=%L1%"
set "C=java %U% -Xmx4g -cp dist\Renderer.jar com.dan.aufbereitung.Aufbereitung ..\HalligHooge\gebiet\Hooge.rezept --schritte seegang"
call :lauf
if not "%E%"=="0" goto :abbruch

set "N=3 WavesKarteCheck --teil 6 --kurz (Karte gegen Referenz, Randspektrum je Randzelle)"
set "L=%L1%"
set "C=%R% com.dan.physics.waves.demo.WavesKarteCheck --teil 6 --kurz"
call :lauf

set "N=4 SeegangCheck (Pruefsteine 1 bis 10, 12)"
set "L=%L2%"
set "C=%R% com.dan.lauf.demo.SeegangCheck --gebiet %G% --archiv %A8%"
call :lauf

set "N=5 WaterCheck (Wasser Stufe 1 unveraendert)"
set "L=%L3%"
set "C=%R% com.dan.render.water.demo.WaterCheck --gebiet %G% --ohne-attest"
call :lauf

set "N=6 BuehneCheck (Szenario mit Seegang)"
set "L=%L3%"
set "C=%R% com.dan.lauf.demo.BuehneCheck --szenen %SZ% --ohne-attest"
call :lauf

set "N=7 Renderer.jar ins Projekt, HalligHooge bauen (clean jar, Importe und Umfang: Pruefstein 11)"
set "L=%L4%"
copy /y dist\Renderer.jar "%HH%\lib\Renderer.jar" >>"%L4%" 2>&1
cd /d "%HH%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" goto :abbruch

set "N=8 Anwendung: Sturmflut mit Seegang, 40 s"
set "L=%L4%"
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
