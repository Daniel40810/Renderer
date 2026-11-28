@echo off
rem Seegang Lauf L7q (Seegang Werkbuch, Datei 115): S9f - die Brandung wartet auf die erste Flaeche (SF197 A).
rem Schritte: 1 Renderer clean jar, Renderer.jar nach Steuerhaus\lib und HalligHooge\lib; 2 Steuerhaus clean jar,
rem Steuerhaus.jar nach HalligHooge\lib; 3 HalligHooge clean jar; 4 Abbild 2.8.1 (SF196 A);
rem 5 BrandungBildCheck --teil anlauf (Pruefstein 21: Xaver, Hanswarft, vor und nach der ersten Flaeche).
rem Ausgaben: HalligHooge\docs\status\Status_L7q.txt, S9q_Brandung_Anlauf.md. Erwartete Dauer: rund 6 min.
rem Die App muss geschlossen sein (sonst bricht der Lauf am Anfang ab). Netz: keins. Datenbank: nicht noetig.
rem Gestartet von Claude per Explorer.
setlocal EnableExtensions
chcp 65001 >nul
set "P=C:\Users\Dan40810\Documents\NetBeansProjects"
set "RE=%P%\Renderer"
set "SH=%P%\Steuerhaus"
set "HH=%P%\HalligHooge"
set "ST=%HH%\docs\status"
set "SZ=%HH%\src\szenen\Hooge.szenen"
set "VER=2.8.1"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
set "R=java %U% --enable-native-access=ALL-UNNAMED -Xmx16g -cp dist\Renderer.jar"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
if not defined JDK if exist "C:\Program Files\Java\jdk-21\bin\jpackage.exe" set "JDK=C:\Program Files\Java\jdk-21"
if not exist "%ST%" mkdir "%ST%"
set "L=%ST%\Status_L7q.txt"
>"%L%" echo Lauf L7q (S9f: Brandung wartet auf die erste Flaeche; Kette, Abbild %VER%, Pruefstein 21), gestartet %DATE% %TIME%
tasklist /fi "imagename eq HalligHooge.exe" 2>nul | find /i "HalligHooge.exe" >nul
if not errorlevel 1 (
  >>"%L%" echo Die App HalligHooge.exe laeuft noch - bitte schliessen und den Lauf neu starten.
  echo Die App HalligHooge.exe laeuft noch - bitte schliessen.
  goto :abbruch
)

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
>>"%L%" dir "%HH%\dist\abbild\HalligHooge\HalligHooge.exe" "%HH%\dist\HalligHooge.jar" "%HH%\dist\lib\Renderer.jar"
popd

pushd "%RE%"
echo [%TIME%] 5 BrandungBildCheck --teil anlauf
>>"%L%" echo [%DATE% %TIME%] Start 5 BrandungBildCheck --teil anlauf
%R% com.dan.lauf.demo.BrandungBildCheck --szenen "%SZ%" --teil anlauf >>"%L%" 2>&1
set "E5=%ERRORLEVEL%"
>>"%L%" echo [%DATE% %TIME%] Ende 5 BrandungBildCheck - Exit %E5%
if exist "%RE%\docs\status\S9f_Brandung_Anlauf.md" copy /y "%RE%\docs\status\S9f_Brandung_Anlauf.md" "%ST%\S9q_Brandung_Anlauf.md" >nul
popd
>>"%L%" echo FERTIG %DATE% %TIME% (Pruefung Exit %E5%)
echo Fertig. Uebersicht: %L%
timeout /t 5 >nul
goto :eof
:abbruch
>>"%L%" echo ABBRUCH %DATE% %TIME%
echo Abbruch - siehe %L%
timeout /t 60 >nul
exit /b 1
