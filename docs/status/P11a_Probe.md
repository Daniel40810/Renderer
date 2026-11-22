# P11a – Probe Wellenfront (F112 A), Lauf A7: mit Dauerrate

## Teil 1: Kernel im Treiber

Gerät: AMD Radeon(TM) Graphics; VK_AMD_shader_info ja, VK_KHR_pipeline_executable_properties ja

Wellen: 32 bis 64 Fäden, vorgebbar ja

### /com/dan/render/reference/gpu/wf_luft_neu.comp, Wellen nach Treiber

SPIR-V 43112 Byte; Pipeline 0 in 0,1 s
AMD: VGPR 64 von 1024 (verfügbar 256), SGPR 62 von 2048, Scratch 0 Byte je Faden, LDS 0 Byte, Wellen je Recheneinheit 64 (Ergebnis 0)
Maschinencode 118225 Byte, 2197 Zeilen, Zugriffe auf Scratch rund 66; Datei docs\status\P11a_Befund_wf_luft_neu_comp.isa.txt
KHR: numUsedVgprs 64; numUsedSgprs 62; ldsSizePerLocalWorkGroup 65536; ldsUsageSizeInBytes 0; scratchMemUsageInBytes 0; 

### /com/dan/render/reference/gpu/wf_luft_schnitt.comp, Wellen nach Treiber

SPIR-V 17436 Byte; Pipeline 0 in 0,0 s
AMD: VGPR 56 von 1024 (verfügbar 256), SGPR 40 von 2048, Scratch 0 Byte je Faden, LDS 0 Byte, Wellen je Recheneinheit 64 (Ergebnis 0)
Maschinencode 42670 Byte, 779 Zeilen, Zugriffe auf Scratch rund 20; Datei docs\status\P11a_Befund_wf_luft_schnitt_comp.isa.txt
KHR: numUsedVgprs 56; numUsedSgprs 40; ldsSizePerLocalWorkGroup 65536; ldsUsageSizeInBytes 0; scratchMemUsageInBytes 0; 

### /com/dan/render/reference/gpu/wf_luft_flug.comp, Wellen nach Treiber

SPIR-V 29392 Byte; Pipeline 0 in 0,0 s
AMD: VGPR 192 von 1024 (verfügbar 256), SGPR 48 von 2048, Scratch 0 Byte je Faden, LDS 0 Byte, Wellen je Recheneinheit 64 (Ergebnis 0)
Maschinencode 102897 Byte, 1892 Zeilen, Zugriffe auf Scratch rund 34; Datei docs\status\P11a_Befund_wf_luft_flug_comp.isa.txt
KHR: numUsedVgprs 192; numUsedSgprs 48; ldsSizePerLocalWorkGroup 65536; ldsUsageSizeInBytes 0; scratchMemUsageInBytes 0; 

### /com/dan/render/reference/gpu/wf_luft_auftrag.comp, Wellen nach Treiber

SPIR-V 32964 Byte; Pipeline 0 in 0,0 s
AMD: VGPR 128 von 1024 (verfügbar 256), SGPR 50 von 2048, Scratch 0 Byte je Faden, LDS 0 Byte, Wellen je Recheneinheit 64 (Ergebnis 0)
Maschinencode 80748 Byte, 1477 Zeilen, Zugriffe auf Scratch rund 24; Datei docs\status\P11a_Befund_wf_luft_auftrag_comp.isa.txt
KHR: numUsedVgprs 128; numUsedSgprs 50; ldsSizePerLocalWorkGroup 65536; ldsUsageSizeInBytes 0; scratchMemUsageInBytes 0; 

### /com/dan/render/reference/gpu/wf_luft_ereignis.comp, Wellen nach Treiber

SPIR-V 71556 Byte; Pipeline 0 in 0,0 s
AMD: VGPR 112 von 1024 (verfügbar 256), SGPR 36 von 2048, Scratch 0 Byte je Faden, LDS 0 Byte, Wellen je Recheneinheit 64 (Ergebnis 0)
Maschinencode 195150 Byte, 3608 Zeilen, Zugriffe auf Scratch rund 52; Datei docs\status\P11a_Befund_wf_luft_ereignis_comp.isa.txt
KHR: numUsedVgprs 112; numUsedSgprs 36; ldsSizePerLocalWorkGroup 65536; ldsUsageSizeInBytes 0; scratchMemUsageInBytes 0; 

### /com/dan/render/reference/gpu/wf_ebenen_neu.comp, Wellen nach Treiber

