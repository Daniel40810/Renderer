@echo off
rem P13b, Lauf B5 (Werkbuch HalligHooge Teil A, 12.14, Datei 93): nur die Bilder zur Sichtpruefung, mit der neuen Ansicht
rem "vorland" (Hooge.szenen) mitten in Pionierzone und unterer Salzwiese. Kein Bau: dist\Renderer.jar aus Lauf B4.
rem Ausgaben: HalligHooge\docs\status\Status_Phase13bB5_.txt; Attest Renderer\docs\status\P13b_Salzwiese.md;
rem Bild Renderer\docs\status\P13b_Salzwiese.png (Juli, September, Januar je Ansicht).
rem Erwartete Dauer: rund 3 min.
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0.."
set "HH=C:\Users\Dan40810\Documents\NetBeansProjects\HalligHooge"
set "L=%HH%\docs\status\Status_Phase13bB5_.txt"
>"%L%" echo Lauf P13b B5, gestartet %DATE% %TIME%
>>"%L%" echo.
java -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 --enable-native-access=ALL-UNNAMED -Xmx16g -cp dist\Renderer.jar com.dan.lauf.demo.VegetationLaufCheck --teil bild --szenen "%HH%\src\szenen\Hooge.szenen" --ansichten halme,vorland,lahnungen,warften >>"%L%" 2>&1
>>"%L%" echo.
>>"%L%" echo Exit %ERRORLEVEL% - FERTIG %DATE% %TIME%
echo Fertig. Siehe %L%
timeout /t 20 >nul
