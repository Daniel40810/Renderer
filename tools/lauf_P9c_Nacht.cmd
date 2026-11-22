@echo off
rem P9c, Kette ueber Nacht (Daniel, 18.11.2026: F87 A, Kette): bauen, MorphoCheck, das Modelljahr in drei Laeufen, Feldvergleiche, Bild, Projekt.
rem 1 Renderer bauen; 2 MorphoCheck (Pruefsteine 1 bis 8); 3 M1a: 14. bis 16.10. mit Faktor 60 (Vorlauf 13.10.) nach _p9c\archiv_m1a;
rem 4 M1b: 17. bis 19.10. mit Faktor 60, Boden aus M1a (Vorlauf 16.10.) nach _p9c\archiv_m1b; 5 M2: Sturm 25.10. 06 UTC bis 26.10. 12 UTC
rem mit Faktor 4, Boden aus M1b, nach _p9c\archiv_m2; alle mit Deckel 0.01, Rauheit aus dem Sediment, Seegang 20 x 18 auf 1000 m und 200 m.
rem Bricht ein Lauf des Modelljahrs ab (Zeile "FEHLT  Ablauf" im Status), endet die Kette dort.
rem 6 Aufbereitung morpho; 7 MorphoFeldCheck, SedimentLaufCheck --nur 2 (M1a) und --nur 3 (M1b); 8 WaterCheck, TerrainCheck, BuehneCheck;
rem 9 Renderer.jar ins Projekt, HalligHooge bauen, Anwendung 40 s mit --szenario morpho.
rem Atteste: Renderer\docs\status\P9c_Kern.md, P9c_M1a.md, P9c_M1b.md, P9c_M2.md, P9c_Feld.md, P9c_Feld_M1a.md, P9c_Bild.md.
rem Ausgaben: HalligHooge\docs\status\Status_Phase9cN*.txt (UTF-8), Uebersicht in Status_Phase9cN.txt.
rem Anhalten zwischen zwei Schritten: eine Datei STOPP.txt neben dieses Skript legen.
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0.."

set "HH=C:\Users\Dan40810\Documents\NetBeansProjects\HalligHooge"
set "ST=%HH%\docs\status"
set "G=D:\Daten\HalligHooge\gebiet"
set "A9=D:\Daten\HalligHooge\_p9b\archiv"
set "A9R=D:\Daten\HalligHooge\_p9b\archiv_ruhig"
set "M1A=D:\Daten\HalligHooge\_p9c\archiv_m1a"
set "M1B=D:\Daten\HalligHooge\_p9c\archiv_m1b"
set "M2=D:\Daten\HalligHooge\_p9c\archiv_m2"
set "F3=400,850,700,400"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
set "R=java %U% --enable-native-access=ALL-UNNAMED -Xmx12g -cp dist\Renderer.jar"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "SEE=--stuetzen 20,18 --seegang-gitter 1000,200"
set "UE=%ST%\Status_Phase9cN.txt"
set "SZ=%HH%\szenen\Hooge.szenen"
set "APP=java %U% --enable-native-access=ALL-UNNAMED -Xmx12g -jar dist\HalligHooge.jar --szenen szenen\Hooge.szenen"
set "MO=--gebiet %G% --archiv-ruhig - --lauf sturm --nur 1 --fenster3 %F3% --vorlauf 3 --deckel 0.01 --rauheit %SEE%"
set "L1=%ST%\Status_Phase9cN1.txt"
set "L2=%ST%\Status_Phase9cN2.txt"
set "L3=%ST%\Status_Phase9cN3.txt"
set "L4=%ST%\Status_Phase9cN4.txt"
set "L5=%ST%\Status_Phase9cN5.txt"
set "L6=%ST%\Status_Phase9cN6.txt"
set "L7=%ST%\Status_Phase9cN7.txt"

>"%UE%" echo Lauf P9c Nacht, gestartet %DATE% %TIME%
>"%L1%" echo Status_Phase9cN1: Renderer bauen
>"%L2%" echo Status_Phase9cN2: MorphoCheck
>"%L3%" echo Status_Phase9cN3: M1a, 14. bis 16.10., Faktor 60, nach %M1A%
>"%L4%" echo Status_Phase9cN4: M1b, 17. bis 19.10., Faktor 60, Boden aus M1a, nach %M1B%
>"%L5%" echo Status_Phase9cN5: M2, Sturm, Faktor 4, Boden aus M1b, nach %M2%
>"%L6%" echo Status_Phase9cN6: Aufbereitung morpho, MorphoFeldCheck, SedimentLaufCheck --nur 2 und --nur 3
>"%L7%" echo Status_Phase9cN7: WaterCheck, TerrainCheck, BuehneCheck, Projekt, Anwendung

