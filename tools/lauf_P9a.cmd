@echo off
rem P9a (Werkbuch HalligHooge Teil A, 12.10): Renderer bauen (mit check-packages, Pruefstein 12) und das Sediment pruefen.
rem Ausgabe: HalligHooge\docs\status\Status_Phase9a.txt (Ablauf), Status_Phase9a1.txt (Bau), Status_Phase9a2.txt (SedimentCheck),
rem Status_Phase9a3.txt (SedimentKarteCheck: Karte gegen Referenz, Zeiten), alle UTF-8.
rem Atteste: Renderer\docs\status\P9a_Sediment.md, P9a_SedimentKarte.md
setlocal EnableExtensions
chcp 65001 >nul
set "RE=C:\Users\Dan40810\Documents\NetBeansProjects\Renderer"
set "ST=C:\Users\Dan40810\Documents\NetBeansProjects\HalligHooge\docs\status"
set "S=%ST%\Status_Phase9a.txt"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
cd /d "%RE%"
>"%S%" echo Status_Phase9a: gestartet %DATE% %TIME%

echo [%TIME%] Renderer bauen ...
call "%ANT%" -f build.xml clean jar >"%ST%\Status_Phase9a1.txt" 2>&1
set "E=%ERRORLEVEL%"
>>"%S%" echo 1 Bau (ant clean jar, check-packages): Exit %E%  %TIME%
if not "%E%"=="0" goto :ende

echo [%TIME%] SedimentKarteCheck ...
java %U% --enable-native-access=ALL-UNNAMED -Xmx8g -cp dist\Renderer.jar com.dan.physics.sediment.demo.SedimentKarteCheck >"%ST%\Status_Phase9a3.txt" 2>&1
set "E=%ERRORLEVEL%"
>>"%S%" echo 3 SedimentKarteCheck: Exit %E%  %TIME%

echo [%TIME%] SedimentCheck ...
java %U% -Xmx8g -cp dist\Renderer.jar com.dan.physics.sediment.demo.SedimentCheck >"%ST%\Status_Phase9a2.txt" 2>&1
set "E=%ERRORLEVEL%"
>>"%S%" echo 2 SedimentCheck: Exit %E%  %TIME%

:ende
>>"%S%" echo FERTIG %DATE% %TIME%
echo [%TIME%] Fertig. Dieses Fenster schliesst sich in 20 s.
timeout /t 20 >nul
