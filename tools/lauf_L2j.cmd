@echo off
rem Seegang Lauf L2j (Seegang Werkbuch, Datei 24): S3c-7 - SF33 A (Uebergang am Rand von G3w nach aussen) und SF32 A
rem (N6 als Mehrkosten des Wassers mit Netz gegen den Weg ohne Netz; Zaehler B47 und alter Weg im Netz).
rem 1 Renderer bauen (clean jar mit SPIR-V und Paketpruefung);
rem 2 SeegangBilder S3c7 zuerst (kalt): die 16 Bilder wie in L2i (Bild 01 am Ende noch einmal), Uebergang aussen;
rem   N6 neu mit Zaehlern; N8; Diagnose zu Bild 05 (Karte von Hs);
rem 3 und 4 Bild 05 noch zweimal zum Vergleich: Uebergang innen (wie L2i) und ohne Uebergang (wie L2h);
rem 5 SeegangNetzCheck mit Validierung (N9 je Art des Uebergangs, N6c mit den neuen Zaehlern).
rem Ausgaben: HalligHooge\docs\status\Status_L2j_*.txt; Atteste in Renderer\docs\status; Bilder in Renderer\docs\status\seegang:
rem S3c7_*.png, S3c7_innen_01.png und S3c7_aus_01.png (je Bild 05).
rem Erwartete Dauer: rund 12 min [geschaetzt].
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
set "UE=%ST%\Status_L2j_.txt"
set "L1=%ST%\Status_L2j_1.txt"
set "L2=%ST%\Status_L2j_2.txt"
set "L3=%ST%\Status_L2j_3.txt"
set "L4=%ST%\Status_L2j_4.txt"
set "L5=%ST%\Status_L2j_5.txt"
set "V=-Dcore.gpu.validierung=true"
set "B5=seegang/prielmuendung/2026-10-25T14:30:00Z"
set "BI=seegang/brandung/2026-10-25T12:30:00Z,seegang/steinkante_west/2026-10-25T12:30:00Z,seegang/hafen/2026-10-25T12:30:00Z,seegang/landunter_hanswarft/2026-10-25T12:30:00Z,seegang/prielmuendung/2026-10-25T14:30:00Z,xaver/brandung/2013-12-05T13:30:00Z,xaver/steinkante_west/2013-12-05T12:00:00Z,xaver/hafen/2013-12-05T13:30:00Z,xaver/landunter_hanswarft/2013-12-05T13:30:00Z,xaver/prielmuendung/2013-12-06T08:30:00Z,ruhigsee/brandung/2026-10-30T12:00:00Z,ruhigsee/hafen/2026-10-30T12:00:00Z,ruhigsee/prielmuendung/2026-10-30T08:00:00Z,seegang/anleger_see/2026-10-25T12:30:00Z,xaver/anleger_see/2013-12-05T13:30:00Z,seegang/brandung/2026-10-25T12:30:00Z"
set "E=0"
set "EF=0"
for /f %%t in ('powershell -NoProfile -Command "[DateTime]::Now.Ticks"') do set "T0=%%t"
>"%UE%" echo Lauf L2j (S3c-7: SF33 A, SF32 A), gestartet %DATE% %TIME%
>"%L1%" echo Status_L2j_1: Renderer bauen (clean jar, SPIR-V, check-packages)
>"%L2%" echo Status_L2j_2: SeegangBilder S3c7 (kalt zuerst, Uebergang aussen)
>"%L3%" echo Status_L2j_3: SeegangBilder Bild 05, Uebergang innen
>"%L4%" echo Status_L2j_4: SeegangBilder Bild 05, ohne Uebergang
>"%L5%" echo Status_L2j_5: SeegangNetzCheck mit Validierung

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

set "N=2 SeegangBilder S3c7 (16 Bilder, Uebergang aussen, N6 neu, N8, Diagnose 05)"
set "L=%L2%"
set "C=%R% com.dan.lauf.demo.SeegangBilder --szenen %SZ% --kennung S3c7 --bilder %BI% --diagnose 5"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=3 SeegangBilder Bild 05, Uebergang innen (wie L2i)"
set "L=%L3%"
set "C=%R% -Dseegang.rand=innen com.dan.lauf.demo.SeegangBilder --szenen %SZ% --kennung S3c7_innen --bilder %B5%"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=4 SeegangBilder Bild 05, ohne Uebergang (wie L2h)"
set "L=%L4%"
set "C=%R% -Dseegang.rand=aus com.dan.lauf.demo.SeegangBilder --szenen %SZ% --kennung S3c7_aus --bilder %B5%"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=5 SeegangNetzCheck mit Validierung"
set "L=%L5%"
set "C=%R% %V% com.dan.lauf.demo.SeegangNetzCheck"
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
