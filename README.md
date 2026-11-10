# bTUNES

Einfacher Musik-Player für Android, der die Musik auf dem Handy abspielt – mit einem Plattenspieler als Startansicht: Das Albumcover liegt als Etikett auf der Schallplatte, die sich beim Abspielen dreht.

- Bibliothek nach Alben (mit Cover), Interpreten, Ordnern oder Titeln
- Suche, Zufall, Wiederholen, eigene Playlists
- Spielt im Hintergrund weiter, Steuerung in Benachrichtigung und Sperrbildschirm
- Reiter **Zeitung**: PDF-Ausgaben (z. B. DIE ZEIT) aus lokalen Ordnern – die App sucht auf Wunsch selbst nach Ordnern mit PDFs
  - Seitenansicht: Doppeltipp zoomt auf die Spalte, „Spalte ›“ springt weiter; Zoom mit zwei Fingern (PC: Strg + Mausrad)
  - Lesemodus: Artikel als Fließtext mit großer Schrift, hell/dunkel, Inhaltsverzeichnis mit Suche
  - Am Handy braucht die App dafür einmalig den Zugriff „Alle Dateien verwalten“

**Installieren:** Unter *Releases* → `apk-latest` die Datei `bTUNES.apk` aufs Handy laden und öffnen.

Die App wird bei jeder Änderung automatisch über GitHub Actions gebaut (`.github/workflows/android.yml`).
`index.html` lässt sich auch im Browser öffnen – dann läuft ein Vorführmodus mit Beispielalben.

## Linux-Version

Unter *Releases* → `linux-latest`: `bTUNES-….AppImage` (ausführbar machen und starten) oder `bTUNES-….rpm` für Fedora (`sudo dnf install ./bTUNES-*.rpm`).
Die Musikordner wählst du in der Bibliothek über das Ordner-Symbol.
