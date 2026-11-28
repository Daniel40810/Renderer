@echo off
rem Seegang Lauf L7p (Seegang Werkbuch, Datei 113): die Kette wieder heil machen und das Abbild neu erzeugen.
rem Anlass: L7o hat mit "clean jar" auf HalligHooge dist\abbild geloescht (HalligHooge.exe 2.7.0), und nach den
rem Git-Resets fehlten im Renderer die Seegang-Quellen. src und tools sind aus Commit c0a17f9 zurueckgeschrieben.
rem Schritte: 1 Renderer clean jar, Renderer.jar nach Steuerhaus\lib und HalligHooge\lib; 2 Steuerhaus clean jar,
rem Steuerhaus.jar nach HalligHooge\lib; 3 HalligHooge clean jar; 4 Abbild 2.8.0 (dist\abbild\HalligHooge\HalligHooge.exe).
rem Ausgabe: HalligHooge\docs\status\Status_L7p.txt. Erwartete Dauer: rund 5 min. Die App muss geschlossen sein.
rem Netz: keins. Datenbank: nicht noetig. Gestartet von Claude per Explorer.
setlocal EnableExtensions
chcp 65001 >nul
set "P=C:\Users\Dan40810\Documents\NetBeansProjects"
set "RE=%P%\Renderer"
set "SH=%P%\Steuerhaus"
set "HH=%P%\HalligHooge"
set "ST=%HH%\docs\status"
set "VER=2.8.0"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
if not defined JDK if exist "C:\Program Files\Java\jdk-21\bin\jpackage.exe" set "JDK=C:\Program Files\Java\jdk-21"
if not exist "%ST%" mkdir "%ST%"
set "L=%ST%\Status_L7p.txt"
>"%L%" echo Lauf L7p (Kette bauen, Abbild %VER%), gestartet %DATE% %TIME%

pushd "%RE%"
echo [%TIME%] 1 Renderer bauen
>>"%L%" echo [%DATE% %TIME%] Start 1 Renderer bauen
call "%ANT%" -f build.xml clean jar >>"%L%" 2>&1 || goto :abbruch
>>"%L%" echo [%DATE% %TIME%] Ende 1 Renderer bauen - Exit 0
popd
copy /y "%RE%\dist\Renderer.jar" "%SH%\lib\Renderer.jar" >>"%L%" 2>&1 || goto :abbruch
copy /y "%RE%\dist\Renderer.jar" "%HH%\lib\Renderer.jar" >>"%L%" 2>&1 || goto :abbruch

pushd "%SH%"
echo [%TIME%] 2 Steuerhaus bauen
>>"%L%" echo [%DATE% %TIME%] Start 2 Steuerhaus bauen
call "%ANT%" -f build.xml clean jar >>"%L%" 2>&1 || goto :abbruch
>>"%L%" echo [%DATE% %TIME%] Ende 2 Steuerhaus bauen - Exit 0
popd
copy /y "%SH%\dist\Steuerhaus.jar" "%HH%\lib\Steuerhaus.jar" >>"%L%" 2>&1 || goto :abbruch

pushd "%HH%"
echo [%TIME%] 3 HalligHooge bauen
>>"%L%" echo [%DATE% %TIME%] Start 3 HalligHooge bauen
call "%ANT%" -f build.xml clean jar >>"%L%" 2>&1 || goto :abbruch
>>"%L%" echo [%DATE% %TIME%] Ende 3 HalligHooge bauen - Exit 0

echo [%TIME%] 4 Abbild %VER%
>>"%L%" echo [%DATE% %TIME%] Start 4 Abbild %VER%
call "%SH%\tools\abbild.cmd" "%HH%" HalligHooge %VER% >>"%L%" 2>&1
set "E=%ERRORLEVEL%"
>>"%L%" echo [%DATE% %TIME%] Ende 4 Abbild %VER% - Exit %E%
if not "%E%"=="0" goto :abbruch
if not exist "%HH%\dist\abbild\HalligHooge\HalligHooge.exe" goto :abbruch
>>"%L%" dir "%HH%\dist\abbild\HalligHooge\HalligHooge.exe" "%HH%\dist\HalligHooge.jar" "%HH%\dist\lib\Renderer.jar" "%HH%\dist\lib\Steuerhaus.jar"
popd
>>"%L%" echo FERTIG %DATE% %TIME%
echo Fertig. Uebersicht: %L%
timeout /t 5 >nul
goto :eof
:abbruch
>>"%L%" echo ABBRUCH %DATE% %TIME%
echo Abbruch - siehe %L%
timeout /t 60 >nul
exit /b 1
