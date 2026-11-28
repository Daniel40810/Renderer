@echo off
rem Seegang Lauf L6a (Seegang Werkbuch, Datei 104): S8a Gischt am Bug und Tropfen auf der Linse, S8b Katzenpfoten mit
rem der Boee aus ERA5, Wellen fuer Treibgut und Enten, Ausweichen.
rem 1 Renderer bauen (mit SPIR-V und Paketpruefung); Renderer.jar nach Steuerhaus\lib und HalligHooge\lib; Steuerhaus und
rem   HalligHooge bauen; 2 Aufbereitung, Schritt boee fuer 202610 und 201312 nach HalligHooge\daten\gebiet\antrieb;
rem 3 SeegangS8Check Teil 1-3 (Pruefsteine 35-37, 40, 41; Bilder) - mit Validierung; 4 SchwimmerCheck Teil 4 (Kosten mit
rem   den Punkten fuer Enten und Treibgut, Pruefsteine 17-19) - mit Validierung.
rem Ausgaben: HalligHooge\docs\status\Status_L6a_*.txt; Atteste Renderer\docs\status\S8ab_Seegang.md, S7b4_Schwimmer_teil4.md;
rem Bilder Renderer\docs\status\S8*.png.
rem Erwartete Dauer: rund 30 min, hoechstens 45 min [geschaetzt]. Gestartet von Claude per Explorer.
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
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
set "R=java %U% --enable-native-access=ALL-UNNAMED -Xmx16g -cp dist\Renderer.jar"
set "V=-Dcore.gpu.validierung=true"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"

if not exist "%ST%" mkdir "%ST%"
set "UE=%ST%\Status_L6a_.txt"
set "L1=%ST%\Status_L6a_1.txt"
set "L2=%ST%\Status_L6a_2.txt"
set "L3=%ST%\Status_L6a_3.txt"
set "L4=%ST%\Status_L6a_4.txt"
set "E=0"
set "EF=0"
for /f %%t in ('powershell -NoProfile -Command "[DateTime]::Now.Ticks"') do set "T0=%%t"
>"%UE%" echo Lauf L6a (S8a, S8b), gestartet %DATE% %TIME%
>"%L1%" echo Status_L6a_1: Renderer, Steuerhaus, HalligHooge bauen
>"%L2%" echo Status_L6a_2: Aufbereitung boee (202610, 201312)
>"%L3%" echo Status_L6a_3: SeegangS8Check mit Validierung
>"%L4%" echo Status_L6a_4: SchwimmerCheck Teil 4 mit Validierung

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

set "N=2a Aufbereitung boee 202610"
set "L=%L2%"
set "C=java %U% -Xmx4g -cp dist\Renderer.jar com.dan.aufbereitung.Aufbereitung ..\HalligHooge\gebiet\Hooge.rezept --schritte boee --ziel %HH%\daten\gebiet"
call :lauf
if not "%E%"=="0" goto :abbruch
set "N=2b Aufbereitung boee 201312 (Xaver)"
set "C=java %U% -Xmx4g -cp dist\Renderer.jar com.dan.aufbereitung.Aufbereitung ..\HalligHooge\gebiet\Hooge.rezept --schritte boee --antrieb.zeitraum 201312 --ziel %HH%\daten\gebiet"
call :lauf
if not "%E%"=="0" goto :abbruch
dir "%HH%\daten\gebiet\antrieb\boee.*" "%HH%\daten\gebiet\antrieb\201312\boee.*" >>"%L2%" 2>&1

set "N=3 SeegangS8Check Teil 1-3 (mit Validierung)"
set "L=%L3%"
set "C=%R% %V% com.dan.lauf.demo.SeegangS8Check --szenen %SZ%"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=4 SchwimmerCheck Teil 4 (Kosten mit Enten und Treibgut; mit Validierung)"
set "L=%L4%"
set "C=%R% %V% com.dan.lauf.demo.SchwimmerCheck --szenen %SZ% --teil 4"
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