SPIR-V 43588 Byte; Pipeline 0 in 0,1 s
AMD: VGPR 64 von 1024 (verfügbar 256), SGPR 62 von 2048, Scratch 0 Byte je Faden, LDS 0 Byte, Wellen je Recheneinheit 64 (Ergebnis 0)
Maschinencode 119316 Byte, 2215 Zeilen, Zugriffe auf Scratch rund 71; Datei docs\status\P11a_Befund_wf_ebenen_neu_comp.isa.txt
KHR: numUsedVgprs 64; numUsedSgprs 62; ldsSizePerLocalWorkGroup 65536; ldsUsageSizeInBytes 0; scratchMemUsageInBytes 0; 

### /com/dan/render/reference/gpu/wf_ebenen_schnitt.comp, Wellen nach Treiber

SPIR-V 23652 Byte; Pipeline 0 in 0,0 s
AMD: VGPR 72 von 1024 (verfügbar 256), SGPR 52 von 2048, Scratch 0 Byte je Faden, LDS 0 Byte, Wellen je Recheneinheit 64 (Ergebnis 0)
Maschinencode 64001 Byte, 1178 Zeilen, Zugriffe auf Scratch rund 26; Datei docs\status\P11a_Befund_wf_ebenen_schnitt_comp.isa.txt
KHR: numUsedVgprs 72; numUsedSgprs 52; ldsSizePerLocalWorkGroup 65536; ldsUsageSizeInBytes 0; scratchMemUsageInBytes 0; 

### /com/dan/render/reference/gpu/wf_ebenen_flug.comp, Wellen nach Treiber

SPIR-V 29548 Byte; Pipeline 0 in 0,0 s
AMD: VGPR 192 von 1024 (verfügbar 256), SGPR 48 von 2048, Scratch 0 Byte je Faden, LDS 0 Byte, Wellen je Recheneinheit 64 (Ergebnis 0)
Maschinencode 103127 Byte, 1896 Zeilen, Zugriffe auf Scratch rund 34; Datei docs\status\P11a_Befund_wf_ebenen_flug_comp.isa.txt
KHR: numUsedVgprs 192; numUsedSgprs 48; ldsSizePerLocalWorkGroup 65536; ldsUsageSizeInBytes 0; scratchMemUsageInBytes 0; 

### /com/dan/render/reference/gpu/wf_ebenen_auftrag.comp, Wellen nach Treiber

SPIR-V 33928 Byte; Pipeline 0 in 0,0 s
AMD: VGPR 128 von 1024 (verfügbar 256), SGPR 48 von 2048, Scratch 0 Byte je Faden, LDS 0 Byte, Wellen je Recheneinheit 64 (Ergebnis 0)
Maschinencode 86158 Byte, 1577 Zeilen, Zugriffe auf Scratch rund 24; Datei docs\status\P11a_Befund_wf_ebenen_auftrag_comp.isa.txt
KHR: numUsedVgprs 128; numUsedSgprs 48; ldsSizePerLocalWorkGroup 65536; ldsUsageSizeInBytes 0; scratchMemUsageInBytes 0; 

### /com/dan/render/reference/gpu/wf_ebenen_ereignis.comp, Wellen nach Treiber

SPIR-V 90500 Byte; Pipeline 0 in 0,0 s
AMD: VGPR 128 von 1024 (verfügbar 256), SGPR 44 von 2048, Scratch 0 Byte je Faden, LDS 0 Byte, Wellen je Recheneinheit 64 (Ergebnis 0)
Maschinencode 257341 Byte, 4740 Zeilen, Zugriffe auf Scratch rund 58; Datei docs\status\P11a_Befund_wf_ebenen_ereignis_comp.isa.txt
KHR: numUsedVgprs 128; numUsedSgprs 44; ldsSizePerLocalWorkGroup 65536; ldsUsageSizeInBytes 0; scratchMemUsageInBytes 0; 


## Teil 2: Wellenfront gegen den großen Kernel (dieselben Zufallszahlen, 2 048 Plätze)

