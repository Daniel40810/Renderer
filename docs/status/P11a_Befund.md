# P11a – Befund zur Geschwindigkeit der Referenz Stufe 2

## Teil 1: Kernel im Treiber

Gerät: AMD Radeon(TM) Graphics; VK_AMD_shader_info ja, VK_KHR_pipeline_executable_properties ja

Wellen: 32 bis 64 Fäden, vorgebbar ja

### /com/dan/render/reference/gpu/pfad_ebenen.comp, Wellen nach Treiber

SPIR-V 172452 Byte; Pipeline 0 in 0,0 s
AMD: VGPR 256 von 1024 (verfügbar 256), SGPR 103 von 2048, Scratch 10800 Byte je Faden, LDS 0 Byte, Wellen je Recheneinheit 64 (Ergebnis 0)
Maschinencode 2662156 Byte, 45175 Zeilen, Zugriffe auf Scratch rund 11109; Datei docs\status\P11a_Befund_pfad_ebenen_comp.isa.txt
KHR: numUsedVgprs 256; numUsedSgprs 103; ldsSizePerLocalWorkGroup 65536; ldsUsageSizeInBytes 0; scratchMemUsageInBytes 10800; 

### /com/dan/render/reference/gpu/pfad_ebenen.comp, Wellen zu 32

SPIR-V 172452 Byte; Pipeline 0 in 6,4 s
AMD: VGPR 256 von 1024 (verfügbar 256), SGPR 94 von 2048, Scratch 10784 Byte je Faden, LDS 0 Byte, Wellen je Recheneinheit 64 (Ergebnis 0)
Maschinencode 2488286 Byte, 42227 Zeilen, Zugriffe auf Scratch rund 10124; Datei docs\status\P11a_Befund_pfad_ebenen_comp_w32.isa.txt
KHR: numUsedVgprs 256; numUsedSgprs 94; ldsSizePerLocalWorkGroup 65536; ldsUsageSizeInBytes 0; scratchMemUsageInBytes 10784; 

### /com/dan/render/reference/gpu/pfad_luft.comp, Wellen nach Treiber

SPIR-V 150044 Byte; Pipeline 0 in 6,4 s
AMD: VGPR 256 von 1024 (verfügbar 256), SGPR 103 von 2048, Scratch 6256 Byte je Faden, LDS 0 Byte, Wellen je Recheneinheit 64 (Ergebnis 0)
Maschinencode 1550101 Byte, 26695 Zeilen, Zugriffe auf Scratch rund 5835; Datei docs\status\P11a_Befund_pfad_luft_comp.isa.txt
KHR: numUsedVgprs 256; numUsedSgprs 103; ldsSizePerLocalWorkGroup 65536; ldsUsageSizeInBytes 0; scratchMemUsageInBytes 6256; 

### /com/dan/render/reference/gpu/pfad_luft.comp, Wellen zu 32

SPIR-V 150044 Byte; Pipeline 0 in 4,5 s
AMD: VGPR 256 von 1024 (verfügbar 256), SGPR 92 von 2048, Scratch 6256 Byte je Faden, LDS 0 Byte, Wellen je Recheneinheit 64 (Ergebnis 0)
Maschinencode 1558181 Byte, 27053 Zeilen, Zugriffe auf Scratch rund 5221; Datei docs\status\P11a_Befund_pfad_luft_comp_w32.isa.txt
Teil 1 abgebrochen: java.lang.OutOfMemoryError: Out of stack space.
## Teil 2: einzelne Aufrufe (ms auf der Karte, je zweimal)

- Wasserflug (2 m, ohne Pfadverfolger): 1024 × 16: 3,0 / 3,7 ms (4,44e+06 Pfade/s); 16384 × 16: 38,0 / 38,3 ms (6,84e+06 Pfade/s); 65536 × 16: 148,1 / 147,7 ms (7,10e+06 Pfade/s); 
- Himmel über der Kugel, Sonne 40°: 64 × 1: 8,0 / 7,2 ms (8,91e+03 Pfade/s); 256 × 1: 10,9 / 11,9 ms (2,16e+04 Pfade/s); 1024 × 1: 20,5 / 22,4 ms (4,57e+04 Pfade/s); 4096 × 1: 77,2 / 74,8 ms (5,48e+04 Pfade/s); 16384 × 1: 270,6 / 273,1 ms (6,00e+04 Pfade/s); 65536 × 1: 1068,5 / 1070,1 ms (6,12e+04 Pfade/s); abgebrochen (über 400 ms)
- Ofentest, glattes Wasser über Grund: 64 × 1: 335,9 / 279,9 ms (229 Pfade/s); 256 × 1: 451,6 / 467,0 ms (548 Pfade/s); abgebrochen (über 400 ms)
- Ofentest ohne Wasser (Luft und Boden): 64 × 1: 49,7 / 3,3 ms (1,96e+04 Pfade/s); 256 × 1: 50,7 / 4,8 ms (5,28e+04 Pfade/s); 1024 × 1: 56,3 / 11,0 ms (9,32e+04 Pfade/s); 4096 × 1: 82,1 / 198,6 ms (2,06e+04 Pfade/s); 

Mit pfad_luft.comp (ohne Wasser):

- Himmel über der Kugel, Sonne 40° (pfad_luft): 1024 × 1: 14,9 / 11,3 ms (9,09e+04 Pfade/s); 4096 × 1: 43,8 / 42,5 ms (9,65e+04 Pfade/s); 16384 × 1: 152,8 / 154,1 ms (1,06e+05 Pfade/s); 65536 × 1: 601,3 / 601,3 ms (1,09e+05 Pfade/s); abgebrochen (über 400 ms)
- Ofentest ohne Wasser (pfad_luft): 1024 × 1: 29,7 / 5,7 ms (1,80e+05 Pfade/s); 4096 × 1: 45,7 / 107,4 ms (3,81e+04 Pfade/s); 16384 × 1: 549,3 / 239,5 ms (6,84e+04 Pfade/s); abgebrochen (über 400 ms)

## Teil 3: Stufe 1 auf dem Prozessor (16 Fäden, je rund 4 s)

- Himmel über der Kugel, Sonne 40°: 3346624 Pfade in 4,0 s, 8,36e+05 Pfade/s
- Ofentest, glattes Wasser über Grund: 291456 Pfade in 4,0 s, 7,26e+04 Pfade/s
- Ofentest ohne Wasser (Luft und Boden): 16811648 Pfade in 4,0 s, 4,20e+06 Pfade/s
