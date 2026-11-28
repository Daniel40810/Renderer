@echo off
rem Seegang Lauf L2h (Seegang Werkbuch, Datei 20): S3c-5 - Nacharbeit an S3c nach dem Befund von L2b (Datei 19) und SF28-SF30 A.
rem (Der Name L2c ist seit Datei 12 vergeben; darum L2h.)
rem 1 Renderer bauen (clean jar mit SPIR-V und Paketpruefung);
rem 2 SeegangNetzCheck mit Validierung (N1-N3, neu N3b Teich hinter einem Deich, N6b Abbruch bitgleich);
rem 3 WaterCheck und 4 BrandungBildCheck (probe, bitgleich): der Weg ohne Netz wie bisher;
rem 5 Nachlauf des Seegangs Sturmflut 25.10. und 6 ruhiger Tag 30.10. mit Quellen je 10 s (SF30 A; ersetzt seegang_G2w/G3w in
rem   daten\_p8c\archiv und daten\_p8c\archiv_ruhig); Xaver folgt in einem eigenen Lauf;
rem 7 SeegangBilder S3c5: die 15 Bilder aus L2b mit Auge ueber Wasser (SF29 A), ohne die Brecher aus P13c im Netz (SF28 A),
rem   N6 je Bildteil, N8 an den Naehten, Diagnose zu Bild 05 (Karte von Hs, doppelter Anlauf).
rem Ausgaben: HalligHooge\docs\status\Status_L2h_*.txt; Atteste in Renderer\docs\status; Bilder in Renderer\docs\status\seegang\S3c5_*.png.
rem Erwartete Dauer: rund 65 min [geschaetzt], davon die Nachlaeufe rund 53 min und die Bilder rund 8 min.
rem Anhalten zwischen zwei Schritten: eine Datei STOPP.txt neben dieses Skript legen.
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0.."

set "HH=C:\Users\Dan40810\Documents\NetBeansProjects\HalligHooge"
set "ST=%HH%\docs\status"
set "SZ=%HH%\src\szenen\Hooge.szenen"
set "D=%HH%\daten"
set "G=%D%\gebiet"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
set "R=java %U% --enable-native-access=ALL-UNNAMED -Xmx16g -cp dist\Renderer.jar"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"

if not exist "%ST%" mkdir "%ST%"
set "UE=%ST%\Status_L2h_.txt"
set "L1=%ST%\Status_L2h_1.txt"
set "L2=%ST%\Status_L2h_2.txt"
set "L3=%ST%\Status_L2h_3.txt"
set "L4=%ST%\Status_L2h_4.txt"
set "L5=%ST%\Status_L2h_5.txt"
set "L6=%ST%\Status_L2h_6.txt"
set "L7=%ST%\Status_L2h_7.txt"
set "V=-Dcore.gpu.validierung=true"
set "BI=seegang/brandung/2026-10-25T12:30:00Z,seegang/steinkante_west/2026-10-25T12:30:00Z,seegang/hafen/2026-10-25T12:30:00Z,seegang/landunter_hanswarft/2026-10-25T12:30:00Z,seegang/prielmuendung/2026-10-25T14:30:00Z,xaver/brandung/2013-12-05T13:30:00Z,xaver/steinkante_west/2013-12-05T12:00:00Z,xaver/hafen/2013-12-05T13:30:00Z,xaver/landunter_hanswarft/2013-12-05T13:30:00Z,xaver/prielmuendung/2013-12-06T08:30:00Z,ruhigsee/brandung/2026-10-30T12:00:00Z,ruhigsee/hafen/2026-10-30T12:00:00Z,ruhigsee/prielmuendung/2026-10-30T08:00:00Z,seegang/anleger_see/2026-10-25T12:30:00Z,xaver/anleger_see/2013-12-05T13:30:00Z"
set "E=0"
set "EF=0"
for /f %%t in ('powershell -NoProfile -Command "[DateTime]::Now.Ticks"') do set "T0=%%t"
>"%UE%" echo Lauf L2h (S3c-5: Nacharbeit, Nachlauf mit Quellen je 10 s, Bilder), gestartet %DATE% %TIME%
>"%L1%" echo Status_L2h_1: Renderer bauen (clean jar, SPIR-V, check-packages)
>"%L2%" echo Status_L2h_2: SeegangNetzCheck mit Validierung
>"%L3%" echo Status_L2h_3: WaterCheck
>"%L4%" echo Status_L2h_4: BrandungBildCheck probe, bitgleich
>"%L5%" echo Status_L2h_5: Nachlauf des Seegangs, Sturmflut 25.10., Quellen je 10 s
>"%L6%" echo Status_L2h_6: Nachlauf des Seegangs, ruhiger Tag 30.10., Quellen je 10 s
>"%L7%" echo Status_L2h_7: SeegangBilder S3c5

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

set "N=2 SeegangNetzCheck mit Validierung"
set "L=%L2%"
set "C=%R% %V% com.dan.lauf.demo.SeegangNetzCheck"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=3 WaterCheck (Weg ohne Netz)"
set "L=%L3%"
set "C=%R% com.dan.render.water.demo.WaterCheck --gebiet %G%"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=4 BrandungBildCheck probe, bitgleich"
set "L=%L4%"
set "C=%R% com.dan.lauf.demo.BrandungBildCheck --szenen %SZ% --teil probe,bitgleich"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=5 Nachlauf des Seegangs, Sturmflut 25.10. (Quellen je 10 s)"
set "L=%L5%"
set "C=%R% com.dan.lauf.demo.SeegangNachlaufLauf --gebiet %G% --archive %D%\_p8c\archiv --attest S3c5_Nachlauf_Sturm"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=6 Nachlauf des Seegangs, ruhiger Tag 30.10. (Quellen je 10 s)"
set "L=%L6%"
set "C=%R% com.dan.lauf.demo.SeegangNachlaufLauf --gebiet %G% --archive %D%\_p8c\archiv_ruhig --attest S3c5_Nachlauf_Ruhig"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=7 SeegangBilder S3c5 (15 Bilder, N6, N8, Diagnose 05)"
set "L=%L7%"
set "C=%R% com.dan.lauf.demo.SeegangBilder --szenen %SZ% --kennung S3c5 --bilder %BI% --diagnose 5"
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
