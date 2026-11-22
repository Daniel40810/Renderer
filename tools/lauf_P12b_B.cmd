@echo off
rem P12b, Lauf B (Werkbuch HalligHooge Teil A, 12.13, Dateien 69, 70): Gestalt des Gelaendes (F132-F134 A).
rem 1 Renderer bauen; 2 Aufbereitung, Schritt gestalt; 3 GestaltCheck (Pruefsteine 7, 11, 14); 4 GestaltBildCheck
rem (Pruefsteine 12, 13, 9); 5 TerrainCheck; 6 BauCheck; 7 HausCheck (mit Koerpern); 8 GestaltLaufCheck (Pruefsteine 15, 10);
rem 9 BuehneCheck mit Haeusern und Gestalt aus (Pruefstein 9); 10 BildCheck mit Gestalt aus; 11 HalligHooge bauen,
rem ImportPruefung; 12 Anwendung 40 s.
rem Ausgaben: HalligHooge\docs\status\Status_Phase12bB_*.txt; Atteste Renderer\docs\status\P12b_Gestalt.md,
rem P12b_GestaltBild.md, P12b_GestaltLauf.md, P6_Gelaende.md, P12a_Bau.md, P12a_Haeuser.md, P6_Bild.md; Bilder Renderer\docs\status\P12b_*.png;
rem Gestalt D:\Daten\HalligHooge\gebiet\gestalt\ und bau\koerper.bin.
rem Erwartete Dauer: rund 15 bis 20 Minuten.
rem Anhalten zwischen zwei Schritten: eine Datei STOPP.txt neben dieses Skript legen.
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0.."

set "HH=C:\Users\Dan40810\Documents\NetBeansProjects\HalligHooge"
set "ST=%HH%\docs\status"
set "SZ=%HH%\src\szenen\Hooge.szenen"
set "G=D:\Daten\HalligHooge\gebiet"
set "A6=D:\Daten\HalligHooge\_p6\archiv"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
set "R=java %U% --enable-native-access=ALL-UNNAMED -Xmx16g -cp dist\Renderer.jar"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"

set "UE=%ST%\Status_Phase12bB_.txt"
set "L1=%ST%\Status_Phase12bB_1.txt"
set "L2=%ST%\Status_Phase12bB_2.txt"
set "L3=%ST%\Status_Phase12bB_3.txt"
set "L4=%ST%\Status_Phase12bB_4.txt"
set "L5=%ST%\Status_Phase12bB_5.txt"
set "L6=%ST%\Status_Phase12bB_6.txt"
set "L7=%ST%\Status_Phase12bB_7.txt"
set "L8=%ST%\Status_Phase12bB_8.txt"
set "L9=%ST%\Status_Phase12bB_9.txt"
set "L10=%ST%\Status_Phase12bB_10.txt"
set "L11=%ST%\Status_Phase12bB_11.txt"
set "L12=%ST%\Status_Phase12bB_12.txt"
set "E=0"
set "EF=0"

for /f %%t in ('powershell -NoProfile -Command "[DateTime]::Now.Ticks"') do set "T0=%%t"
>"%UE%" echo Lauf P12b B, gestartet %DATE% %TIME%
>"%L1%" echo Status_Phase12bB_1: Renderer bauen (clean jar, check-packages)
>"%L2%" echo Status_Phase12bB_2: Aufbereitung, Schritt gestalt
>"%L3%" echo Status_Phase12bB_3: GestaltCheck (Pruefsteine 7, 11, 14)
>"%L4%" echo Status_Phase12bB_4: GestaltBildCheck (Pruefsteine 12, 13, 9)
>"%L5%" echo Status_Phase12bB_5: TerrainCheck
>"%L6%" echo Status_Phase12bB_6: BauCheck
>"%L7%" echo Status_Phase12bB_7: HausCheck mit Koerpern
>"%L8%" echo Status_Phase12bB_8: GestaltLaufCheck (Pruefsteine 15, 10)
>"%L9%" echo Status_Phase12bB_9: BuehneCheck mit Haeusern und Gestalt aus (Pruefstein 9)
>"%L10%" echo Status_Phase12bB_10: BildCheck mit Gestalt aus
>"%L11%" echo Status_Phase12bB_11: HalligHooge bauen, ImportPruefung
>"%L12%" echo Status_Phase12bB_12: Anwendung: Ruhiger Tag, 40 s

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

set "N=2 Aufbereitung, Schritt gestalt"
set "L=%L2%"
set "C=java %U% -Xmx12g -cp dist\Renderer.jar com.dan.aufbereitung.Aufbereitung ..\HalligHooge\gebiet\Hooge.rezept --schritte gestalt"
call :lauf
if not "%E%"=="0" goto :abbruch

set "N=3 GestaltCheck (Pruefsteine 7, 11, 14)"
set "L=%L3%"
set "C=java %U% -Xmx4g -cp dist\Renderer.jar com.dan.aufbereitung.demo.GestaltCheck --gebiet %G%"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=4 GestaltBildCheck (Pruefsteine 12, 13, 9)"
set "L=%L4%"
set "C=%R% com.dan.render.terrain.demo.GestaltBildCheck --gebiet %G%"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=5 TerrainCheck"
set "L=%L5%"
set "C=%R% com.dan.render.terrain.demo.TerrainCheck --gebiet %G%"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=6 BauCheck"
set "L=%L6%"
set "C=%R% com.dan.render.bau.demo.BauCheck"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=7 HausCheck mit Koerpern"
set "L=%L7%"
set "C=%R% com.dan.lauf.demo.HausCheck --szenen %SZ%"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=8 GestaltLaufCheck (Pruefsteine 15, 10)"
set "L=%L8%"
set "C=%R% com.dan.lauf.demo.GestaltLaufCheck --szenen %SZ%"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=9 BuehneCheck mit Haeusern und Gestalt aus (Pruefstein 9)"
set "L=%L9%"
set "C=java %U% -Dlauf.haeuser=aus -Dlauf.gestalt=aus --enable-native-access=ALL-UNNAMED -Xmx16g -cp dist\Renderer.jar com.dan.lauf.demo.BuehneCheck --szenen %SZ%"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=10 BildCheck mit Gestalt aus"
set "L=%L10%"
set "C=java %U% -Dlauf.gestalt=aus --enable-native-access=ALL-UNNAMED -Xmx16g -cp dist\Renderer.jar com.dan.lauf.demo.BildCheck --gebiet %G% --archiv %A6% --nur 2,3"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=11 Renderer.jar und FStyle.jar ins Projekt, HalligHooge bauen (clean jar)"
set "L=%L11%"
copy /y dist\Renderer.jar "%HH%\lib\Renderer.jar" >>"%L11%" 2>&1
copy /y dist\lib\FStyle.jar "%HH%\lib\FStyle.jar" >>"%L11%" 2>&1
pushd "%HH%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" (
  popd
  goto :abbruch
)
set "N=11b ImportPruefung"
set "JC=java"
if exist "%JAVA_HOME%\bin\javac.exe" set "JC="%JAVA_HOME%\bin\java.exe""
set "C=%JC% %U% tools\ImportPruefung.java src lib\FStyle.jar"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=12 Anwendung: Ruhiger Tag, 40 s"
set "L=%L12%"
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
