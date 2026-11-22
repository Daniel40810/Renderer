@echo off
rem P9c, Lauf A (Werkbuch HalligHooge Teil A, 12.10, Feinplan P9c): bauen, Pruefsteine 1 bis 9, Probelaeufe.
rem 1 Renderer bauen (ant clean jar, mit check-packages); 2 MorphoCheck (Pruefsteine 1 bis 7 auf dem Prozessor, 8 Karte gegen
rem Prozessor am Bus), SedimentKarteCheck und SedimentCheck (Sediment 0.3); 3 drei Probelaeufe ueber 6 h (13.10., mit Rueckwirkung
rem und Faktor 120): grober Seegang wie in M1; dazu die Rauheit aus dem Sediment; feiner Seegang wie in P9b; 4 Feldvergleiche
rem aus den Archiven von P9b (Pruefstein 7 dort neu, auch ueber tiefem Wasser nach F86 A).
rem Atteste: Renderer\docs\status\P9c_Kern.md, P9a_SedimentKarte.md, P9a_Sediment.md, P9c_Probe_*.md, P9b_Sediment.md.
rem Ausgaben: HalligHooge\docs\status\Status_Phase9cA*.txt (UTF-8), Uebersicht in Status_Phase9cA.txt.
rem Anhalten zwischen zwei Schritten: eine Datei STOPP.txt neben dieses Skript legen.
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0.."

set "HH=C:\Users\Dan40810\Documents\NetBeansProjects\HalligHooge"
set "ST=%HH%\docs\status"
set "G=D:\Daten\HalligHooge\gebiet"
set "A9=D:\Daten\HalligHooge\_p9b\archiv"
set "A9R=D:\Daten\HalligHooge\_p9b\archiv_ruhig"
set "M1=D:\Daten\HalligHooge\_p9c\archiv_m1"
set "M2=D:\Daten\HalligHooge\_p9c\archiv_m2"
set "F3=400,850,700,400"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
set "R=java %U% --enable-native-access=ALL-UNNAMED -Xmx12g -cp dist\Renderer.jar"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "SEE=--stuetzen 20,18 --seegang-gitter 1000,200"
set "UE=%ST%\Status_Phase9cA.txt"
set "L1=%ST%\Status_Phase9cA1.txt"
set "L2=%ST%\Status_Phase9cA2.txt"
set "L3=%ST%\Status_Phase9cA3.txt"
set "L4=%ST%\Status_Phase9cA4.txt"
set "Z=%TIME: =0%"
set "AP=D:\Daten\HalligHooge\_p9c\probe_%DATE:~-4%%DATE:~3,2%%DATE:~0,2%_%Z:~0,2%%Z:~3,2%"
set "PR=--gebiet %G% --archiv-ruhig - --lauf sturm --nur 1 --fenster3 %F3% --start 2026-10-13T00:00:00Z --ab 2026-10-13T03:00:00Z --sediment-ab 2026-10-13T03:00:00Z --ende 2026-10-13T06:00:00Z --vorlauf 2 --faktor 120"

>"%UE%" echo Lauf P9c A, gestartet %DATE% %TIME%
>"%L1%" echo Status_Phase9cA1: Renderer bauen (ant clean jar, mit check-packages)
>"%L2%" echo Status_Phase9cA2: MorphoCheck, SedimentKarteCheck, SedimentCheck
>"%L3%" echo Status_Phase9cA3: Probelaeufe nach %AP%_grob, _rauheit, _fein
>"%L4%" echo Status_Phase9cA4: Feldvergleiche aus den Archiven von P9b

set "N=1 Renderer bauen (clean jar, check-packages)"
set "L=%L1%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" goto :abbruch

set "N=2 MorphoCheck (Pruefsteine 1 bis 8)"
set "L=%L2%"
set "C=%R% com.dan.lauf.demo.MorphoCheck --gebiet %G%"
call :lauf

set "N=2 SedimentKarteCheck (Karte gegen Referenz, mit dem Fall P9c)"
set "L=%L2%"
set "C=%R% com.dan.physics.sediment.demo.SedimentKarteCheck"
call :lauf

set "N=2 SedimentCheck (Referenz)"
set "L=%L2%"
set "C=java %U% -Xmx8g -cp dist\Renderer.jar com.dan.physics.sediment.demo.SedimentCheck"
call :lauf

set "N=3 Probelauf 6 h, grober Seegang (wie M1)"
set "L=%L3%"
set "C=%R% com.dan.lauf.demo.SedimentLaufCheck %PR% --archiv %AP%_grob %SEE% --attest P9c_Probe_grob"
call :lauf

set "N=3 Probelauf 6 h, grober Seegang, Rauheit aus dem Sediment"
set "L=%L3%"
set "C=%R% com.dan.lauf.demo.SedimentLaufCheck %PR% --archiv %AP%_rauheit %SEE% --rauheit --attest P9c_Probe_rauheit"
call :lauf

set "N=3 Probelauf 6 h, feiner Seegang (28 x 36 auf 500 m und 100 m)"
set "L=%L3%"
set "C=%R% com.dan.lauf.demo.SedimentLaufCheck %PR% --archiv %AP%_fein --attest P9c_Probe_fein"
call :lauf

set "N=4 Feldvergleiche aus den Archiven von P9b (Pruefsteine 4 bis 8 von P9b, 7 auch ueber tiefem Wasser)"
set "L=%L4%"
set "C=%R% com.dan.lauf.demo.SedimentLaufCheck --gebiet %G% --archiv %A9% --archiv-ruhig %A9R% --fenster3 %F3% --nur 2"
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
