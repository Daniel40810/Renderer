@echo off
rem P13b, Lauf B7 (Werkbuch HalligHooge Teil A, 12.14, Datei 96; F157 A, F158 A): Halme der Zonen mit ausgeglichener
rem Flaeche (Kosten), Salzwiese unter der Pionierzone als Schlick ohne Halme. 1 Renderer bauen; 2 VegetationCheck;
rem 3 VegetationLaufCheck --teil bild (Pruefsteine 7, 14, Bilder, Karte P13b_Zonen_Vorland.png).
rem Ausgaben: HalligHooge\docs\status\Status_Phase13bB7_.txt; Atteste Renderer\docs\status\P13b_Vegetation.md,
rem P13b_Salzwiese.md; Bilder Renderer\docs\status\P13b_Salzwiese.png, P13b_Zonen_Vorland.png.
rem Erwartete Dauer: rund 4 min.
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0.."
set "HH=C:\Users\Dan40810\Documents\NetBeansProjects\HalligHooge"
set "L=%HH%\docs\status\Status_Phase13bB7_.txt"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
>"%L%" echo Lauf P13b B7, gestartet %DATE% %TIME%
>>"%L%" echo.
call "%ANT%" -f build.xml clean jar >>"%L%" 2>&1
if errorlevel 1 (
  >>"%L%" echo ABBRUCH: Renderer bauen
  echo Abbruch beim Bauen. Siehe %L%
  timeout /t 60 >nul
  exit /b 1
)
>>"%L%" echo.
java %U% -cp dist\Renderer.jar com.dan.physics.vegetation.demo.VegetationCheck >>"%L%" 2>&1
>>"%L%" echo.
java %U% --enable-native-access=ALL-UNNAMED -Xmx16g -cp dist\Renderer.jar com.dan.lauf.demo.VegetationLaufCheck --teil bild --szenen "%HH%\src\szenen\Hooge.szenen" --ansichten halme,vorland,lahnungen,warften >>"%L%" 2>&1
>>"%L%" echo.
>>"%L%" echo Exit %ERRORLEVEL% - FERTIG %DATE% %TIME%
echo Fertig. Siehe %L%
timeout /t 20 >nul
