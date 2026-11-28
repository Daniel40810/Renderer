@echo off
rem Seegang Lauf L2c (Seegang Werkbuch, Datei 11): Nachpruefung nach L2, keine Sichtpruefung.
rem 1 Renderer bauen (clean jar mit SPIR-V und Paketpruefung);
rem 2 SeegangKarteCheck: tanh mit Klemme (B37, tiefes ruhiges Wasser), Rechenzeit in einer Abgabe und 9 Messungen (B38);
rem 3 NahfeldKarteCheck: der blockierte Fall ohne NaN (B37);
rem 4 NahfeldLauf nur Xaver, Brandung vor dem Japsand (in L2 NaN im groben Nahfeld, B37).
rem Ausgaben: HalligHooge\docs\status\Status_L2c_*.txt; Atteste Renderer\docs\status\S3b_SeegangKarte.md, S2_NahfeldKarte.md,
rem S2c_Nahfeld_Daten.md; Bild von Hs Renderer\docs\status\seegang\S2c_01.png. Keine neuen Daten.
rem Erwartete Dauer: rund 5 min (Bau 1, Karte 1, Nahfeld auf der Karte 1, Xaver 2) [geschaetzt].
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
set "UE=%ST%\Status_L2c_.txt"
set "L1=%ST%\Status_L2c_1.txt"
set "L2=%ST%\Status_L2c_2.txt"
set "L3=%ST%\Status_L2c_3.txt"
set "L4=%ST%\Status_L2c_4.txt"
set "E=0"
set "EF=0"
for /f %%t in ('powershell -NoProfile -Command "[DateTime]::Now.Ticks"') do set "T0=%%t"
>"%UE%" echo Lauf L2c (Nachpruefung nach L2), gestartet %DATE% %TIME%
>"%L1%" echo Status_L2c_1: Renderer bauen (clean jar, SPIR-V, check-packages)
>"%L2%" echo Status_L2c_2: SeegangKarteCheck (tanh mit Klemme, Rechenzeit in einer Abgabe, Messungen)
>"%L3%" echo Status_L2c_3: NahfeldKarteCheck (blockierter Fall)
>"%L4%" echo Status_L2c_4: NahfeldLauf Xaver, Brandung

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

set "N=2 SeegangKarteCheck (tanh mit Klemme, Rechenzeit in einer Abgabe, Messungen)"
set "L=%L2%"
set "C=%R% com.dan.render.seegang.demo.SeegangKarteCheck"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=3 NahfeldKarteCheck (blockierter Fall ohne NaN)"
set "L=%L3%"
set "C=%R% com.dan.physics.waves.nah.demo.NahfeldKarteCheck"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=4 NahfeldLauf (Xaver, Brandung vor dem Japsand)"
set "L=%L4%"
set "C=%R% com.dan.lauf.demo.NahfeldLauf --szenen %SZ% --faelle xaver/brandung/2013-12-05T13:30:00Z --kennung S2c"
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
