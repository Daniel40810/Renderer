@echo off
rem P13a, Lauf A2 (Werkbuch HalligHooge Teil A, 12.14, Datei 85; F145 A): die Sturmflut Xaver, 3.12. 06 Uhr bis 6.12.
rem 18 Uhr UTC (48 h Einschwingen, 36 h Archiv ueber drei Hochwasser), Kette G0-G3 mit Seegang und Sediment.
rem 1 Renderer bauen; 2 SedimentLaufCheck: Lauf und Archiv nach D:\Daten\HalligHooge\_p13\archiv_xaver (rund 4,5 GB);
rem 3 XaverCheck, Teile scheitel und bild (Pruefsteine 1-4, 9, 10); 4 HalligHooge bauen, ImportPruefung;
rem 5 Anwendung: Xaver, 40 s; 6 ReferenceCheck (Kern, voll, wie in Datei 83 versprochen).
rem Ausgaben: HalligHooge\docs\status\Status_Phase13aA2_*.txt; Atteste Renderer\docs\status\P13a_Xaver_Lauf.md,
rem P13a_Xaver.md; Bilder Renderer\docs\status\P13a_Xaver*.png.
rem Erwartete Dauer: rund 1,5 bis 2 Stunden, davon der Lauf (Schritt 2) den groessten Teil.
rem Anhalten zwischen zwei Schritten: eine Datei STOPP.txt neben dieses Skript legen.
rem Liegt das Archiv schon (sediment_G3\lauf.txt), rechnet Schritt 2 nicht neu; zum Neurechnen den Ordner wegraeumen.
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0.."

set "HH=C:\Users\Dan40810\Documents\NetBeansProjects\HalligHooge"
set "ST=%HH%\docs\status"
set "SZ=%HH%\src\szenen\Hooge.szenen"
set "G=D:\Daten\HalligHooge\gebiet"
set "AX=D:\Daten\HalligHooge\_p13\archiv_xaver"
set "F3=400,850,700,400"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
set "R=java %U% --enable-native-access=ALL-UNNAMED -Xmx16g -cp dist\Renderer.jar"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"

set "UE=%ST%\Status_Phase13aA2_.txt"
set "L1=%ST%\Status_Phase13aA2_1.txt"
set "L2=%ST%\Status_Phase13aA2_2.txt"
set "L3=%ST%\Status_Phase13aA2_3.txt"
set "L4=%ST%\Status_Phase13aA2_4.txt"
set "L5=%ST%\Status_Phase13aA2_5.txt"
set "L6=%ST%\Status_Phase13aA2_6.txt"
set "E=0"
set "EF=0"

for /f %%t in ('powershell -NoProfile -Command "[DateTime]::Now.Ticks"') do set "T0=%%t"
>"%UE%" echo Lauf P13a A2, gestartet %DATE% %TIME%
>"%L1%" echo Status_Phase13aA2_1: Renderer bauen (clean jar, check-packages)
>"%L2%" echo Status_Phase13aA2_2: SedimentLaufCheck Xaver, Lauf und Archiv nach %AX%
>"%L3%" echo Status_Phase13aA2_3: XaverCheck, Teile scheitel und bild
>"%L4%" echo Status_Phase13aA2_4: HalligHooge bauen, ImportPruefung
>"%L5%" echo Status_Phase13aA2_5: Anwendung: Xaver, 40 s
>"%L6%" echo Status_Phase13aA2_6: ReferenceCheck (Kern, voll)

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

set "N=2 SedimentLaufCheck Xaver (Lauf 84 h Modellzeit, Archiv 36 h)"
set "L=%L2%"
set "C=%R% com.dan.lauf.demo.SedimentLaufCheck --gebiet %G% --archiv %AX% --archiv-ruhig - --lauf sturm --nur 1 --fenster3 %F3% --start 2013-12-03T06:00:00Z --ab 2013-12-05T06:00:00Z --sediment-ab 2013-12-04T18:00:00Z --ende 2013-12-06T18:00:00Z --attest P13a_Xaver_Lauf"
call :lauf
if not exist "%AX%\G3\lauf.txt" goto :abbruch
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=3 XaverCheck, Teile scheitel und bild (Pruefsteine 1-4, 9, 10)"
set "L=%L3%"
set "C=%R% com.dan.lauf.demo.XaverCheck --teil scheitel,bild --gebiet %G% --archiv %AX% --szenen %SZ%"
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

set "N=5 Anwendung: Xaver, 40 s"
set "L=%L5%"
set "C=java %U% --enable-native-access=ALL-UNNAMED -Xmx12g -jar dist\HalligHooge.jar --szenen src\szenen\Hooge.szenen --szenario xaver --sekunden 40"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"
popd

set "N=6 ReferenceCheck (Kern, voll)"
set "L=%L6%"
set "C=%R% com.dan.render.reference.demo.ReferenceCheck"
call :lauf
if not "%E%"=="0" set "EF=1"

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
