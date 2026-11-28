@echo off
rem Seegang Lauf L2e (Seegang Werkbuch, Datei 13): Nahfeld mit einem Faden je Stuetzstelle, keine Sichtpruefung.
rem 1 Renderer bauen (clean jar mit SPIR-V und Paketpruefung);
rem 2 SeegangKarteCheck: Rechenzeit, 14 Messungen, dazu drei Schwellen (zur Frage SF23);
rem 3 NahfeldKarteCheck: Ausbreitung, Tempo und Beugung mit einem Faden je Stuetzstelle (B39), Zeit je Kernel;
rem 4 NahfeldLauf: 5 Faelle mit der neuen Karte, S2.1 fein ohne den Rand (SF24 A).
rem Ausgaben: HalligHooge\docs\status\Status_L2e_*.txt; Atteste Renderer\docs\status\S3b_SeegangKarte.md, S2_NahfeldKarte.md,
rem S2e_Nahfeld_Daten.md; Bilder von Hs Renderer\docs\status\seegang\S2e_01 ... 05.png. Keine neuen Daten.
rem Erwartete Dauer: rund 8 min (Bau 1, Karte 1, Nahfeld auf der Karte 1, Nahfeld mit Daten 5) [geschaetzt].
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
set "UE=%ST%\Status_L2e_.txt"
set "L1=%ST%\Status_L2e_1.txt"
set "L2=%ST%\Status_L2e_2.txt"
set "L3=%ST%\Status_L2e_3.txt"
set "L4=%ST%\Status_L2e_4.txt"
set "E=0"
set "EF=0"
for /f %%t in ('powershell -NoProfile -Command "[DateTime]::Now.Ticks"') do set "T0=%%t"
>"%UE%" echo Lauf L2e (Nahfeld je Stuetzstelle), gestartet %DATE% %TIME%
>"%L1%" echo Status_L2e_1: Renderer bauen (clean jar, SPIR-V, check-packages)
>"%L2%" echo Status_L2e_2: SeegangKarteCheck (Rechenzeit, Messungen, Schwellen)
>"%L3%" echo Status_L2e_3: NahfeldKarteCheck (ein Faden je Stuetzstelle, Zeit je Kernel)
>"%L4%" echo Status_L2e_4: NahfeldLauf (5 Faelle)

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

set "N=2 SeegangKarteCheck (Rechenzeit, Messungen, Schwellen)"
set "L=%L2%"
set "C=%R% com.dan.render.seegang.demo.SeegangKarteCheck"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=3 NahfeldKarteCheck (ein Faden je Stuetzstelle, Zeit je Kernel)"
set "L=%L3%"
set "C=%R% com.dan.physics.waves.nah.demo.NahfeldKarteCheck"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=4 NahfeldLauf (5 Faelle, S2.1 fein ohne Rand)"
set "L=%L4%"
set "C=%R% com.dan.lauf.demo.NahfeldLauf --szenen %SZ% --kennung S2e"
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