- Himmel über der Kugel, Sonne 40° (luft): großer Kernel 0,05500 ± 0,00090, Wellenfront 0,05578 ± 0,00090; 31 Runden; NaN 0, abgebrochen 0
- Ofentest ohne Wasser (Luft und Boden) (luft): großer Kernel 1,00013 ± 0,00225, Wellenfront 0,99986 ± 0,00231; 255 Runden; NaN 0, abgebrochen 0
- Ofentest, glattes Wasser über Grund (ebenen): großer Kernel 1,00888 ± 0,00882, Wellenfront 1,00888 ± 0,00882; 4095 Runden; NaN 0, abgebrochen 0
- Raues Wasser über Grund, Sonne 40°, Blick 30° hinab (ebenen): großer Kernel 0,02423 ± 0,00635, Wellenfront 0,02145 ± 0,00621; 511 Runden; NaN 0, abgebrochen 0

## Teil 3: Pfade/s auf der Karte (Wellenfront)

- Himmel über der Kugel, Sonne 40° (luft), 16384 Plätze × 64 Pfade: 0,24 s auf der Karte, 447 Runden in 11 Abschickungen, längste 53 ms: im Ganzen 4,39e+06 Pfade/s, im Dauerbetrieb 4,99e+06 Pfade/s; NaN 0, abgebrochen 0
- Himmel über der Kugel, Sonne 40° (luft), 65536 Plätze × 64 Pfade: 0,88 s auf der Karte, 383 Runden in 23 Abschickungen, längste 51 ms: im Ganzen 4,74e+06 Pfade/s, im Dauerbetrieb 5,24e+06 Pfade/s; NaN 0, abgebrochen 0
- Ofentest ohne Wasser (Luft und Boden) (luft), 16384 Plätze × 64 Pfade: 0,28 s auf der Karte, 3199 Runden in 15 Abschickungen, längste 45 ms: im Ganzen 3,71e+06 Pfade/s, im Dauerbetrieb 1,05e+07 Pfade/s; NaN 0, abgebrochen 0
- Ofentest ohne Wasser (Luft und Boden) (luft), 65536 Plätze × 64 Pfade: 0,74 s auf der Karte, 3439 Runden in 27 Abschickungen, längste 45 ms: im Ganzen 5,67e+06 Pfade/s, im Dauerbetrieb 1,18e+07 Pfade/s; NaN 0, abgebrochen 0
- Ofentest, glattes Wasser über Grund (ebenen), 16384 Plätze × 16 Pfade: 1,91 s auf der Karte, 16127 Runden in 43 Abschickungen, längste 69 ms: im Ganzen 1,37e+05 Pfade/s, im Dauerbetrieb 1,98e+05 Pfade/s; NaN 0, abgebrochen 0
- Ofentest, glattes Wasser über Grund (ebenen), 65536 Plätze × 16 Pfade: 7,39 s auf der Karte, 15039 Runden in 142 Abschickungen, längste 66 ms: im Ganzen 1,42e+05 Pfade/s, im Dauerbetrieb 2,00e+05 Pfade/s; NaN 0, abgebrochen 0
- Raues Wasser über Grund, Sonne 40°, Blick 30° hinab (ebenen), 16384 Plätze × 32 Pfade: 0,58 s auf der Karte, 1791 Runden in 21 Abschickungen, längste 46 ms: im Ganzen 9,10e+05 Pfade/s, im Dauerbetrieb 1,19e+06 Pfade/s; NaN 0, abgebrochen 0
- Raues Wasser über Grund, Sonne 40°, Blick 30° hinab (ebenen), 65536 Plätze × 32 Pfade: 2,07 s auf der Karte, 1903 Runden in 57 Abschickungen, längste 54 ms: im Ganzen 1,01e+06 Pfade/s, im Dauerbetrieb 1,29e+06 Pfade/s; NaN 0, abgebrochen 0

## Teil 4: Stufe 1 auf dem Prozessor (16 Fäden, je rund 4 s) und Verhältnis

- Himmel über der Kugel, Sonne 40°: 3215488 Pfade in 4,0 s, 8,04e+05 Pfade/s
  Karte / Stufe 1: im Dauerbetrieb 6,52, im Ganzen 5,90
- Ofentest ohne Wasser (Luft und Boden): 15036608 Pfade in 4,0 s, 3,76e+06 Pfade/s
  Karte / Stufe 1: im Dauerbetrieb 3,13, im Ganzen 1,51
- Ofentest, glattes Wasser über Grund: 372416 Pfade in 4,0 s, 9,29e+04 Pfade/s
  Karte / Stufe 1: im Dauerbetrieb 2,15, im Ganzen 1,53
- Raues Wasser über Grund, Sonne 40°, Blick 30° hinab: 543104 Pfade in 4,0 s, 1,36e+05 Pfade/s
  Karte / Stufe 1: im Dauerbetrieb 9,54, im Ganzen 7,47
