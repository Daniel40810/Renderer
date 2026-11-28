@echo off
rem Seegang Lauf L2b (Seegang Werkbuch, Datei 18): S3c – das Netz des Wassers aus der Clipmap, die Bilder "nachher", Messung SF14.
rem 1 Renderer bauen (clean jar mit SPIR-V und Paketpruefung);
rem 2 SeegangKarteCheck (Synthese mit Rand ins Fernfeld, Pruefstein 2,2 ms); 3 SeegangNetzCheck mit Validierung (Netz N1-N3);
rem 4 OberflaecheCheck; 5 WaterCheck und 6 BrandungBildCheck (probe, bitgleich): der Weg ohne Netz wie bisher;
rem 7 SeegangBilder S3: die 13 Bilder aus S0 und zweimal "Anleger von See" mit der neuen Flaeche, Zeiten der Karte;
rem 8 QuellenMessung SF14 (physics.waves, Quellen je 60 s und je 10 s, 25.10. ab 09:00 UTC, 1 h Vorlauf, 2 h).
rem Ausgaben: HalligHooge\docs\status\Status_L2b_*.txt; Atteste in Renderer\docs\status; Bilder in Renderer\docs\status\seegang\S3_*.png.
rem Keine neuen Daten. Erwartete Dauer: rund 50 min [geschaetzt], davon die Bilder rund 30 min und die Messung rund 12 min.
rem Anhalten zwischen zwei Schritten: eine Datei STOPP.txt neben dieses Skript legen.
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0.."

set "HH=C:\Users\Dan40810\Documents\NetBeansProjects\HalligHooge"
set "ST=%HH%\docs\status"
set "SZ=%HH%\src\szenen\Hooge.szenen"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
set "R=java %U% --enable-native-access=ALL-UNNAMED -Xmx16g -cp dist\Renderer.jar"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"

if not exist "%ST%" mkdir "%ST%"
set "UE=%ST%\Status_L2b_.txt"
set "L1=%ST%\Status_L2b_1.txt"
set "L2=%ST%\Status_L2b_2.txt"
set "L3=%ST%\Status_L2b_3.txt"
set "L4=%ST%\Status_L2b_4.txt"
set "L5=%ST%\Status_L2b_5.txt"
set "L6=%ST%\Status_L2b_6.txt"
set "L7=%ST%\Status_L2b_7.txt"
set "L8=%ST%\Status_L2b_8.txt"
set "G=D:\Daten\HalligHooge\gebiet"
set "V=-Dcore.gpu.validierung=true"
set "BI=seegang/brandung/2026-10-25T12:30:00Z,seegang/steinkante_west/2026-10-25T12:30:00Z,seegang/hafen/2026-10-25T12:30:00Z,seegang/landunter_hanswarft/2026-10-25T12:30:00Z,seegang/prielmuendung/2026-10-25T14:30:00Z,xaver/brandung/2013-12-05T13:30:00Z,xaver/steinkante_west/2013-12-05T12:00:00Z,xaver/hafen/2013-12-05T13:30:00Z,xaver/landunter_hanswarft/2013-12-05T13:30:00Z,xaver/prielmuendung/2013-12-06T08:30:00Z,ruhigsee/brandung/2026-10-30T12:00:00Z,ruhigsee/hafen/2026-10-30T12:00:00Z,ruhigsee/prielmuendung/2026-10-30T08:00:00Z,seegang/anleger_see/2026-10-25T12:30:00Z,xaver/anleger_see/2013-12-05T13:30:00Z"
set "E=0"
set "EF=0"
for /f %%t in ('powershell -NoProfile -Command "[DateTime]::Now.Ticks"') do set "T0=%%t"
>"%UE%" echo Lauf L2b (S3c: Netz, Bilder nachher, Messung SF14), gestartet %DATE% %TIME%
>"%L1%" echo Status_L2b_1: Renderer bauen (clean jar, SPIR-V, check-packages)
>"%L2%" echo Status_L2b_2: SeegangKarteCheck
>"%L3%" echo Status_L2b_3: SeegangNetzCheck mit Validierung
>"%L4%" echo Status_L2b_4: OberflaecheCheck
>"%L5%" echo Status_L2b_5: WaterCheck
>"%L6%" echo Status_L2b_6: BrandungBildCheck probe, bitgleich
>"%L7%" echo Status_L2b_7: SeegangBilder S3
>"%L8%" echo Status_L2b_8: QuellenMessung SF14

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

set "N=2 SeegangKarteCheck"
set "L=%L2%"
set "C=%R% com.dan.render.seegang.demo.SeegangKarteCheck"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=3 SeegangNetzCheck mit Validierung"
set "L=%L3%"
set "C=%R% %V% com.dan.lauf.demo.SeegangNetzCheck"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=4 OberflaecheCheck"
set "L=%L4%"
set "C=%R% com.dan.lauf.demo.OberflaecheCheck"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=5 WaterCheck (Weg ohne Netz)"
set "L=%L5%"
set "C=%R% com.dan.render.water.demo.WaterCheck --gebiet %G%"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=6 BrandungBildCheck probe, bitgleich"
set "L=%L6%"
set "C=%R% com.dan.lauf.demo.BrandungBildCheck --szenen %SZ% --teil probe,bitgleich"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=7 SeegangBilder S3 (15 Bilder mit der neuen Flaeche)"
set "L=%L7%"
set "C=%R% com.dan.lauf.demo.SeegangBilder --szenen %SZ% --kennung S3 --bilder %BI%"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=8 QuellenMessung SF14"
set "L=%L8%"
set "C=%R% com.dan.lauf.demo.QuellenMessung --gebiet %G% --archiv D:\Daten\HalligHooge\_p8c\archiv"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

>>"%UE%" echo.
if not "%EF%"=="0" >>"%UE%" echo Mindestens ein Schritt mit Exit ungleich 0 - siehe die Statusdateien und Atteste
>>"%UE%" echo FERTIG %DATE% %TIME%
echo.
echo Fertig. Uebersicht: %UE%
timeout /t 20 >nul
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
