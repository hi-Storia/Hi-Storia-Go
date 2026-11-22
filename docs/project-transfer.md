# Esportazione e importazione dei progetti

Hi-Storia Go può salvare l'intero progetto corrente in un unico file `nome-progetto.historia.zip`
e importarlo su un altro smartphone Android, senza copiare a mano file o cartelle.

Il codice si trova in `android/app/src/main/java/it/historia/player/data/project/`
(`ProjectTransferManager` coordina il flusso; la UI è in `ProjectScreen` /
`ProjectTransferViewModel`).

## Procedura utente

### Esportare

1. Apri l'area admin (tocchi ripetuti sul logo) → **Progetto**.
2. In fondo alla schermata, sezione **Trasferimento progetto**, tocca **Esporta progetto**.
3. Scegli dove salvare il file (memoria del telefono, scheda SD, Drive, chiavetta USB…). Il nome
   proposto è ricavato dal nome del bene culturale, ad esempio `castello-di-santa-severa.historia.zip`.
4. Al termine compare un messaggio con il numero di file audio e la dimensione esportata.
   Se l'operazione fallisce (errore, spazio insufficiente) il file parziale viene eliminato,
   quando il provider di archiviazione lo consente. Se annulli la scelta non viene creato nulla.

### Importare

1. Copia il file `.historia.zip` sul secondo smartphone (cavo USB, Drive, Bluetooth, chiavetta…).
2. Apri **Progetto** → **Importa progetto** e seleziona il file.
3. L'app verifica l'archivio e mostra un riepilogo: nome, scuola, attivatori, tipologie e lingue
   (con le serie disattivate), numero e dimensione dei file audio, versione dell'app di origine
   ed eventuali avvisi (per esempio un'associazione a un file che non esisteva già all'origine).
4. Tocca **Sostituisci progetto** per confermare oppure **Annulla**. La conferma sostituisce
   integralmente il progetto corrente, compresi gli audio caricati: se vuoi conservarlo,
   esportalo prima.
5. Durante l'importazione la riproduzione viene fermata; al termine il player si ricarica con il
   nuovo progetto, subito utilizzabile.

Non serve alcun permesso di accesso alla memoria: lettura e scrittura passano dallo Storage Access
Framework (`CreateDocument` / `OpenDocument`).

## Cosa contiene il progetto

| Dato | Origine | Nell'archivio |
| --- | --- | --- |
| Nome bene culturale, scuola, numero attivatori | `ProjectSettingsStore` | `project.json` |
| Tipologie (predefinite e personalizzate) con etichette | `ProjectSettingsStore` | `profiles` |
| Lingue per tipologia (predefinite e aggiunte) con etichette | `ProjectSettingsStore` | `profiles[].customLanguages`, `languages` |
| Serie attive e **disattivate** | `ProjectSettingsStore` | `series[].enabled` |
| Associazioni segnale → traccia, nomi attivatori, volumi | `SignalMappingStore` | `series[].signals` |
| Tracce standard nascoste dall'utente | `UserAudioStore` | `series[].hiddenDefaultTracks` |
| Traccia idle, esplorazione, presentazione per serie | `Idle/Exploration/PresentationSettingsStore` | `series[].idle/exploration/presentation` |
| Timeout idle ed esplorazione | `Idle/ExplorationSettingsStore` | `idle`, `exploration` |
| Musica di sottofondo | `BackgroundMusicSettingsStore` | `backgroundMusic` |
| Audio caricati dall'utente | `filesDir/audio/<progetto>/…` | `audio/…` |

Vengono esportate **tutte** le serie del progetto, non solo quella selezionata, con i rispettivi
audio anche quando la serie è disattivata.

**Non** vengono esportati: diagnostica e log, soglia del sensore e altre impostazioni hardware
della scheda Arduino, stato USB, serie selezionata al momento, preferenze non legate al progetto.
Il formato non è un dump delle SharedPreferences: ogni campo ha uno schema esplicito e validato.

## Formato dell'archivio (schema versione 1)

```text
nome-progetto.historia.zip
├── manifest.json                                    sempre la prima voce
├── project.json                                     configurazione completa
└── audio/
    ├── series/<profilo>/<lingua>/tracks/<file>      tracce degli attivatori
    ├── series/<profilo>/<lingua>/idle/<file>        traccia idle
    ├── series/<profilo>/<lingua>/exploration/<file> introduzione esplorazione
    ├── series/<profilo>/<lingua>/presentation/<file>
    └── background/<file>                            musica di sottofondo
```

I percorsi `audio/series/…` corrispondono 1:1 a `filesDir/audio/<deviceId>/…` sullo smartphone:
identificativi di tipologie e lingue (`adult`, `custom_profile_1`, `custom_lang_2`…) e nomi dei
file sono conservati, così le associazioni restano valide senza riscritture.

### `manifest.json`

