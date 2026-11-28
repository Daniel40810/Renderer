@echo off
rem Seegang Lauf L7j (Seegang Werkbuch, Datei 108): S9c-1 (SF175 A) - Spiegelung des Gelaendes in halber Aufloesung.
rem netz_halb.frag legt Strahl und Himmel ab, spiegel_halb.comp rechnet einen von vier Bildpunkten, spiegel_misch.frag mischt
rem dazu. Im Container: voller Weg bitgleich (Abdruck 6/6), halb gegen voll nur in der Ansicht mit Wand anders.
rem 1 Renderer bauen; 2 SeegangNetzCheck; 3 LeistungCheck --satz s9c (Ersparnis halb gegen voll, Pruefstein 45 mit fester
rem Belichtung, Bilder halb/voll fuer anleger_see und bord_kutter); 4 SeegangAbnahme ohne Warmlauf (Pruefstein 44).
rem Ausgaben: HalligHooge\docs\status\Status_L7j_*.txt, S9c_Leistung.md/.csv, S9c_*_halb.png/_voll.png, S9j_Abnahme.md.
rem Erwartete Dauer: rund 25 min, hoechstens 35 min [Bau 2, NetzCheck 1, LeistungCheck 9, Abnahme 12].
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
set "UE=%ST%\Status_L7j_.txt"
set "L1=%ST%\Status_L7j_1.txt"
set "L2=%ST%\Status_L7j_2.txt"
set "L3=%ST%\Status_L7j_3.txt"
set "L4=%ST%\Status_L7j_4.txt"
set "E=0"
set "EF=0"
>"%UE%" echo Lauf L7j (S9c-1 Spiegelung in halber Aufloesung), gestartet %DATE% %TIME%
>"%L1%" echo Status_L7j_1: Renderer bauen
>"%L2%" echo Status_L7j_2: SeegangNetzCheck
>"%L3%" echo Status_L7j_3: LeistungCheck --satz s9c
>"%L4%" echo Status_L7j_4: SeegangAbnahme

set "N=1 Renderer bauen (clean jar, SPIR-V, check-packages)"
set "L=%L1%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" goto :abbruch
copy /y "%RE%\dist\Renderer.jar" "%SH%\lib\Renderer.jar" >>"%L1%" 2>&1
copy /y "%RE%\dist\Renderer.jar" "%HH%\lib\Renderer.jar" >>"%L1%" 2>&1
set "N=2 SeegangNetzCheck (voller Weg bitgleich, halb gegen voll)"
set "L=%L2%"
set "C=%R% com.dan.lauf.demo.SeegangNetzCheck"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=3 LeistungCheck --satz s9c (Spiegelung halb gegen voll)"
set "L=%L3%"
set "C=%R% com.dan.lauf.demo.LeistungCheck --szenen %SZ% --satz s9c"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"
copy /y "%RE%\docs\status\S9c_Leistung.md" "%ST%\S9c_Leistung.md" >>"%L3%" 2>&1
copy /y "%RE%\docs\status\S9c_Leistung.csv" "%ST%\S9c_Leistung.csv" >>"%L3%" 2>&1
copy /y "%RE%\docs\status\S9c_*.png" "%ST%\" >>"%L3%" 2>&1

set "N=4 SeegangAbnahme ohne Warmlauf"
set "L=%L4%"
set "C=%R% com.dan.lauf.demo.SeegangAbnahme --szenen %SZ%"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"
copy /y "%RE%\docs\status\S9b_Abnahme.md" "%ST%\S9j_Abnahme.md" >>"%L4%" 2>&1

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
