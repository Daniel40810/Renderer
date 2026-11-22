@echo off
rem P12a, Lauf A (Werkbuch HalligHooge Teil A, 12.13, Dateien 63, 64): Haeuser auf den Warften (F124-F128 A).
rem 1 LoD2 laden (Kartenwerkzeug); 2 Renderer bauen; 3 Aufbereitung, Schritt gebaeude; 4 GebaeudeCheck (Pruefsteine 1-3);
rem 5 BauCheck (Pruefsteine 4, 5); 6 HausCheck (Pruefsteine 6, 9, 10: Bilder der Warften); 7 BuehneCheck mit Haeusern aus
rem (Pruefstein 9); 8 BildCheck; 9 HalligHooge bauen, ImportPruefung; 10 Anwendung 40 s.
rem Ausgaben: HalligHooge\docs\status\Status_Phase12aA_*.txt; Atteste Renderer\docs\status\P12a_Gebaeude.md, P12a_Bau.md,
rem P12a_Haeuser.md, P6_Bild.md; Bilder Renderer\docs\status\P12a_Warften*.png und P12a_Haus_*.png;
rem Haeuser D:\Daten\HalligHooge\gebiet\bau\gebaeude.bin und gebaeude.txt.
rem Erwartete Dauer: rund 15 Minuten (Laden 6 Kacheln, BuehneCheck rund 2 min).
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
set "PY=python"
where python >nul 2>nul || set "PY=py"
set PYTHONIOENCODING=utf-8

set "UE=%ST%\Status_Phase12aA_.txt"
set "L1=%ST%\Status_Phase12aA_1.txt"
set "L2=%ST%\Status_Phase12aA_2.txt"
set "L3=%ST%\Status_Phase12aA_3.txt"
set "L4=%ST%\Status_Phase12aA_4.txt"
set "L5=%ST%\Status_Phase12aA_5.txt"
set "L6=%ST%\Status_Phase12aA_6.txt"
set "L7=%ST%\Status_Phase12aA_7.txt"
set "L8=%ST%\Status_Phase12aA_8.txt"
set "L9=%ST%\Status_Phase12aA_9.txt"
set "L10=%ST%\Status_Phase12aA_10.txt"
set "E=0"
set "EF=0"

for /f %%t in ('powershell -NoProfile -Command "[DateTime]::Now.Ticks"') do set "T0=%%t"
>"%UE%" echo Lauf P12a A, gestartet %DATE% %TIME%
>"%L1%" echo Status_Phase12aA_1: LoD2 laden (Kartenwerkzeug)
>"%L2%" echo Status_Phase12aA_2: Renderer bauen (clean jar, check-packages)
>"%L3%" echo Status_Phase12aA_3: Aufbereitung, Schritt gebaeude
>"%L4%" echo Status_Phase12aA_4: GebaeudeCheck (Pruefsteine 1-3)
>"%L5%" echo Status_Phase12aA_5: BauCheck (Pruefsteine 4, 5)
>"%L6%" echo Status_Phase12aA_6: HausCheck (Pruefsteine 6, 9, 10)
>"%L7%" echo Status_Phase12aA_7: BuehneCheck mit Haeusern aus (Pruefstein 9)
>"%L8%" echo Status_Phase12aA_8: BildCheck
>"%L9%" echo Status_Phase12aA_9: HalligHooge bauen, ImportPruefung
>"%L10%" echo Status_Phase12aA_10: Anwendung 40 s

set "N=1 LoD2 laden (Kartenwerkzeug)"
set "L=%L1%"
pushd "%HH%"
set "C=%PY% -u tools\kartenmaterial\kartenmaterial.py laden --quellen lod2 --ziel D:\Daten\HalligHooge"
call :lauf
popd
if not "%E%"=="0" goto :abbruch

set "N=2 Renderer bauen (clean jar, check-packages)"
set "L=%L2%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" goto :abbruch
powershell -NoProfile -Command "if ((Get-Item 'dist\Renderer.jar').LastWriteTime.Ticks -gt %T0%) { exit 0 } else { exit 1 }"
if errorlevel 1 (
  >>"%UE%" echo dist\Renderer.jar ist nicht neuer als der Start des Laufs
  set "N=2 Renderer bauen (jar nicht neu)"
  goto :abbruch
)
>>"%UE%" echo dist\Renderer.jar ist neu

set "N=3 Aufbereitung, Schritt gebaeude"
set "L=%L3%"
set "C=java %U% -Xmx8g -cp dist\Renderer.jar com.dan.aufbereitung.Aufbereitung ..\HalligHooge\gebiet\Hooge.rezept --schritte gebaeude"
call :lauf
if not "%E%"=="0" goto :abbruch

set "N=4 GebaeudeCheck (Pruefsteine 1-3)"
set "L=%L4%"
set "C=java %U% -Xmx4g -cp dist\Renderer.jar com.dan.aufbereitung.demo.GebaeudeCheck --gebiet %G%"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=5 BauCheck (Pruefsteine 4, 5)"
set "L=%L5%"
set "C=%R% com.dan.render.bau.demo.BauCheck"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=6 HausCheck (Pruefsteine 6, 9, 10)"
set "L=%L6%"
set "C=%R% com.dan.lauf.demo.HausCheck --szenen %SZ%"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=7 BuehneCheck mit Haeusern aus (Pruefstein 9)"
set "L=%L7%"
set "C=java %U% -Dlauf.haeuser=aus --enable-native-access=ALL-UNNAMED -Xmx16g -cp dist\Renderer.jar com.dan.lauf.demo.BuehneCheck --szenen %SZ%"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=8 BildCheck"
set "L=%L8%"
set "C=%R% com.dan.lauf.demo.BildCheck --gebiet %G% --archiv %A6% --nur 2,3"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=9 Renderer.jar und FStyle.jar ins Projekt, HalligHooge bauen (clean jar)"
set "L=%L9%"
copy /y dist\Renderer.jar "%HH%\lib\Renderer.jar" >>"%L9%" 2>&1
copy /y dist\lib\FStyle.jar "%HH%\lib\FStyle.jar" >>"%L9%" 2>&1
pushd "%HH%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" (
  popd
  goto :abbruch
)
set "N=9b ImportPruefung"
set "JC=java"
if exist "%JAVA_HOME%\bin\javac.exe" set "JC="%JAVA_HOME%\bin\java.exe""
set "C=%JC% %U% tools\ImportPruefung.java src lib\FStyle.jar"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=10 Anwendung: Ruhiger Tag, 40 s"
set "L=%L10%"
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
