@echo off
rem Teil B, Nachtrag (Werkbuch HalligHooge Teil B, B.10): HalligHooge neu bauen und die Anwendung starten.
rem Der Bau prueft jetzt selbst die Importe und den Umfang des Projekts (tools\ImportPruefung.java, Pruefsteine 4 und 5).
rem Ausgabe: HalligHooge\docs\status\Status_TeilB5.txt (UTF-8). Der Renderer wird nicht neu gebaut.
setlocal EnableExtensions
chcp 65001 >nul
set "HH=C:\Users\Dan40810\Documents\NetBeansProjects\HalligHooge"
set "L=%HH%\docs\status\Status_TeilB5.txt"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
cd /d "%HH%"
>"%L%" echo Status_TeilB5: HalligHooge bauen (ant clean jar, mit ImportPruefung), gestartet %DATE% %TIME%
echo [%TIME%] HalligHooge bauen ...
call "%ANT%" -f build.xml clean jar >>"%L%" 2>&1
set "E=%ERRORLEVEL%"
>>"%L%" echo.
>>"%L%" echo Bau: Exit %E%  %DATE% %TIME%
if "%E%"=="0" goto :start
echo Der Bau ist fehlgeschlagen - siehe %L%
pause
goto :eof

:start
echo [%TIME%] Die Anwendung startet. Dieses Fenster schliesst sich mit ihr.
call "%HH%\HalligHooge.cmd"
