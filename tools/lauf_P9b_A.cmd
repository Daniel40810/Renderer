@echo off
rem P9b, Lauf A (Werkbuch HalligHooge Teil A, 12.10): Renderer bauen, Aufbereitung sediment, Sediment 0.2 pruefen, Probelauf.
rem 1 Renderer bauen (ant clean jar, mit check-packages); 2 Aufbereitung, Schritt sediment (Kornfeld nach gebiet\sediment);
rem 3 SedimentKarteCheck und SedimentCheck (Sediment 0.2); 4 SedimentLaufCheck als Probelauf ueber 8 h Modellzeit
rem (23.10., abgelegt 05 bis 08 Uhr UTC, mit Seegang und Sediment auf der Karte) in einen eigenen Ordner _p9b\probe_<Zeit>.
rem Ausgaben: HalligHooge\docs\status\Status_Phase9bA*.txt (UTF-8), Uebersicht in Status_Phase9bA.txt.
rem Atteste: Renderer\docs\status\P9a_Sediment.md, P9a_SedimentKarte.md, P9b_Sediment.md.
rem Anhalten zwischen zwei Schritten: eine Datei STOPP.txt neben dieses Skript legen.
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0.."

set "HH=C:\Users\Dan40810\Documents\NetBeansProjects\HalligHooge"
set "ST=%HH%\docs\status"
set "G=D:\Daten\HalligHooge\gebiet"
set "A9=D:\Daten\HalligHooge\_p9b\archiv"
set "A9R=D:\Daten\HalligHooge\_p9b\archiv_ruhig"
set "F3=400,850,700,400"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
set "R=java %U% --enable-native-access=ALL-UNNAMED -Xmx12g -cp dist\Renderer.jar"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "UE=%ST%\Status_Phase9bA.txt"
set "L1=%ST%\Status_Phase9bA1.txt"
set "L2=%ST%\Status_Phase9bA2.txt"
set "L3=%ST%\Status_Phase9bA3.txt"
set "Z=%TIME: =0%"
set "AP=D:\Daten\HalligHooge\_p9b\probe_%DATE:~-4%%DATE:~3,2%%DATE:~0,2%_%Z:~0,2%%Z:~3,2%"

>"%UE%" echo Lauf P9b A, gestartet %DATE% %TIME%
>"%L1%" echo Status_Phase9bA1: Renderer bauen (ant clean jar, mit check-packages), Aufbereitung sediment
>"%L2%" echo Status_Phase9bA2: SedimentKarteCheck, SedimentCheck (Sediment 0.2)
>"%L3%" echo Status_Phase9bA3: SedimentLaufCheck, Probelauf nach %AP%

set "N=1 Renderer bauen (clean jar, check-packages)"
set "L=%L1%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" goto :abbruch

set "N=2 Aufbereitung, Schritt sediment (EasyGSH-DB 2016 nach gebiet\sediment)"
set "L=%L1%"
set "C=java %U% -Xmx6g -cp dist\Renderer.jar com.dan.aufbereitung.Aufbereitung ..\HalligHooge\gebiet\Hooge.rezept --schritte sediment"
call :lauf
if not "%E%"=="0" goto :abbruch

set "N=3 SedimentKarteCheck (Karte gegen Referenz, Zeiten)"
set "L=%L2%"
set "C=%R% com.dan.physics.sediment.demo.SedimentKarteCheck"
call :lauf

set "N=3 SedimentCheck (Referenz)"
set "L=%L2%"
set "C=java %U% -Xmx8g -cp dist\Renderer.jar com.dan.physics.sediment.demo.SedimentCheck"
call :lauf

set "N=4 SedimentLaufCheck, Probelauf 8 h (Pruefsteine 1, 2, 3, 4, 9)"
set "L=%L3%"
set "C=%R% com.dan.lauf.demo.SedimentLaufCheck --gebiet %G% --archiv %AP% --archiv-ruhig - --lauf sturm --fenster3 %F3% --start 2026-10-23T00:00:00Z --ab 2026-10-23T05:00:00Z --ende 2026-10-23T08:00:00Z --vorlauf 2"
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
