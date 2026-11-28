@echo off
rem Seegang Lauf L2 (Seegang Werkbuch, Datei 10; SF19 A): S2 und S3b auf deiner Karte, keine Sichtpruefung.
rem 1 Renderer bauen (clean jar mit SPIR-V und Paketpruefung);
rem 2 SeegangKarteCheck: Oberflaeche auf der Karte gegen die Referenz und die Rechenzeit der Synthese (Pruefstein 1,5 ms);
rem 3 OberflaecheCheck (Referenz, 38 Pruefsteine); 4 NahfeldCheck und NahfeldKarteCheck;
rem 5 NahfeldLauf: das Nahfeld mit echten Daten, 5 Faelle (Sturmflut 25.10., Xaver, ruhiger Tag), Bindung tau = 120 s.
rem Ausgaben: HalligHooge\docs\status\Status_L2_*.txt; Atteste Renderer\docs\status\S3b_SeegangKarte.md, S3_Oberflaeche.md,
rem S2_Nahfeld.md, S2_NahfeldKarte.md, S2_Nahfeld_Daten.md; Bilder von Hs Renderer\docs\status\seegang\S2_01 ... 05.png.
rem Keine neuen Daten.
rem Erwartete Dauer: rund 20 min (Bau 3, Karte 1, Oberflaeche 2, Nahfeld-Pruefungen 4, Nahfeld mit Daten 10) [geschaetzt].
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
set "UE=%ST%\Status_L2_.txt"
set "L1=%ST%\Status_L2_1.txt"
set "L2=%ST%\Status_L2_2.txt"
set "L3=%ST%\Status_L2_3.txt"
set "L4=%ST%\Status_L2_4.txt"
set "L5=%ST%\Status_L2_5.txt"
set "E=0"
set "EF=0"
for /f %%t in ('powershell -NoProfile -Command "[DateTime]::Now.Ticks"') do set "T0=%%t"
>"%UE%" echo Lauf L2 (Seegang S2 und S3b), gestartet %DATE% %TIME%
>"%L1%" echo Status_L2_1: Renderer bauen (clean jar, SPIR-V, check-packages)
>"%L2%" echo Status_L2_2: SeegangKarteCheck (Karte gegen Referenz, Rechenzeit)
>"%L3%" echo Status_L2_3: OberflaecheCheck
>"%L4%" echo Status_L2_4: NahfeldCheck und NahfeldKarteCheck
>"%L5%" echo Status_L2_5: NahfeldLauf (5 Faelle, tau = 120 s)

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

set "N=2 SeegangKarteCheck (Karte gegen Referenz, Rechenzeit der Synthese)"
set "L=%L2%"
set "C=%R% com.dan.render.seegang.demo.SeegangKarteCheck"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=3 OberflaecheCheck (Referenz, 38 Pruefsteine)"
set "L=%L3%"
set "C=%R% com.dan.lauf.demo.OberflaecheCheck"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=4a NahfeldCheck (Referenz, 36 Pruefsteine)"
set "L=%L4%"
set "C=%R% com.dan.physics.waves.nah.demo.NahfeldCheck"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=4b NahfeldKarteCheck (Karte gegen Referenz, 14 Pruefsteine)"
set "C=%R% com.dan.physics.waves.nah.demo.NahfeldKarteCheck"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=5 NahfeldLauf (Nahfeld mit Daten, 5 Faelle, tau = 120 s)"
set "L=%L5%"
set "C=%R% com.dan.lauf.demo.NahfeldLauf --szenen %SZ% --kennung S2"
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
