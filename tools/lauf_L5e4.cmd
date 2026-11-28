@echo off
rem Seegang Lauf L5e4 (Seegang Werkbuch, Datei 102): Abnahme S7, Teil 2 - nach L5e3 (Befunde: Auge an Bord der Faehre im
rem Fahrgastraum des Netzes nach Foto; Ansicht faehre gegen die Sonne): Auge vor dem Fahrgastraum, Ansicht faehre von Westen.
rem 1 Renderer bauen (mit Paketpruefung); Renderer.jar nach Steuerhaus\lib und HalligHooge\lib; Steuerhaus und
rem   HalligHooge bauen; 2 KielwasserCheck Teil 1-4 (S7d; Faehre kommt an, Verholen, faehrt ab - prueft auch den
rem   berichtigten Lotsen an der Faehre); 3 SeezeichenCheck (30, 31, 33; Bilder); 4 BordkameraCheck (S7c) - mit Validierung.
rem Ausgaben: HalligHooge\docs\status\Status_L5e4_*.txt; Atteste Renderer\docs\status\S7d_Kielwasser.md, S7e_Seezeichen.md,
rem S7c_Bordkamera.md; Bilder und Video Renderer\docs\status\.
rem Erwartete Dauer: rund 53 min, hoechstens 65 min [geschaetzt]. Gestartet von Claude per Explorer.
rem Anhalten zwischen zwei Schritten: eine Datei STOPP.txt neben dieses Skript legen.
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0.."

set "P=C:\Users\Dan40810\Documents\NetBeansProjects"
set "HH=%P%\HalligHooge"
set "SH=%P%\Steuerhaus"
set "RE=%P%\Renderer"
set "ST=%HH%\docs\status"
set "SZ=%HH%\src\szenen\Hooge.szenen"
set "OSM=%HH%\download\_status\osm_seezeichen.csv"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
set "R=java %U% --enable-native-access=ALL-UNNAMED -Xmx16g -cp dist\Renderer.jar"
set "V=-Dcore.gpu.validierung=true"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"

if not exist "%ST%" mkdir "%ST%"
set "UE=%ST%\Status_L5e4_.txt"
set "L1=%ST%\Status_L5e4_1.txt"
set "L2=%ST%\Status_L5e4_2.txt"
set "L3=%ST%\Status_L5e4_3.txt"
set "L4=%ST%\Status_L5e4_4.txt"
set "E=0"
set "EF=0"
for /f %%t in ('powershell -NoProfile -Command "[DateTime]::Now.Ticks"') do set "T0=%%t"
>"%UE%" echo Lauf L5e4 (Abnahme S7), gestartet %DATE% %TIME%
>"%L1%" echo Status_L5e4_1: Renderer, Steuerhaus, HalligHooge bauen
>"%L2%" echo Status_L5e4_2: KielwasserCheck Teil 1-4 mit Validierung
>"%L3%" echo Status_L5e4_3: SeezeichenCheck mit Validierung
>"%L4%" echo Status_L5e4_4: BordkameraCheck mit Validierung

set "N=1 Renderer bauen (clean jar, SPIR-V, check-packages)"
set "L=%L1%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" goto :abbruch
powershell -NoProfile -Command "if ((Get-Item 'dist\Renderer.jar').LastWriteTime.Ticks -gt %T0%) { exit 0 } else { exit 1 }"
if errorlevel 1 (
  >>"%UE%" echo dist\Renderer.jar ist nicht neuer als der Start des Laufs
  set "N=1 Renderer bauen (jar nicht neu)"
  goto :abbruch
)
>>"%UE%" echo dist\Renderer.jar ist neu
copy /y "%RE%\dist\Renderer.jar" "%SH%\lib\Renderer.jar" >>"%L1%" 2>&1
copy /y "%RE%\dist\Renderer.jar" "%HH%\lib\Renderer.jar" >>"%L1%" 2>&1
pushd "%SH%"
set "N=1b Steuerhaus bauen (clean jar)"
set "L=%L1%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
popd
if not "%E%"=="0" goto :abbruch
pushd "%HH%"
set "N=1c HalligHooge bauen (clean jar)"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
popd
if not "%E%"=="0" goto :abbruch

set "N=2 KielwasserCheck Teil 1-4 (S7d; mit Validierung)"
set "L=%L2%"
set "C=%R% %V% com.dan.lauf.demo.KielwasserCheck --szenen %SZ%"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=3 SeezeichenCheck (30, 31, 33; Bilder mit der Ansicht faehre von Westen; mit Validierung)"
set "L=%L3%"
set "C=%R% %V% com.dan.lauf.demo.SeezeichenCheck --szenen %SZ% --osm %OSM%"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=4 BordkameraCheck (S7c; Auge der Faehre neu; mit Validierung)"
set "L=%L4%"
set "C=%R% %V% com.dan.lauf.demo.BordkameraCheck --szenen %SZ%"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

>>"%UE%" echo.
if not "%EF%"=="0" >>"%UE%" echo Mindestens ein Schritt mit Exit ungleich 0 - siehe die Statusdateien und Atteste
>>"%UE%" echo FERTIG %DATE% %TIME%
echo.
echo Fertig. Uebersicht: %UE%
timeout /t 5 >nul
goto :eof

:abbruch
>>"%UE%" echo.
>>"%UE%" echo ABBRUCH %DATE% %TIME% nach: %N%
echo.
echo Abbruch nach: %N%  - siehe %L%
timeout /t 60 >nul
exit /b 1

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
