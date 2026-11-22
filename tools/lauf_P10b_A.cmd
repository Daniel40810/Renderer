@echo off
rem P10b, Lauf A (Werkbuch HalligHooge Teil A, 12.11): Renderer bauen, Wetter backen (Schritt luft), atmos pruefen,
rem Nachlauf aus den Archiven archiv_ruhig (30.10.) und archiv_m1b (17.-19.10.), Pruefsteine 1-8 und 15, Tabelle der Tage.
rem Ausgabe: HalligHooge\docs\status\Status_Phase10bA.txt (Ablauf), ...A1 (Bau), ...A2 (Aufbereitung luft), ...A3 (CoreCheck),
rem ...A4 (AtmosCheck), ...A5 (KimmungCheck), ...A6 (AtmosLaufCheck), alle UTF-8.
rem Atteste: Renderer\docs\status\P10a_Atmos.md, P10a_Kimmung.md, P10b_Atmos.md
rem Der Nachlauf legt in die Archive: atmos_G3 (surf.wet, surf.T) und luft_G3s (air.n), zusammen rund 3 GB.
setlocal EnableExtensions
chcp 65001 >nul
set "RE=C:\Users\Dan40810\Documents\NetBeansProjects\Renderer"
set "ST=C:\Users\Dan40810\Documents\NetBeansProjects\HalligHooge\docs\status"
set "S=%ST%\Status_Phase10bA.txt"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
cd /d "%RE%"
>"%S%" echo Status_Phase10bA: gestartet %DATE% %TIME%

echo [%TIME%] Renderer bauen ...
call "%ANT%" -f build.xml clean jar >"%ST%\Status_Phase10bA1.txt" 2>&1
set "E=%ERRORLEVEL%"
>>"%S%" echo 1 Bau (ant clean jar, check-packages): Exit %E%  %TIME%
if not "%E%"=="0" goto :ende

echo [%TIME%] Aufbereitung, Schritt luft ...
java %U% -Xmx6g -cp dist\Renderer.jar com.dan.aufbereitung.Aufbereitung ..\HalligHooge\gebiet\Hooge.rezept --schritte luft >"%ST%\Status_Phase10bA2.txt" 2>&1
set "E=%ERRORLEVEL%"
>>"%S%" echo 2 Aufbereitung luft: Exit %E%  %TIME%
if not "%E%"=="0" goto :ende

echo [%TIME%] CoreCheck ...
java %U% -Xmx8g -cp dist\Renderer.jar com.dan.core.demo.CoreCheck >"%ST%\Status_Phase10bA3.txt" 2>&1
set "E=%ERRORLEVEL%"
>>"%S%" echo 3 CoreCheck: Exit %E%  %TIME%

echo [%TIME%] AtmosCheck ...
java %U% -Xmx8g -cp dist\Renderer.jar com.dan.physics.atmos.demo.AtmosCheck >"%ST%\Status_Phase10bA4.txt" 2>&1
set "E=%ERRORLEVEL%"
>>"%S%" echo 4 AtmosCheck: Exit %E%  %TIME%

echo [%TIME%] KimmungCheck ...
java %U% -Xmx8g -cp dist\Renderer.jar com.dan.lauf.demo.KimmungCheck >"%ST%\Status_Phase10bA5.txt" 2>&1
set "E=%ERRORLEVEL%"
>>"%S%" echo 5 KimmungCheck: Exit %E%  %TIME%

echo [%TIME%] AtmosLaufCheck (Nachlauf aus den Archiven) ...
java %U% -Xmx16g -cp dist\Renderer.jar com.dan.lauf.demo.AtmosLaufCheck --gebiet D:\Daten\HalligHooge\gebiet --archiv D:\Daten\HalligHooge\_p9b\archiv_ruhig,D:\Daten\HalligHooge\_p9c\archiv_m1b --name P10b_Atmos >"%ST%\Status_Phase10bA6.txt" 2>&1
set "E=%ERRORLEVEL%"
>>"%S%" echo 6 AtmosLaufCheck: Exit %E%  %TIME%

:ende
>>"%S%" echo FERTIG %DATE% %TIME%
echo [%TIME%] Fertig. Dieses Fenster schliesst sich in 20 s.
timeout /t 20 >nul
