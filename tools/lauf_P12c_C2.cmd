@echo off
rem P12c, Lauf C2 (Werkbuch HalligHooge Teil A, 12.13, Dateien 75, 78): Bewuchs im Bild (F135-F138 A).
rem 1 Renderer bauen; 2 BewuchsCheck (Pruefsteine 17, 16, 9); 3 BauCheck; 4 HausCheck mit Gestalt und Bewuchs aus
rem (Pruefstein 6 wie P12a); 5 GestaltLaufCheck (Pruefstein 15, mit Bewuchs); 6 BewuchsLaufCheck (Pruefsteine 18, 10);
rem 7 BuehneCheck mit Haeusern, Gestalt und Bewuchs aus (Pruefstein 9); 8 HalligHooge bauen, ImportPruefung;
rem 9 Anwendung 40 s.
rem Ausgaben: HalligHooge\docs\status\Status_Phase12cC2_*.txt; Atteste Renderer\docs\status\P12c_Bewuchs.md,
rem P12c_BewuchsLauf.md, P12a_Bau.md, P12a_Haeuser.md, P12b_GestaltLauf.md, P6_Buehne.md; Bilder Renderer\docs\status\P12c_*.png.
rem Erwartete Dauer: rund 15 Minuten.
rem Anhalten zwischen zwei Schritten: eine Datei STOPP.txt neben dieses Skript legen.
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0.."

set "HH=C:\Users\Dan40810\Documents\NetBeansProjects\HalligHooge"
set "ST=%HH%\docs\status"
set "SZ=%HH%\src\szenen\Hooge.szenen"
set "G=D:\Daten\HalligHooge\gebiet"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
set "R=java %U% --enable-native-access=ALL-UNNAMED -Xmx16g -cp dist\Renderer.jar"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"

set "UE=%ST%\Status_Phase12cC2_.txt"
set "L1=%ST%\Status_Phase12cC2_1.txt"
set "L2=%ST%\Status_Phase12cC2_2.txt"
set "L3=%ST%\Status_Phase12cC2_3.txt"
set "L4=%ST%\Status_Phase12cC2_4.txt"
set "L5=%ST%\Status_Phase12cC2_5.txt"
set "L6=%ST%\Status_Phase12cC2_6.txt"
set "L7=%ST%\Status_Phase12cC2_7.txt"
set "L8=%ST%\Status_Phase12cC2_8.txt"
set "L9=%ST%\Status_Phase12cC2_9.txt"
set "E=0"
set "EF=0"

for /f %%t in ('powershell -NoProfile -Command "[DateTime]::Now.Ticks"') do set "T0=%%t"
>"%UE%" echo Lauf P12c C2, gestartet %DATE% %TIME%
>"%L1%" echo Status_Phase12cC2_1: Renderer bauen (clean jar, check-packages)
>"%L2%" echo Status_Phase12cC2_2: BewuchsCheck (Pruefsteine 17, 16, 9)
>"%L3%" echo Status_Phase12cC2_3: BauCheck
>"%L4%" echo Status_Phase12cC2_4: HausCheck mit Gestalt und Bewuchs aus (Pruefstein 6 wie P12a)
>"%L5%" echo Status_Phase12cC2_5: GestaltLaufCheck (Pruefstein 15, mit Bewuchs)
>"%L6%" echo Status_Phase12cC2_6: BewuchsLaufCheck (Pruefsteine 18, 10)
>"%L7%" echo Status_Phase12cC2_7: BuehneCheck mit Haeusern, Gestalt und Bewuchs aus (Pruefstein 9)
>"%L8%" echo Status_Phase12cC2_8: HalligHooge bauen, ImportPruefung
>"%L9%" echo Status_Phase12cC2_9: Anwendung: Ruhiger Tag, 40 s

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

set "N=2 BewuchsCheck (Pruefsteine 17, 16, 9)"
set "L=%L2%"
set "C=%R% com.dan.lauf.demo.BewuchsCheck --gebiet %G%"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=3 BauCheck"
set "L=%L3%"
set "C=%R% com.dan.render.bau.demo.BauCheck"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=4 HausCheck mit Gestalt und Bewuchs aus (Pruefstein 6 wie P12a)"
set "L=%L4%"
set "C=java %U% -Dlauf.gestalt=aus -Dlauf.bewuchs=aus --enable-native-access=ALL-UNNAMED -Xmx16g -cp dist\Renderer.jar com.dan.lauf.demo.HausCheck --szenen %SZ%"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=5 GestaltLaufCheck (Pruefstein 15, mit Bewuchs)"
set "L=%L5%"
set "C=%R% com.dan.lauf.demo.GestaltLaufCheck --szenen %SZ%"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=6 BewuchsLaufCheck (Pruefsteine 18, 10)"
set "L=%L6%"
set "C=%R% com.dan.lauf.demo.BewuchsLaufCheck --szenen %SZ%"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=7 BuehneCheck mit Haeusern, Gestalt und Bewuchs aus (Pruefstein 9)"
set "L=%L7%"
set "C=java %U% -Dlauf.haeuser=aus -Dlauf.gestalt=aus -Dlauf.bewuchs=aus --enable-native-access=ALL-UNNAMED -Xmx16g -cp dist\Renderer.jar com.dan.lauf.demo.BuehneCheck --szenen %SZ%"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=8 Renderer.jar und FStyle.jar ins Projekt, HalligHooge bauen (clean jar)"
set "L=%L8%"
copy /y dist\Renderer.jar "%HH%\lib\Renderer.jar" >>"%L8%" 2>&1
copy /y dist\lib\FStyle.jar "%HH%\lib\FStyle.jar" >>"%L8%" 2>&1
pushd "%HH%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" (
  popd
  goto :abbruch
)
set "N=8b ImportPruefung"
set "JC=java"
if exist "%JAVA_HOME%\bin\javac.exe" set "JC="%JAVA_HOME%\bin\java.exe""
set "C=%JC% %U% tools\ImportPruefung.java src lib\FStyle.jar"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=9 Anwendung: Ruhiger Tag, 40 s"
set "L=%L9%"
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
