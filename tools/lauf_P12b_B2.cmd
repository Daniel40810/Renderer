@echo off
rem P12b, Lauf B2 (Werkbuch HalligHooge Teil A, 12.13, Datei 71): nach Lauf B - Koerper nur in den Kaskaden 0 und 1,
rem Gestalt im Shader sparsamer, Grund unter der Flut (F134 A), Ansicht fething 30 m vom Ufer.
rem 1 Renderer bauen; 2 GestaltBildCheck (Pruefsteine 12, 13, 9, Grund); 3 BauCheck; 4 HausCheck mit Gestalt aus (Pruefstein 6
rem wie P12a); 5 GestaltLaufCheck (Pruefsteine 15, 10); 6 BuehneCheck mit Haeusern und Gestalt aus (Pruefstein 9).
rem Ausgaben: HalligHooge\docs\status\Status_Phase12bB2_*.txt; Atteste Renderer\docs\status\P12b_GestaltBild.md,
rem P12a_Bau.md, P12a_Haeuser.md, P12b_GestaltLauf.md; Bilder Renderer\docs\status\P12b_*.png.
rem Erwartete Dauer: rund 5 Minuten.
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

set "UE=%ST%\Status_Phase12bB2_.txt"
set "L1=%ST%\Status_Phase12bB2_1.txt"
set "L2=%ST%\Status_Phase12bB2_2.txt"
set "L3=%ST%\Status_Phase12bB2_3.txt"
set "L4=%ST%\Status_Phase12bB2_4.txt"
set "L5=%ST%\Status_Phase12bB2_5.txt"
set "L6=%ST%\Status_Phase12bB2_6.txt"
set "E=0"
set "EF=0"

for /f %%t in ('powershell -NoProfile -Command "[DateTime]::Now.Ticks"') do set "T0=%%t"
>"%UE%" echo Lauf P12b B2, gestartet %DATE% %TIME%
>"%L1%" echo Status_Phase12bB2_1: Renderer bauen (clean jar, check-packages)
>"%L2%" echo Status_Phase12bB2_2: GestaltBildCheck (Pruefsteine 12, 13, 9, Grund)
>"%L3%" echo Status_Phase12bB2_3: BauCheck
>"%L4%" echo Status_Phase12bB2_4: HausCheck mit Gestalt aus (Pruefstein 6 wie P12a)
>"%L5%" echo Status_Phase12bB2_5: GestaltLaufCheck (Pruefsteine 15, 10)
>"%L6%" echo Status_Phase12bB2_6: BuehneCheck mit Haeusern und Gestalt aus (Pruefstein 9)

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

set "N=2 GestaltBildCheck (Pruefsteine 12, 13, 9, Grund)"
set "L=%L2%"
set "C=%R% com.dan.render.terrain.demo.GestaltBildCheck --gebiet %G%"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=3 BauCheck"
set "L=%L3%"
set "C=%R% com.dan.render.bau.demo.BauCheck"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=4 HausCheck mit Gestalt aus (Pruefstein 6 wie P12a)"
set "L=%L4%"
set "C=java %U% -Dlauf.gestalt=aus --enable-native-access=ALL-UNNAMED -Xmx16g -cp dist\Renderer.jar com.dan.lauf.demo.HausCheck --szenen %SZ%"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=5 GestaltLaufCheck (Pruefsteine 15, 10)"
set "L=%L5%"
set "C=%R% com.dan.lauf.demo.GestaltLaufCheck --szenen %SZ%"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=6 BuehneCheck mit Haeusern und Gestalt aus (Pruefstein 9)"
set "L=%L6%"
set "C=java %U% -Dlauf.haeuser=aus -Dlauf.gestalt=aus --enable-native-access=ALL-UNNAMED -Xmx16g -cp dist\Renderer.jar com.dan.lauf.demo.BuehneCheck --szenen %SZ%"
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
