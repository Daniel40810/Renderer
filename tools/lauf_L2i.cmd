@echo off
rem Seegang Lauf L2i (Seegang Werkbuch, Datei 22): S3c-6 - B51 (Wasser am Auge aus dem Spiegelbild), SF31 A (Rand von G3w
rem ueber 500 m in G2w gemischt), N6 messen (Zaehler je Bildpunkt, Vorlauf der Tiefe als Schalter, Zeiten kalt) und B53.
rem 1 Renderer bauen (clean jar mit SPIR-V und Paketpruefung);
rem 2 SeegangBilder S3c6 ZUERST (kalt): die 15 Bilder aus L2h und Bild 01 noch einmal am Ende (kalt gegen warm); N6 mit
rem   Vorlauf der Tiefe, altem Weg ohne Netz und Zaehlern; N8 an den Naehten; Diagnose zu Bild 05 (Karte von Hs);
rem 3 SeegangNetzCheck mit Validierung (neu N6c Vorlauf und Zaehler bitgleich, N9 Uebergang am Rand eines feineren Netzes);
rem 4 WaterCheck mit dem Gebiet auf D: (B53; dort liegt das Fenster _p6) und 5 BrandungBildCheck (probe, bitgleich).
rem Ausgaben: HalligHooge\docs\status\Status_L2i_*.txt; Atteste in Renderer\docs\status; Bilder in Renderer\docs\status\seegang\S3c6_*.png.
rem Erwartete Dauer: rund 15 min [geschaetzt], davon die Bilder rund 8 min.
rem Erst starten, wenn der Nachlauf Xaver (lauf_S3c5_Xaver.cmd) fertig ist: sonst ist dist\Renderer.jar gesperrt.
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
set "UE=%ST%\Status_L2i_.txt"
set "L1=%ST%\Status_L2i_1.txt"
set "L2=%ST%\Status_L2i_2.txt"
set "L3=%ST%\Status_L2i_3.txt"
set "L4=%ST%\Status_L2i_4.txt"
set "L5=%ST%\Status_L2i_5.txt"
set "V=-Dcore.gpu.validierung=true"
set "GD=D:\Daten\HalligHooge\gebiet"
set "BI=seegang/brandung/2026-10-25T12:30:00Z,seegang/steinkante_west/2026-10-25T12:30:00Z,seegang/hafen/2026-10-25T12:30:00Z,seegang/landunter_hanswarft/2026-10-25T12:30:00Z,seegang/prielmuendung/2026-10-25T14:30:00Z,xaver/brandung/2013-12-05T13:30:00Z,xaver/steinkante_west/2013-12-05T12:00:00Z,xaver/hafen/2013-12-05T13:30:00Z,xaver/landunter_hanswarft/2013-12-05T13:30:00Z,xaver/prielmuendung/2013-12-06T08:30:00Z,ruhigsee/brandung/2026-10-30T12:00:00Z,ruhigsee/hafen/2026-10-30T12:00:00Z,ruhigsee/prielmuendung/2026-10-30T08:00:00Z,seegang/anleger_see/2026-10-25T12:30:00Z,xaver/anleger_see/2013-12-05T13:30:00Z,seegang/brandung/2026-10-25T12:30:00Z"
set "E=0"
set "EF=0"
for /f %%t in ('powershell -NoProfile -Command "[DateTime]::Now.Ticks"') do set "T0=%%t"
>"%UE%" echo Lauf L2i (S3c-6: B51, SF31 A, N6 messen, B53), gestartet %DATE% %TIME%
>"%L1%" echo Status_L2i_1: Renderer bauen (clean jar, SPIR-V, check-packages)
>"%L2%" echo Status_L2i_2: SeegangBilder S3c6 (kalt zuerst)
>"%L3%" echo Status_L2i_3: SeegangNetzCheck mit Validierung
>"%L4%" echo Status_L2i_4: WaterCheck mit dem Gebiet auf D:
>"%L5%" echo Status_L2i_5: BrandungBildCheck probe, bitgleich

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

set "N=2 SeegangBilder S3c6 (16 Bilder, N6 kalt, N8, Diagnose 05)"
set "L=%L2%"
set "C=%R% com.dan.lauf.demo.SeegangBilder --szenen %SZ% --kennung S3c6 --bilder %BI% --diagnose 5"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=3 SeegangNetzCheck mit Validierung"
set "L=%L3%"
set "C=%R% %V% com.dan.lauf.demo.SeegangNetzCheck"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=4 WaterCheck (Weg ohne Netz, Gebiet auf D:)"
set "L=%L4%"
set "C=%R% com.dan.render.water.demo.WaterCheck --gebiet %GD%"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=5 BrandungBildCheck probe, bitgleich"
set "L=%L5%"
set "C=%R% com.dan.lauf.demo.BrandungBildCheck --szenen %SZ% --teil probe,bitgleich"
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