```json
{
  "format": "it.historia.go.project",
  "schemaVersion": 1,
  "createdAt": "2026-11-22T10:15:00Z",
  "generator": { "app": "Hi-Storia Go", "applicationId": "it.historia.go", "versionName": "0.1.0-alpha", "versionCode": 1 },
  "project": { "deviceId": "default_project", "name": "…", "school": "…", "trackCount": 6,
               "profileCount": 2, "seriesCount": 4, "enabledSeriesCount": 3 },
  "audioFileCount": 11,
  "totalAudioBytes": 48123456,
  "files": [ { "path": "audio/series/adult/it/tracks/01.mp3", "size": 1234567, "sha256": "…" } ],
  "assetDependencies": [ "audio/default/tracks/00.mp3", "audio/idle/default/idle.mp3" ]
}
```

### `project.json`

```json
{
  "schemaVersion": 1,
  "deviceId": "default_project",
  "name": "Castello di Santa Severa",
  "school": "Liceo Galilei",
  "trackCount": 6,
  "profiles": [
    { "id": "adult", "label": "Adulti", "custom": false, "customLanguages": ["en"] },
    { "id": "custom_profile_1", "label": "Bambini", "custom": true, "customLanguages": ["custom_lang_1", "it"] }
  ],
  "languages": { "it": "Italiano", "en": "English", "custom_lang_1": "Napoletano" },
  "series": [
    {
      "profileId": "adult", "language": "it", "enabled": true,
      "signals": [ { "signal": 1, "track": "01-ingresso.mp3", "label": "Ingresso", "volume": 80 } ],
      "hiddenDefaultTracks": [],
      "idle": { "useDefaultTrack": false, "track": "attesa.mp3" },
      "exploration": { "useDefaultTrack": true, "track": null },
      "presentation": { "track": null }
    }
  ],
  "idle": { "timeoutMinutes": 12 },
  "exploration": { "timeoutMinutes": 7 },
  "backgroundMusic": { "enabled": true, "useDefaultTrack": false, "track": "musica.mp3",
                       "onlyDuringTracks": true, "fullVolumePercent": 75, "duckedVolumePercent": 12.5 }
}
```

Le lingue di una tipologia predefinita sono quelle del catalogo dell'app più `customLanguages`;
quelle di una tipologia personalizzata sono solo `customLanguages`.

### Audio inclusi nell'APK

Gli audio standard (`assets/audio/default/…`, idle, sottofondo ed esplorazione predefiniti) non
vengono copiati nell'archivio: il manifest li elenca in `assetDependencies`. All'importazione
l'app controlla che esistano nella propria versione; se manca qualcosa (per esempio un asset
rimosso in una versione futura) lo segnala tra gli avvisi del riepilogo, senza bloccare.

Le associazioni vengono risolte come nel player (`TrackResolver`): oltre alle cartelle delle serie,
l'esportazione include anche i file di altre cartelle del progetto raggiunti dai percorsi di
ripiego (per esempio `adult/<lingua>`). File di serie rimosse che nessuna associazione usa non
fanno parte del progetto e non vengono esportati.

## Sicurezza e limiti

L'importazione legge l'archivio in streaming e rifiuta l'intero file al primo problema:

- `manifest.json` deve essere la prima voce; ogni file audio successivo deve esservi dichiarato
  con dimensione e SHA-256, verificati durante l'estrazione. Voci non dichiarate, duplicate o
  mancanti invalidano l'archivio.
- **ZIP Slip / path traversal**: rifiutati nomi con `..`, `.`, segmenti vuoti, percorsi assoluti,
  backslash, `:`; ammessa solo la struttura descritta sopra, con identificativi `[A-Za-z0-9_-]`
  (max 64) e nomi file `[A-Za-z0-9._ -]` (max 96) con estensione audio supportata
  (`mp3 m4a aac wav ogg opus`). Il percorso canonico di destinazione deve restare nell'area
  temporanea. Su Android 14+ il sistema blocca già i nomi con `..`.
- **Decompression bomb**: l'estrazione si interrompe appena un file supera la dimensione dichiarata.
- Limiti: 2 000 voci; 4 MB per `manifest.json` e `project.json`; 300 MB per file audio; 4 GB di
  audio in totale; prima di estrarre serve spazio libero pari agli audio + 20 MB.
- Tutti i valori di `project.json` sono controllati per tipo e intervallo (attivatori 1–99,
  volumi 10–100, timeout idle 1–120 e esplorazione 1–30 minuti, volumi sottofondo 0–100…).
  Deve restare almeno una serie attiva.

Errori riconosciuti e mostrati all'utente: archivio corrotto o troncato, manifest non valido,
versione dello schema più recente di quella supportata ("aggiorna Hi-Storia Go"), progetto non
compatibile (configurazione o tipologia predefinita sconosciuta), file mancanti o alterati,
percorsi non sicuri, limiti superati, spazio insufficiente.

## Sostituzione sicura, backup e rollback

1. **Preparazione** (`prepareImport`): l'archivio viene validato ed estratto in
   `filesDir/project_transfer/staging/<id>`. Il progetto corrente non viene toccato; se l'utente
   annulla, o la validazione fallisce, l'area temporanea viene eliminata.
