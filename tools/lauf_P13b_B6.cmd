@echo off
rem P13b, Lauf B6 (Werkbuch HalligHooge Teil A, 12.14, Datei 94): Bilder zur Sichtpruefung mit der Ansicht "vorland"
rem mitten in Pionier- und unterer Zone (O 472 440, N 6 046 360, Blick 20 Grad; Halme stehen nur bis 30 m vom Auge),
rem Buschel je Art und Karte der Zonen um das Auge (P13b_Zonen_Vorland.png). 1 Renderer bauen (Bewuchsbild zaehlt die
rem Buschel je Art); 2 VegetationLaufCheck --teil bild.
rem Ausgaben: HalligHooge\docs\status\Status_Phase13bB6_.txt; Attest Renderer\docs\status\P13b_Salzwiese.md;
rem Bilder Renderer\docs\status\P13b_Salzwiese.png, P13b_Zonen_Vorland.png.
rem Erwartete Dauer: rund 3 min.
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0.."
set "HH=C:\Users\Dan40810\Documents\NetBeansProjects\HalligHooge"
set "L=%HH%\docs\status\Status_Phase13bB6_.txt"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
>"%L%" echo Lauf P13b B6, gestartet %DATE% %TIME%
>>"%L%" echo.
call "%ANT%" -f build.xml clean jar >>"%L%" 2>&1
if errorlevel 1 (
  >>"%L%" echo ABBRUCH: Renderer bauen
  echo Abbruch beim Bauen. Siehe %L%
  timeout /t 60 >nul
  exit /b 1
)
>>"%L%" echo.
java -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 --enable-native-access=ALL-UNNAMED -Xmx16g -cp dist\Renderer.jar com.dan.lauf.demo.VegetationLaufCheck --teil bild --szenen "%HH%\src\szenen\Hooge.szenen" --ansichten halme,vorland,lahnungen,warften >>"%L%" 2>&1
>>"%L%" echo.
>>"%L%" echo Exit %ERRORLEVEL% - FERTIG %DATE% %TIME%
echo Fertig. Siehe %L%
timeout /t 20 >nul
