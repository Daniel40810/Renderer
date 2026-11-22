@echo off
rem P8a (Werkbuch HalligHooge Teil A, 12.9): Renderer bauen (mit check-packages, Pruefstein 9) und den WavesCheck rechnen.
rem Ausgabe: HalligHooge\docs\status\Status_Phase8a.txt (Ablauf), Status_Phase8a1.txt (Bau), Status_Phase8a2.txt (WavesCheck),
rem Status_Phase8a3.txt (WavesKarteCheck: Karte gegen Referenz mit Stroemung und Quellen, Zeiten), alle UTF-8.
rem Atteste: Renderer\docs\status\P8a_Waves.md, P8a_WavesKarte.md
setlocal EnableExtensions
chcp 65001 >nul
set "RE=C:\Users\Dan40810\Documents\NetBeansProjects\Renderer"
set "ST=C:\Users\Dan40810\Documents\NetBeansProjects\HalligHooge\docs\status"
set "S=%ST%\Status_Phase8a.txt"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
cd /d "%RE%"
>"%S%" echo Status_Phase8a: gestartet %DATE% %TIME%

echo [%TIME%] Renderer bauen ...
call "%ANT%" -f build.xml clean jar >"%ST%\Status_Phase8a1.txt" 2>&1
set "E=%ERRORLEVEL%"
>>"%S%" echo 1 Bau (ant clean jar, check-packages): Exit %E%  %TIME%
if not "%E%"=="0" goto :ende

echo [%TIME%] WavesKarteCheck ...
java %U% --enable-native-access=ALL-UNNAMED -Xmx8g -cp dist\Renderer.jar com.dan.physics.waves.demo.WavesKarteCheck >"%ST%\Status_Phase8a3.txt" 2>&1
set "E=%ERRORLEVEL%"
>>"%S%" echo 3 WavesKarteCheck: Exit %E%  %TIME%

echo [%TIME%] WavesCheck ...
java %U% -Xmx8g -cp dist\Renderer.jar com.dan.physics.waves.demo.WavesCheck >"%ST%\Status_Phase8a2.txt" 2>&1
set "E=%ERRORLEVEL%"
>>"%S%" echo 2 WavesCheck: Exit %E%  %TIME%

:ende
>>"%S%" echo FERTIG %DATE% %TIME%
echo [%TIME%] Fertig. Dieses Fenster schliesst sich in 20 s.
timeout /t 20 >nul
