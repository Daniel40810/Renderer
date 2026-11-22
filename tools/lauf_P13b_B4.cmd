@echo off
rem P13b, Lauf B4 (Werkbuch HalligHooge Teil A, 12.14, Datei 92): Bilder bei Tag (Lauf B3 zeichnete um 4 Uhr UTC) und
rem Pruefstein 13 (Massenbilanz sediment und aeolian mit Salzwiese), der im Bau fehlte. Die Nachlaeufe aus B2/B3 bleiben.
rem 1 Renderer bauen; 2 VegetationCheck; 3 VegetationLaufCheck verdrahtung, bilanz, nachlauf, bild: Becken mit Tide
rem (Sekunden), Nachlauf aeolian zu _p9c\archiv_m1b ohne Ablage (einige Minuten), Pruefstein 6 aus den vorhandenen
rem Nachlaeufen, Bilder Juli, September, Januar am 30.10.2026 11 Uhr UTC; 4 HalligHooge bauen, ImportPruefung;
rem 5 Anwendung: ruhig mit Salzwiese, 40 s.
rem Ausgaben: HalligHooge\docs\status\Status_Phase13bB4_*.txt; Atteste Renderer\docs\status\P13b_Vegetation.md,
rem P13b_Salzwiese.md; Bild Renderer\docs\status\P13b_Salzwiese.png.
rem Erwartete Dauer: rund 10-15 min.
rem Anhalten zwischen zwei Schritten: eine Datei STOPP.txt neben dieses Skript legen.
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0.."

set "HH=C:\Users\Dan40810\Documents\NetBeansProjects\HalligHooge"
set "ST=%HH%\docs\status"
set "SZ=%HH%\src\szenen\Hooge.szenen"
set "RZ=%HH%\gebiet\Hooge.rezept"
set "G=D:\Daten\HalligHooge\gebiet"
set "AX=D:\Daten\HalligHooge\_p13\archiv_xaver"
set "NL=D:\Daten\HalligHooge\_p13\nachlauf"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
set "R=java %U% --enable-native-access=ALL-UNNAMED -Xmx16g -cp dist\Renderer.jar"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"

set "UE=%ST%\Status_Phase13bB4_.txt"
set "L1=%ST%\Status_Phase13bB4_1.txt"
set "L2=%ST%\Status_Phase13bB4_2.txt"
set "L3=%ST%\Status_Phase13bB4_3.txt"
set "L4=%ST%\Status_Phase13bB4_4.txt"
set "L5=%ST%\Status_Phase13bB4_5.txt"
set "E=0"
set "EF=0"

for /f %%t in ('powershell -NoProfile -Command "[DateTime]::Now.Ticks"') do set "T0=%%t"
>"%UE%" echo Lauf P13b B4, gestartet %DATE% %TIME%
>"%L1%" echo Status_Phase13bB4_1: Renderer bauen (clean jar, check-packages)
>"%L2%" echo Status_Phase13bB4_2: VegetationCheck (Kern)
>"%L3%" echo Status_Phase13bB4_3: VegetationLaufCheck verdrahtung, bilanz, nachlauf und bild
>"%L4%" echo Status_Phase13bB4_4: HalligHooge bauen, ImportPruefung
>"%L5%" echo Status_Phase13bB4_5: Anwendung: ruhig mit Salzwiese, 40 s

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

set "N=3 VegetationLaufCheck verdrahtung, bilanz, nachlauf, bild (Pruefsteine 6, 7, 13, 14)"
set "L=%L3%"
set "C=%R% com.dan.lauf.demo.VegetationLaufCheck --teil verdrahtung,bilanz,nachlauf,bild --gebiet %G% --archiv %AX% --nachlauf %NL% --szenen %SZ%"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=4 Renderer.jar und FStyle.jar ins Projekt, HalligHooge bauen (clean jar)"
set "L=%L4%"
copy /y dist\Renderer.jar "%HH%\lib\Renderer.jar" >>"%L4%" 2>&1
copy /y dist\lib\FStyle.jar "%HH%\lib\FStyle.jar" >>"%L4%" 2>&1
pushd "%HH%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" (
  popd
  goto :abbruch
)
set "N=4b ImportPruefung"
set "JC=java"
if exist "%JAVA_HOME%\bin\javac.exe" set "JC="%JAVA_HOME%\bin\java.exe""
set "C=%JC% %U% tools\ImportPruefung.java src lib\FStyle.jar"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=5 Anwendung: ruhig mit Salzwiese, 40 s"
set "L=%L5%"
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
