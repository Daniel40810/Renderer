@echo off
rem P13b, Lauf B3 (Werkbuch HalligHooge Teil A, 12.14, Datei 91): Nachlauf und Bild nach dem zweiten Fehler aus Lauf B2
rem (Salzwiese vor hydro und atmos anmelden). Zonen und Statistik bleiben. 1 Renderer bauen; 2 VegetationCheck;
rem 3 VegetationLaufCheck verdrahtung, nachlauf, bild: kleiner Bus mit Boden von aussen, Stroemung mit Salzwiese (rund
rem 12 min; ohne liegt aus B2 vor), Bilder Juli und Januar (Pruefsteine 6, 7, 14); 4 HalligHooge bauen, ImportPruefung;
rem 5 Anwendung: ruhig mit Salzwiese, 40 s.
rem Ausgaben: HalligHooge\docs\status\Status_Phase13bB3_*.txt; Atteste Renderer\docs\status\P13b_Vegetation.md,
rem P13b_Salzwiese.md; Bild Renderer\docs\status\P13b_Salzwiese.png.
rem Erwartete Dauer: rund 20 min.
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

set "UE=%ST%\Status_Phase13bB3_.txt"
set "L1=%ST%\Status_Phase13bB3_1.txt"
set "L2=%ST%\Status_Phase13bB3_2.txt"
set "L3=%ST%\Status_Phase13bB3_3.txt"
set "L4=%ST%\Status_Phase13bB3_4.txt"
set "L5=%ST%\Status_Phase13bB3_5.txt"
set "E=0"
set "EF=0"

for /f %%t in ('powershell -NoProfile -Command "[DateTime]::Now.Ticks"') do set "T0=%%t"
>"%UE%" echo Lauf P13b B3, gestartet %DATE% %TIME%
>"%L1%" echo Status_Phase13bB3_1: Renderer bauen (clean jar, check-packages)
>"%L2%" echo Status_Phase13bB3_2: VegetationCheck (Kern)
>"%L3%" echo Status_Phase13bB3_3: VegetationLaufCheck verdrahtung, nachlauf und bild
>"%L4%" echo Status_Phase13bB3_4: HalligHooge bauen, ImportPruefung
>"%L5%" echo Status_Phase13bB3_5: Anwendung: ruhig mit Salzwiese, 40 s

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

set "N=3 VegetationLaufCheck verdrahtung, nachlauf, bild (Pruefsteine 6, 7, 14)"
set "L=%L3%"
set "C=%R% com.dan.lauf.demo.VegetationLaufCheck --teil verdrahtung,nachlauf,bild --gebiet %G% --archiv %AX% --nachlauf %NL% --szenen %SZ%"
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
