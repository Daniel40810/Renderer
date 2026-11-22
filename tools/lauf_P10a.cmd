@echo off
rem P10a (Werkbuch HalligHooge Teil A, 12.11): Renderer bauen (mit check-packages, Pruefstein 12) und atmos pruefen.
rem Ausgabe: HalligHooge\docs\status\Status_Phase10a.txt (Ablauf), Status_Phase10a1.txt (Bau), Status_Phase10a2.txt (AtmosCheck),
rem Status_Phase10a3.txt (KimmungCheck: Strahlen durch das Profil), alle UTF-8.
rem Atteste: Renderer\docs\status\P10a_Atmos.md, P10a_Kimmung.md
setlocal EnableExtensions
chcp 65001 >nul
set "RE=C:\Users\Dan40810\Documents\NetBeansProjects\Renderer"
set "ST=C:\Users\Dan40810\Documents\NetBeansProjects\HalligHooge\docs\status"
set "S=%ST%\Status_Phase10a.txt"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
cd /d "%RE%"
>"%S%" echo Status_Phase10a: gestartet %DATE% %TIME%

echo [%TIME%] Renderer bauen ...
call "%ANT%" -f build.xml clean jar >"%ST%\Status_Phase10a1.txt" 2>&1
set "E=%ERRORLEVEL%"
>>"%S%" echo 1 Bau (ant clean jar, check-packages): Exit %E%  %TIME%
if not "%E%"=="0" goto :ende

echo [%TIME%] AtmosCheck ...
java %U% -Xmx8g -cp dist\Renderer.jar com.dan.physics.atmos.demo.AtmosCheck >"%ST%\Status_Phase10a2.txt" 2>&1
set "E=%ERRORLEVEL%"
>>"%S%" echo 2 AtmosCheck: Exit %E%  %TIME%

echo [%TIME%] KimmungCheck ...
java %U% -Xmx8g -cp dist\Renderer.jar com.dan.lauf.demo.KimmungCheck >"%ST%\Status_Phase10a3.txt" 2>&1
set "E=%ERRORLEVEL%"
>>"%S%" echo 3 KimmungCheck: Exit %E%  %TIME%

:ende
>>"%S%" echo FERTIG %DATE% %TIME%
echo [%TIME%] Fertig. Dieses Fenster schliesst sich in 20 s.
timeout /t 20 >nul
