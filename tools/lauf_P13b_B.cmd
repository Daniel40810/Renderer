@echo off
rem P13b, Lauf B (Werkbuch HalligHooge Teil A, 12.14, Datei 88; F151-F154 A): Salzwiese als Physik.
rem 1 Renderer bauen; 2 VegetationCheck (Kern, ohne Lauf); 3 Lauf B1: G0-G2 ueber 15 Tage ab 1.10.2026, Statistik der
rem Ueberflutung nach D:\Daten\HalligHooge\_p13\ueberflutung (rund 15 min); 4 Aufbereitung, Schritt zonen; 5 ZonenCheck
rem (Pruefsteine 11, 12, 5); 6 VegetationLaufCheck nachlauf und bild: Stroemung auf G3 aus dem Xaver-Archiv ohne und
rem mit Salzwiese (je rund 15 min, Pruefstein 6), Bilder Juli und Januar (Pruefsteine 7, 14); 7 HalligHooge bauen,
rem ImportPruefung; 8 Anwendung: ruhig mit Salzwiese, 40 s.
rem Ausgaben: HalligHooge\docs\status\Status_Phase13bB_*.txt; Atteste Renderer\docs\status\P13b_Vegetation.md,
rem P13b_Ueberflutung.md, P13b_Zonen.md, P13b_Salzwiese.md; Bild Renderer\docs\status\P13b_Salzwiese.png.
rem Erwartete Dauer: rund 55 min (Schritt 3 rund 15 min, Schritt 6 rund 30 min).
rem Anhalten zwischen zwei Schritten: eine Datei STOPP.txt neben dieses Skript legen.
rem Liegt die Statistik schon (_p13\ueberflutung\ueberflutung.txt), rechnet Schritt 3 nicht neu; zum Neurechnen den Ordner wegraeumen.
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0.."

set "HH=C:\Users\Dan40810\Documents\NetBeansProjects\HalligHooge"
set "ST=%HH%\docs\status"
set "SZ=%HH%\src\szenen\Hooge.szenen"
set "RZ=%HH%\gebiet\Hooge.rezept"
set "G=D:\Daten\HalligHooge\gebiet"
set "AX=D:\Daten\HalligHooge\_p13\archiv_xaver"
set "UB=D:\Daten\HalligHooge\_p13\ueberflutung"
set "NL=D:\Daten\HalligHooge\_p13\nachlauf"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
set "R=java %U% --enable-native-access=ALL-UNNAMED -Xmx16g -cp dist\Renderer.jar"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"

set "UE=%ST%\Status_Phase13bB_.txt"
set "L1=%ST%\Status_Phase13bB_1.txt"
set "L2=%ST%\Status_Phase13bB_2.txt"
set "L3=%ST%\Status_Phase13bB_3.txt"
set "L4=%ST%\Status_Phase13bB_4.txt"
set "L5=%ST%\Status_Phase13bB_5.txt"
set "L6=%ST%\Status_Phase13bB_6.txt"
set "L7=%ST%\Status_Phase13bB_7.txt"
set "L8=%ST%\Status_Phase13bB_8.txt"
set "E=0"
set "EF=0"

for /f %%t in ('powershell -NoProfile -Command "[DateTime]::Now.Ticks"') do set "T0=%%t"
>"%UE%" echo Lauf P13b B, gestartet %DATE% %TIME%
>"%L1%" echo Status_Phase13bB_1: Renderer bauen (clean jar, check-packages)
>"%L2%" echo Status_Phase13bB_2: VegetationCheck (Kern)
>"%L3%" echo Status_Phase13bB_3: Lauf B1, Statistik der Ueberflutung nach %UB%
>"%L4%" echo Status_Phase13bB_4: Aufbereitung, Schritt zonen
>"%L5%" echo Status_Phase13bB_5: ZonenCheck (Pruefsteine 11, 12, 5)
>"%L6%" echo Status_Phase13bB_6: VegetationLaufCheck nachlauf und bild
>"%L7%" echo Status_Phase13bB_7: HalligHooge bauen, ImportPruefung
>"%L8%" echo Status_Phase13bB_8: Anwendung: ruhig mit Salzwiese, 40 s

set "N=1 Renderer bauen (clean jar, check-packages)"
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

set "N=2 VegetationCheck (Kern)"
set "L=%L2%"
set "C=%R% com.dan.physics.vegetation.demo.VegetationCheck"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=3 Lauf B1: G0-G2, 15 Tage, Statistik der Ueberflutung"
set "L=%L3%"
if exist "%UB%\ueberflutung.txt" (
  >>"%UE%" echo Statistik liegt schon in %UB% - Schritt 3 uebersprungen
  goto :schritt4
)
set "C=%R% com.dan.lauf.demo.VegetationLaufCheck --teil statistik --gebiet %G% --ziel %UB%"
call :lauf
if not exist "%UB%\ueberflutung.txt" goto :abbruch
if not "%E%"=="0" set "EF=1"
set "E=0"

:schritt4
set "N=4 Aufbereitung, Schritt zonen"
set "L=%L4%"
set "C=java %U% -Xmx8g -cp dist\Renderer.jar com.dan.aufbereitung.Aufbereitung %RZ% --schritte zonen"
call :lauf
if not "%E%"=="0" goto :abbruch

set "N=5 ZonenCheck (Pruefsteine 11, 12, 5)"
set "L=%L5%"
set "C=%R% com.dan.aufbereitung.demo.ZonenCheck --gebiet %G%"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=6 VegetationLaufCheck nachlauf und bild (Pruefsteine 6, 7, 14)"
set "L=%L6%"
set "C=%R% com.dan.lauf.demo.VegetationLaufCheck --teil nachlauf,bild --gebiet %G% --archiv %AX% --nachlauf %NL% --szenen %SZ%"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=7 Renderer.jar und FStyle.jar ins Projekt, HalligHooge bauen (clean jar)"
set "L=%L7%"
copy /y dist\Renderer.jar "%HH%\lib\Renderer.jar" >>"%L7%" 2>&1
copy /y dist\lib\FStyle.jar "%HH%\lib\FStyle.jar" >>"%L7%" 2>&1
pushd "%HH%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" (
  popd
  goto :abbruch
)
set "N=7b ImportPruefung"
set "JC=java"
if exist "%JAVA_HOME%\bin\javac.exe" set "JC="%JAVA_HOME%\bin\java.exe""
set "C=%JC% %U% tools\ImportPruefung.java src lib\FStyle.jar"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=8 Anwendung: ruhig mit Salzwiese, 40 s"
set "L=%L8%"
set "C=java %U% --enable-native-access=ALL-UNNAMED -Xmx12g -jar dist\HalligHooge.jar --szenen src\szenen\Hooge.szenen --szenario ruhig --sekunden 40"
call :lauf
if not "%E%"=="0" set "EF=1"
popd

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
