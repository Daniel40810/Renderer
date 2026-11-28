@echo off
rem Seegang Lauf L6c2 Nachtrag (Seegang Werkbuch, Datei 104): Pruefbilder aus dem Video S8e_Seegang.mp4 (ffmpeg, 960 Bildpunkte breit) und die Tonspur als WAV (24 kHz),
rem fuer die Durchsicht durch Claude vor der Sichtpruefung (SF165). Aendert nichts am Video.
rem Ausgaben: HalligHooge\docs\video\S8e_pruef\t<Sekunde>.png, ton.wav und bilder.txt
rem Erwartete Dauer: unter 2 min. Gestartet von Claude per Explorer.
setlocal
set "VI=C:\Users\Dan40810\Documents\NetBeansProjects\HalligHooge\docs\video"
set "PB=%VI%\S8e_pruef"
if not exist "%PB%" mkdir "%PB%"
set "LOG=%PB%\bilder.txt"
>"%LOG%" echo Pruefbilder aus S8e_Seegang.mp4, gestartet %DATE% %TIME%
for %%T in (3 10 22 35 45 60 70 85 100 106 112 118 126 136 146 160) do (
  ffmpeg -v error -y -ss %%T -i "%VI%\S8e_Seegang.mp4" -frames:v 1 -vf scale=960:-1 "%PB%\t%%T.png" >>"%LOG%" 2>&1
  >>"%LOG%" echo t=%%T
)
ffmpeg -v error -y -i "%VI%\S8e_Seegang.mp4" -vn -ac 2 -ar 24000 "%PB%\ton.wav" >>"%LOG%" 2>&1
>>"%LOG%" echo Ton
>>"%LOG%" echo FERTIG %DATE% %TIME%
endlocal
