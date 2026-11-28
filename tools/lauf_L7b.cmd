@echo off
rem Seegang Lauf L7b (Seegang Werkbuch, Datei 107): S9b Leistung - neue Wege und Abnahme warm (SF171-SF174 A).
rem Neu: S9b-1 Spiegelung des Gelaendes mit grober Tiefe je Kachel (16 x 16) und Hoehenschranke (tiefe_grob.comp);
rem S9b-2 Kraeusel brechen ab, sobald eine Welle unter dem Bildpunkt liegt; S9b-3 Linse: Hoehenkarte je Kachel nur mit
rem ihren Tropfen, das Bild nur in den Kacheln mit Tropfen. Alle drei im Container bitgleich gegen den alten Weg
rem (SeegangNetzCheck 6 Ansichten, LinseRechteckCheck 7 Faelle). Je Neuerung ein Schalter fuer den alten Weg.
rem 1 Renderer, Steuerhaus, HalligHooge bauen; 2 SeegangNetzCheck (bitgleich auf der Karte); 3 LinseRechteckCheck;
rem 4 LeistungCheck --satz s9b (Ersparnis je neuem Weg, Pruefstein 45/46 je Ansicht, S9b-0 laufend gegen stehend,
rem stockende Bilder, Faden je Bild); 5 SeegangAbnahme warm und eingeschwungen (Pruefstein 44).
rem Ausgaben: HalligHooge\docs\status\Status_L7b_*.txt, S9b_Leistung.md/.csv, S9b_Abnahme.md, S9b_*.png, S9b3_Linse.md.
rem Erwartete Dauer: rund 30 min, hoechstens 45 min [geschaetzt: Bau 3, Pruefungen 2, LeistungCheck 12, Abnahme 12].
rem Gestartet von Claude per Explorer. Anhalten zwischen zwei Schritten: eine Datei STOPP.txt neben dieses Skript legen.
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0.."

set "P=C:\Users\Dan40810\Documents\NetBeansProjects"
set "HH=%P%\HalligHooge"
set "SH=%P%\Steuerhaus"
set "RE=%P%\Renderer"
set "ST=%HH%\docs\status"
set "SZ=%HH%\src\szenen\Hooge.szenen"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
set "R=java %U% --enable-native-access=ALL-UNNAMED -Xmx16g -cp dist\Renderer.jar"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"

if not exist "%ST%" mkdir "%ST%"
set "UE=%ST%\Status_L7b_.txt"
set "L1=%ST%\Status_L7b_1.txt"
set "L2=%ST%\Status_L7b_2.txt"
set "L3=%ST%\Status_L7b_3.txt"
set "L4=%ST%\Status_L7b_4.txt"
set "L5=%ST%\Status_L7b_5.txt"
set "E=0"
set "EF=0"
>"%UE%" echo Lauf L7b (S9b Leistung: neue Wege, Abnahme warm), gestartet %DATE% %TIME%
>"%L1%" echo Status_L7b_1: Renderer, Steuerhaus, HalligHooge bauen
>"%L2%" echo Status_L7b_2: SeegangNetzCheck
>"%L3%" echo Status_L7b_3: LinseRechteckCheck
>"%L4%" echo Status_L7b_4: LeistungCheck --satz s9b
>"%L5%" echo Status_L7b_5: SeegangAbnahme --warm 180

set "N=1 Renderer bauen (clean jar, SPIR-V, check-packages)"
set "L=%L1%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" goto :abbruch
copy /y "%RE%\dist\Renderer.jar" "%SH%\lib\Renderer.jar" >>"%L1%" 2>&1
copy /y "%RE%\dist\Renderer.jar" "%HH%\lib\Renderer.jar" >>"%L1%" 2>&1
pushd "%SH%"
set "N=1b Steuerhaus bauen (clean jar)"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
popd
if not "%E%"=="0" goto :abbruch
pushd "%HH%"
set "N=1c HalligHooge bauen (clean jar)"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
popd
if not "%E%"=="0" goto :abbruch

set "N=2 SeegangNetzCheck (neue Wege gegen alte, bitgleich)"
set "L=%L2%"
set "C=%R% com.dan.lauf.demo.SeegangNetzCheck"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=3 LinseRechteckCheck (Linse in Kacheln gegen ganzes Bild)"
set "L=%L3%"
set "C=%R% com.dan.render.frame.demo.LinseRechteckCheck"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"
copy /y "%RE%\docs\status\S9b3_Linse.md" "%ST%\S9b3_Linse.md" >>"%L3%" 2>&1

set "N=4 LeistungCheck --satz s9b (acht Ansichten)"
set "L=%L4%"
set "C=%R% com.dan.lauf.demo.LeistungCheck --szenen %SZ% --satz s9b"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"
copy /y "%RE%\docs\status\S9b_Leistung.md" "%ST%\S9b_Leistung.md" >>"%L4%" 2>&1
copy /y "%RE%\docs\status\S9b_Leistung.csv" "%ST%\S9b_Leistung.csv" >>"%L4%" 2>&1

set "N=5 SeegangAbnahme warm (180 s Warmlauf) und eingeschwungen"
set "L=%L5%"
set "C=%R% com.dan.lauf.demo.SeegangAbnahme --szenen %SZ% --warm 180"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"
copy /y "%RE%\docs\status\S9b_Abnahme.md" "%ST%\S9b_Abnahme.md" >>"%L5%" 2>&1
copy /y "%RE%\docs\status\S9b_*.png" "%ST%\" >>"%L5%" 2>&1

>>"%UE%" echo.
if not "%EF%"=="0" >>"%UE%" echo Mindestens ein Schritt mit Exit ungleich 0 - siehe die Statusdateien
>>"%UE%" echo FERTIG %DATE% %TIME%
echo.
echo Fertig. Uebersicht: %UE%
timeout /t 5 >nul
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
