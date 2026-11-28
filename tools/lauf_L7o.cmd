@echo off
rem Seegang Lauf L7o (Seegang Werkbuch, Datei 112): Schluss der Phase Leistung - Steuerhaus und HalligHooge gegen den
rem Renderer aus L7n neu bauen, damit die App den Stand von S9 hat (Spiegelung halb, Augenhoehe fuer den Himmel).
rem Kein Abbild (HalligHooge.exe bleibt, wie es ist). Ausgabe: HalligHooge\docs\status\Status_L7o.txt.
rem Erwartete Dauer: rund 2 min. Vor dem Bau muss die App geschlossen sein.
rem Gestartet von Claude per Explorer.
setlocal EnableExtensions
chcp 65001 >nul
set "P=C:\Users\Dan40810\Documents\NetBeansProjects"
set "SH=%P%\Steuerhaus"
set "HH=%P%\HalligHooge"
set "ST=%HH%\docs\status"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
if not exist "%ST%" mkdir "%ST%"
set "L=%ST%\Status_L7o.txt"
>"%L%" echo Lauf L7o (Steuerhaus und HalligHooge bauen), gestartet %DATE% %TIME%
pushd "%SH%"
echo [%TIME%] 1 Steuerhaus bauen
>>"%L%" echo [%DATE% %TIME%] Start 1 Steuerhaus bauen
call "%ANT%" -f build.xml clean jar >>"%L%" 2>&1 || goto :abbruch
>>"%L%" echo [%DATE% %TIME%] Ende 1 Steuerhaus bauen - Exit 0
popd
pushd "%HH%"
echo [%TIME%] 2 HalligHooge bauen
>>"%L%" echo [%DATE% %TIME%] Start 2 HalligHooge bauen
call "%ANT%" -f build.xml clean jar >>"%L%" 2>&1 || goto :abbruch
>>"%L%" echo [%DATE% %TIME%] Ende 2 HalligHooge bauen - Exit 0
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
