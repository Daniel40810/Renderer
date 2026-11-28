@echo off
rem Seegang Lauf L5e2 (Seegang Werkbuch, Datei 101): S7e2 - Adler-Express mit Gleiten nach Savitsky (SF150 A), 12 Stueck
rem Treibgut und zwei Trupps Eiderenten (SF151 A); Lotse je Schiff, Fahrplan in Ortszeit, WEG; Adler GM 3,5 m.
rem 1 Renderer bauen (mit Paketpruefung); Renderer.jar nach Steuerhaus\lib und HalligHooge\lib; Steuerhaus und
rem   HalligHooge bauen; 2 GleitCheck (Pruefstein 34); 3 TreibCheck (Pruefstein 32); 4 SeezeichenCheck (30, 31, 33; neu
rem   Bilder Adler-Express bei Tag und Nacht, Enten, Treibgut); 5 SchwimmerCheck Teil 4 (in der Anwendung mit allen neuen
rem   Koerpern, Kosten); 6 KielwasserCheck Teil 5 (Adler-Express kommt an) - die Anwendung mit Validierung.
rem Ausgaben: HalligHooge\docs\status\Status_L5e2_*.txt; Atteste Renderer\docs\status\S7e_Gleiten.md, S7e_Treibgut.md,
rem S7e_Seezeichen.md, S7b4_Schwimmer_teil4.md, S7e_Adler.md; Bilder Renderer\docs\status\S7e_*.png, S7b4_*.png.
rem Erwartete Dauer: rund 40 min, hoechstens 55 min [geschaetzt]. Gestartet von Claude per Explorer.
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
set "UE=%ST%\Status_L5e2_.txt"
set "L1=%ST%\Status_L5e2_1.txt"
set "L2=%ST%\Status_L5e2_2.txt"
set "L3=%ST%\Status_L5e2_3.txt"
set "L4=%ST%\Status_L5e2_4.txt"
set "L5=%ST%\Status_L5e2_5.txt"
set "L6=%ST%\Status_L5e2_6.txt"
set "E=0"
set "EF=0"
for /f %%t in ('powershell -NoProfile -Command "[DateTime]::Now.Ticks"') do set "T0=%%t"
>"%UE%" echo Lauf L5e2 (S7e2), gestartet %DATE% %TIME%
>"%L1%" echo Status_L5e2_1: Renderer, Steuerhaus, HalligHooge bauen
>"%L2%" echo Status_L5e2_2: GleitCheck
>"%L3%" echo Status_L5e2_3: TreibCheck
>"%L4%" echo Status_L5e2_4: SeezeichenCheck mit Validierung
>"%L5%" echo Status_L5e2_5: SchwimmerCheck Teil 4 mit Validierung
>"%L6%" echo Status_L5e2_6: KielwasserCheck Teil 5 mit Validierung

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

set "N=2 GleitCheck (Pruefstein 34 am Gleitprisma, Adler-Express bei 24 kn, langsam bitgleich, hart Ruder)"
set "L=%L2%"
set "C=%R% com.dan.physics.auftrieb.demo.GleitCheck"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=3 TreibCheck (Pruefstein 32: Drift von Balken, Fischkiste, Stamm in Strom und Wind)"
set "L=%L3%"
set "C=%R% com.dan.physics.auftrieb.demo.TreibCheck"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=4 SeezeichenCheck (Lagen, Kennungen, Nacht; neu Adler-Express bei Tag und Nacht, Enten, Treibgut; mit Validierung)"
set "L=%L4%"
set "C=%R% %V% com.dan.lauf.demo.SeezeichenCheck --szenen %SZ% --osm %OSM%"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=5 SchwimmerCheck Teil 4 (in der Anwendung mit Adler-Express, Treibgut, Enten; Kosten; mit Validierung)"
set "L=%L5%"
set "C=%R% %V% com.dan.lauf.demo.SchwimmerCheck --szenen %SZ% --teil 4"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=6 KielwasserCheck Teil 5 (Adler-Express kommt an: puenktlich, 4 Leinen; mit Validierung)"
set "L=%L6%"
set "C=%R% %V% com.dan.lauf.demo.KielwasserCheck --szenen %SZ% --teil 5"
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
