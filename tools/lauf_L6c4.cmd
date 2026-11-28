@echo off
rem Seegang Lauf L6c4 (Seegang Werkbuch, Datei 104): S8e2 - das Video zum Regiebuch "Seegang" neu, mit Seegang, Brandung und Schiffen.
rem Befund 16 (Diagnose L6c3b): Nach "szenario ruhigsee ... raffer 1" lief die Szene 300-fach - das neue Szenario stellte seinen
rem eigenen Zeitraffer ein, nachdem der Regisseur den des Regiebuchs gesetzt hatte. Der Adler-Express lag darum schon am Anleger;
rem an Bord und beim Schwimmer ebenso, der Abend wurde in Sekunden dunkel. Jetzt gilt "raffer" wie "zeit" erst nach dem Wechsel.
rem 1 Steuerhaus, HalligHooge bauen (Renderer unveraendert seit L6c3); 2 RegieCheck; 3 AusgabeCheck (Pruefsteine 1-5, 7);
rem 4 Video 1080p mit Ton (-Dausgabe.flotte=an: Szenenzeit und Schiffe je Filmsekunde im Status); 5 AusgabeCheck --datei;
rem 6 Tonspur (ffprobe); 7 Standbilder und Tonspur aus dem Video (S8e_pruef\v4_t*.png, v4_ton.wav).
rem Ausgaben: HalligHooge\docs\status\Status_L6c4_*.txt; HalligHooge\docs\video\S8e_Seegang.mp4 (die Fassung aus L6c2 wird
rem ueberschrieben; sie war falsch, Befund 15).
rem Erwartete Dauer: rund 65 min, hoechstens 90 min [geschaetzt: Probe L6c3 0,40 s je Bild in 720p ohne die Vorlaeufe; 1080p
rem rund 0,65 s; fuenf Vorlaeufe rund 8 min]. Gestartet von Claude per Explorer.
rem Anhalten zwischen zwei Schritten: eine Datei STOPP.txt neben dieses Skript legen.
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0.."

set "P=C:\Users\Dan40810\Documents\NetBeansProjects"
set "HH=%P%\HalligHooge"
set "SH=%P%\Steuerhaus"
set "RE=%P%\Renderer"
set "ST=%HH%\docs\status"
set "VI=%HH%\docs\video"
set "SZ=%HH%\src\szenen\Hooge.szenen"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
set "R=java %U% --enable-native-access=ALL-UNNAMED -Xmx16g -cp dist\Renderer.jar"
set "V=-Dcore.gpu.validierung=true"
set "J=java %U% --enable-native-access=ALL-UNNAMED -Xmx12g -cp dist\HalligHooge.jar"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"

if not exist "%ST%" mkdir "%ST%"
if not exist "%VI%" mkdir "%VI%"
set "UE=%ST%\Status_L6c4_.txt"
set "L1=%ST%\Status_L6c4_1.txt"
set "L2=%ST%\Status_L6c4_2.txt"
set "L3=%ST%\Status_L6c4_3.txt"
set "L4=%ST%\Status_L6c4_4.txt"
set "E=0"
set "EF=0"
>"%UE%" echo Lauf L6c4 (S8e2 Video), gestartet %DATE% %TIME%
>"%L1%" echo Status_L6c4_1: Steuerhaus, HalligHooge bauen
>"%L2%" echo Status_L6c4_2: RegieCheck, AusgabeCheck
>"%L3%" echo Status_L6c4_3: Video Seegang
>"%L4%" echo Status_L6c4_4: Pruefung der Datei, Tonspur, Standbilder

set "L=%L1%"
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
where ffmpeg >>"%L1%" 2>&1
where ffprobe >>"%L1%" 2>&1

pushd "%HH%"
set "N=2 RegieCheck"
set "L=%L2%"
set "C=%J% com.dan.steuerhaus.demo.RegieCheck --manifest src\szenen\Hooge.steuerhaus"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"
set "N=3 AusgabeCheck (Pruefsteine 1-5, 7)"
set "C=%J% com.dan.steuerhaus.demo.AusgabeCheck --manifest src\szenen\Hooge.steuerhaus"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=4 Video: Seegang (1080p, 24 B/s, 8 Teilbilder, mit Ton)"
set "L=%L3%"
set "C=java %U% -Dausgabe.flotte=an --enable-native-access=ALL-UNNAMED -Xmx12g -cp dist\HalligHooge.jar com.dan.steuerhaus.ausgabe.Ausgabe --manifest src\szenen\Hooge.steuerhaus --regie "Seegang" --groesse 1080p --ziel %VI%\S8e_Seegang.mp4"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=5 AusgabeCheck --datei (Bilder, Groesse, Bildrate)"
set "L=%L4%"
set "C=%J% com.dan.steuerhaus.demo.AusgabeCheck --datei %VI%\S8e_Seegang.mp4"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"
set "N=6 Tonspur (ffprobe)"
set "C=ffprobe -v error -select_streams a:0 -show_entries stream=codec_name,sample_rate,channels,duration -of default=noprint_wrappers=1 %VI%\S8e_Seegang.mp4"
call :lauf
set "E=0"
popd
type "%VI%\S8e_Seegang.mp4.txt" >>"%L4%" 2>&1

set "PB=%VI%\S8e_pruef"
if not exist "%PB%" mkdir "%PB%"
>>"%UE%" echo [%DATE% %TIME%] Start  7 Standbilder und Tonspur
for %%T in (3 10 22 35 45 55 60 66 72 85 100 106 112 118 126 136 146 160) do (
  ffmpeg -v error -y -ss %%T -i "%VI%\S8e_Seegang.mp4" -frames:v 1 -vf scale=960:-1 "%PB%\v4_t%%T.png" >>"%L4%" 2>&1
)
ffmpeg -v error -y -i "%VI%\S8e_Seegang.mp4" -vn -ac 2 -ar 24000 "%PB%\v4_ton.wav" >>"%L4%" 2>&1
>>"%UE%" echo [%DATE% %TIME%] Ende   7 Standbilder und Tonspur

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