2. **Applicazione** (`applyImport`, dopo la conferma):
   1. si scrive un journal `project_transfer/import-journal.json` (fase `backup`);
   2. le chiavi di progetto di tutti gli store vengono salvate, tipizzate, in
      `project_transfer/backup/preferences.json`;
   3. la cartella audio del progetto viene spostata (rename atomico) in `project_transfer/backup/audio`;
   4. journal in fase `apply`; i nuovi audio vengono spostati dall'area temporanea, le chiavi di
      progetto di ogni store vengono eliminate e riscritte con un `commit` sincrono;
   5. verifica dei file installati, journal `committed`, eliminazione di backup, journal e area
      temporanea.
3. **Rollback**: qualsiasi errore nei passi 2.2–2.5 ripristina subito cartella audio e preferenze
   dal backup. Se il processo viene terminato a metà, al successivo avvio (`App.onCreate`) il
   journal viene letto e il ripristino completato prima che il player legga i dati; vengono
   rimosse anche le aree temporanee rimaste.

Lo spostamento usa `rename` sullo stesso filesystem; se non è possibile, copia in una cartella
`.partial`, la rinomina e solo dopo elimina l'origine, così un'interruzione non lascia mai una
destinazione incompleta scambiata per valida.

Le chiavi eliminate sono solo quelle che ciascuno store dichiara appartenenti al progetto
(`projectSection(deviceId)`): nessun residuo del progetto precedente (tipologie, lingue o serie
rimosse) resta attivo, e le altre preferenze dell'app non vengono toccate.

## Compatibilità

- Android 8.0 (API 26) o successivo; nessuna dipendenza aggiunta all'APK (ZIP e JSON della
  piattaforma).
- `schemaVersion` cresce solo per modifiche incompatibili. Un'app legge gli archivi con versione
  minore o uguale alla propria e rifiuta quelli più recenti chiedendo di aggiornare.
- Il progetto è legato al `deviceId` del catalogo (`default_project`): entrambe le app devono
  conoscerlo. Le tipologie predefinite dell'archivio devono esistere nell'app che importa.
- Gli audio standard dipendono dalla versione dell'APK (vedi sopra).

## Limiti noti

- Esiste un solo progetto alla volta: l'importazione sostituisce quello corrente.
- I file audio vengono copiati così come sono; nessuna conversione o ricompressione.
- L'archivio non è cifrato né firmato: le impronte SHA-256 proteggono da corruzione accidentale,
  non da modifiche intenzionali (che restano comunque soggette a tutte le validazioni).
- Con alcuni provider cloud l'eliminazione del file parziale dopo un'esportazione fallita può non
  essere supportata: in quel caso il file incompleto va cancellato a mano (all'importazione verrebbe
  comunque rifiutato).

## Test automatici

`android/app/src/test/java/it/historia/player/data/project/` (Robolectric, store e filesystem reali):

- `ProjectTransferRoundTripTest`: round-trip esportazione → importazione con equivalenza del
  progetto; più tipologie e lingue con serie disattivata; audio diversi per serie e tipologia;
  mapping, nomi attivatori e volumi; idle, esplorazione, presentazione e sottofondo; struttura
  dell'archivio senza asset dell'APK; avvisi per riferimenti mancanti; pulizia dei temporanei.
- `ProjectImportFailureTest`: archivi corrotti, vuoti, troncati, file mancanti o alterati,
  manifest non valido, versione incompatibile, configurazione sconosciuta, percorsi ZIP malevoli,
  decompression bomb, limiti, spazio insufficiente, guasto in ogni fase dell'applicazione e
  processo terminato a metà: in tutti i casi il progetto esistente resta identico.
- `ProjectArchiveFormatTest`: nomi file suggeriti e regole sui percorsi.

```powershell
cd android
.\gradlew.bat :app:testDebugUnitTest
```

## Verifica manuale su due smartphone

1. **Telefono A**: configura un progetto con almeno due tipologie, una lingua aggiunta, una serie
   disattivata, audio caricati su più attivatori (con nomi e volumi diversi), una traccia idle,
   una di esplorazione, una di presentazione e una musica di sottofondo personalizzate.
2. Esporta il progetto in una cartella condivisa (Download, Drive o chiavetta OTG) e controlla il
   messaggio finale.
3. Prova anche l'annullamento della scelta della destinazione (nessun file creato).
4. **Telefono B**: configura un progetto diverso, poi importa l'archivio. Controlla che il
   riepilogo riporti nome, tipologie, lingue (con la serie disattivata) e numero di file.
5. Annulla una prima volta: il progetto di B deve restare invariato.
6. Importa confermando: verifica nella schermata Progetto nomi, tipologie, lingue e serie attive;
   nell'area Contenuti associazioni, nomi attivatori e volumi; in Smartphone timeout, idle,
   esplorazione e sottofondo. Riattiva la serie disattivata e controlla che abbia i suoi audio.
7. Con la scheda collegata via USB tocca gli attivatori di ogni serie, avvia presentazione ed
   esplorazione e attendi l'idle: tutto deve suonare come sul telefono A. Verifica che durante
   l'importazione la riproduzione in corso si sia fermata.
8. Prova un file non valido (uno ZIP qualsiasi, un `.historia.zip` troncato copiandone solo una
   parte): deve comparire un errore chiaro e il progetto corrente non deve cambiare.
