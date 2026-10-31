# com.dan.tree.europeanbeech – Rotbuche

Rotbuche (*Fagus sylvatica*) aus dem Procedural Vegetation Editor von Unreal (Fab/Megascans, `Assets/Trees/tree_european_beech_01_usd.zip` im Projekt Nature), umgewandelt mit `tools/tree/usd2dtree.py` aus dem Projekt Renderer (seit Timmendorf Phase 19 mit zweistufigen Zweigen). Das Paket ist eine Voreinstellung für den Kern `com.dan.tree` (siehe dort).

**Stand: Timmendorf Phase 19** (F24 A).

| Variante | Datei | Höhe | Gelenke | Zweigarten (Knoten, Blätter) | Zweige gesetzt |
|---|---|---|---|---|---|
| A alt | `beech_a.dtree` (2,2 MB) | 26,6 m | 737 | 3 Äste (155/183, 193/301, 128/192) | 651 |
| B hoch | `beech_b.dtree` (1,3 MB) | 23,6 m | 705 | dieselben | 742 |
| C jung | `beech_c.dtree` (0,3 MB) | 8,7 m | 95 | 3 Zweige (41/53, 38/36, 20/15) | 297 |
| D jung mit Ästen | `beech_d.dtree` (0,5 MB) | 9,7 m | 118 | 2 Äste und 3 Zweige | 91 |

Jahreszeit [G nach der Phänologie des Deutschen Wetterdienstes für Schleswig-Holstein]: Blattentfaltung um Tag 121 (1. Mai), frisch hellgrün, nach 30 Tagen dunkel; Verfärbung ab Tag 288 über Gelb zu Kupferbraun, kahl ab Tag 318. Rinde glatt, silbergrau. Biegsamkeit: Stamm 0,6, Äste 0,85, Zittern 0,7.

```java
TreeSpecies buche = EuropeanBeech.species();   // jeder Aufruf eine frische, änderbare Art
TreeMesh m = buche.bake(2, 1);                // C jung, Stufe 1
```

Demo und Selbsttest wie bei der Schwarzerle mit dem Schlüssel `rotbuche`: `TreeDemo rotbuche`, `TreeGlDemo rotbuche`, `TreeCheck rotbuche` (endet mit „Alles in Ordnung.“).

## Neu umwandeln

```
unzip tree_european_beech_01_usd.zip -d /tmp/buche
for v in A B C D; do python3 -I tools/tree/usd2dtree.py /tmp/buche/Tree_European_Beech_01_$v.USD \
  src/com/dan/tree/europeanbeech/beech_$(echo $v | tr A-Z a-z).dtree --name "Rotbuche $v"; done
```
