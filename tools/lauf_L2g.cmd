@echo off
rem Seegang Lauf L2g (Seegang Werkbuch, Datei 16): SF23 A eingebaut, keine Sichtpruefung.
rem 1 Renderer bauen (clean jar mit SPIR-V und Paketpruefung);
rem 2 OberflaecheCheck: Referenz mit der Schwelle aus Varianz oder Neigung (38 Pruefsteine);
rem 3 SeegangKarteCheck: dasselbe auf der Karte, Rechenzeit je Bild im Lauf (ferne Stufen jedes zweite Bild), Pruefstein 2,2 ms.
rem Ausgaben: HalligHooge\docs\status\Status_L2g_*.txt; Atteste Renderer\docs\status\S3_Oberflaeche.md und S3b_SeegangKarte.md.
rem Keine neuen Daten. Erwartete Dauer: rund 3 min [geschaetzt].
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
set "UE=%ST%\Status_L2g_.txt"
set "L1=%ST%\Status_L2g_1.txt"
set "L2=%ST%\Status_L2g_2.txt"
set "L3=%ST%\Status_L2g_3.txt"
set "E=0"
set "EF=0"
for /f %%t in ('powershell -NoProfile -Command "[DateTime]::Now.Ticks"') do set "T0=%%t"
>"%UE%" echo Lauf L2g (SF23 A: Schwelle aus Varianz oder Neigung, ferne Stufen jedes zweite Bild), gestartet %DATE% %TIME%
>"%L1%" echo Status_L2g_1: Renderer bauen (clean jar, SPIR-V, check-packages)
>"%L2%" echo Status_L2g_2: OberflaecheCheck (Referenz)
>"%L3%" echo Status_L2g_3: SeegangKarteCheck (Karte, Rechenzeit je Bild im Lauf)

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

set "N=2 OberflaecheCheck (Referenz, 38 Pruefsteine)"
set "L=%L2%"
set "C=%R% com.dan.lauf.demo.OberflaecheCheck"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=3 SeegangKarteCheck (Karte, Rechenzeit je Bild im Lauf)"
set "L=%L3%"
set "C=%R% com.dan.render.seegang.demo.SeegangKarteCheck"
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