set "N=1 Renderer bauen (clean jar, check-packages)"
set "L=%L1%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" goto :abbruch

set "N=2 MorphoCheck (Pruefsteine 1 bis 8)"
set "L=%L2%"
set "C=%R% com.dan.lauf.demo.MorphoCheck --gebiet %G%"
call :lauf

set "N=3 M1a (14. bis 16.10., Faktor 60)"
set "L=%L3%"
set "C=%R% com.dan.lauf.demo.SedimentLaufCheck %MO% --archiv %M1A% --start 2026-10-13T00:00:00Z --ab 2026-10-14T00:00:00Z --sediment-ab 2026-10-14T00:00:00Z --ende 2026-10-17T00:00:00Z --faktor 60 --attest P9c_M1a"
call :lauf
findstr /c:"FEHLT  Ablauf" "%L3%" >nul && goto :abbruch

set "N=4 M1b (17. bis 19.10., Faktor 60, Boden aus M1a)"
set "L=%L4%"
set "C=%R% com.dan.lauf.demo.SedimentLaufCheck %MO% --archiv %M1B% --start 2026-10-16T00:00:00Z --ab 2026-10-17T00:00:00Z --sediment-ab 2026-10-17T00:00:00Z --ende 2026-10-20T00:00:00Z --faktor 60 --boden-aus %M1A% --attest P9c_M1b"
call :lauf
findstr /c:"FEHLT  Ablauf" "%L4%" >nul && goto :abbruch

set "N=5 M2 (Sturm, Faktor 4, Boden aus M1b)"
set "L=%L5%"
set "C=%R% com.dan.lauf.demo.SedimentLaufCheck %MO% --archiv %M2% --start 2026-10-24T00:00:00Z --ab 2026-10-25T06:00:00Z --sediment-ab 2026-10-25T06:00:00Z --ende 2026-10-26T12:00:00Z --faktor 4 --boden-aus %M1B% --attest P9c_M2"
call :lauf
findstr /c:"FEHLT  Ablauf" "%L5%" >nul && goto :abbruch

set "N=6 Aufbereitung, Schritt morpho (EasyGSH-Bathymetrie 1996 und 2016)"
set "L=%L6%"
set "C=java %U% -Xmx6g -cp dist\Renderer.jar com.dan.aufbereitung.Aufbereitung ..\HalligHooge\gebiet\Hooge.rezept --schritte morpho"
call :lauf

set "N=6 MorphoFeldCheck (Pruefsteine 13, 14, 15)"
set "L=%L6%"
set "C=%R% com.dan.lauf.demo.MorphoFeldCheck --gebiet %G% --m1a %M1A% --m1 %M1B% --m2 %M2%"
call :lauf

set "N=6 Feldvergleiche auf M1a (Stroemung gegen EasyGSH, Truebung gegen Sentinel-2)"
set "L=%L6%"
set "C=%R% com.dan.lauf.demo.SedimentLaufCheck --gebiet %G% --archiv %M2% --archiv-ruhig %M1A% --fenster3 %F3% --nur 2 --attest P9c_Feld_M1a"
call :lauf

set "N=6 Bild aus M1b (Pruefsteine 10 bis 12 von P9b, Status-Bild)"
set "L=%L6%"
set "C=%R% com.dan.lauf.demo.SedimentLaufCheck --gebiet %G% --archiv %M2% --archiv-ruhig %M1B% --fenster3 %F3% --nur 3 --attest P9c_Bild --fahne 466000,6046500,4000,0,-85 --rippel 468990,6045410,250 --rippelzeit 2026-10-18T13:00:00Z"
call :lauf

set "N=8 WaterCheck"
set "L=%L7%"
set "C=%R% com.dan.render.water.demo.WaterCheck --gebiet %G% --ohne-attest"
call :lauf

set "N=8 TerrainCheck"
set "L=%L7%"
set "C=%R% com.dan.render.terrain.demo.TerrainCheck --gebiet %G% --ohne-attest"
call :lauf

set "N=8 BuehneCheck"
set "L=%L7%"
set "C=%R% com.dan.lauf.demo.BuehneCheck --szenen %SZ% --ohne-attest"
call :lauf

set "N=9 Renderer.jar ins Projekt, HalligHooge bauen, Anwendung 40 s"
set "L=%L7%"
copy /y dist\Renderer.jar "%HH%\lib\Renderer.jar" >>"%L7%" 2>&1
cd /d "%HH%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" goto :abbruch
set "C=%APP% --szenario morpho --sekunden 40"
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
