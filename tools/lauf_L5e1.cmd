@echo off
rem Seegang Lauf L5e1 (Seegang Werkbuch, Datei 99): S7e1 - Seezeichen und Lichter (SF146-SF151 A): 26 Pricken aus OSM
rem (Birkenstamm mit Besen), Lichter nach KVR mit Sektoren, Tonnenfeuer nach Kennung, Decksbeleuchtung, Lichtpunkte mit
rem Hof und Spiegelbild (Bildteil Lichter); die Faehre nach Foto (Netz); ANLEGEN_S 95 s.
rem 1 Renderer bauen (mit Paketpruefung); Renderer.jar nach Steuerhaus\lib und HalligHooge\lib; Steuerhaus und
rem   HalligHooge bauen; 2 SeezeichenCheck (Pruefsteine 30, 31, 33; Bilder bei Nacht); 3 SchwimmerCheck Teil 4 (in der
rem   Anwendung, mit den Pricken); 4 KielwasserCheck Teil 4 (Ankunft puenktlich mit 95 s, Abfahrt) - alle mit Validierung.
rem Ausgaben: HalligHooge\docs\status\Status_L5e1_*.txt; Atteste Renderer\docs\status\S7e_Seezeichen.md,
rem S7b4_Schwimmer_teil4.md, S7d_Kielwasser.md; Bilder Renderer\docs\status\S7e_*.png, S7b4_*.png, S7d_*.png.
rem Erwartete Dauer: rund 45 min, hoechstens 60 min [geschaetzt]. Gestartet von Claude per Explorer.
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
set "UE=%ST%\Status_L5e1_.txt"
set "L1=%ST%\Status_L5e1_1.txt"
set "L2=%ST%\Status_L5e1_2.txt"
set "L3=%ST%\Status_L5e1_3.txt"
set "L4=%ST%\Status_L5e1_4.txt"
set "E=0"
set "EF=0"
for /f %%t in ('powershell -NoProfile -Command "[DateTime]::Now.Ticks"') do set "T0=%%t"
>"%UE%" echo Lauf L5e1 (S7e1), gestartet %DATE% %TIME%
>"%L1%" echo Status_L5e1_1: Renderer, Steuerhaus, HalligHooge bauen
>"%L2%" echo Status_L5e1_2: SeezeichenCheck mit Validierung
>"%L3%" echo Status_L5e1_3: SchwimmerCheck Teil 4 mit Validierung
>"%L4%" echo Status_L5e1_4: KielwasserCheck Teil 4 mit Validierung

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

set "N=2 SeezeichenCheck (Lagen gegen OSM, Kennungen, Feuer von SA 13 bei Nacht an und aus, Bilder; mit Validierung)"
set "L=%L2%"
set "C=%R% %V% com.dan.lauf.demo.SeezeichenCheck --szenen %SZ% --osm %OSM%"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=3 SchwimmerCheck Teil 4 (in der Anwendung, mit Pricken und Lichtern; mit Validierung)"
set "L=%L3%"
set "C=%R% %V% com.dan.lauf.demo.SchwimmerCheck --szenen %SZ% --teil 4"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=4 KielwasserCheck Teil 4 (Ankunft: puenktlich mit ANLEGEN_S 95 s, Verholen, 4 Leinen; Abfahrt; mit Validierung)"
set "L=%L4%"
set "C=%R% %V% com.dan.lauf.demo.KielwasserCheck --szenen %SZ% --teil 4"
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
