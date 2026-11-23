# Distribuzione dell'APK

Hi-Storia Go viene distribuita come APK firmato, scaricabile dalla pagina
[GitHub Releases](https://github.com/hi-Storia/Hi-Storia-Go/releases) e installabile su qualsiasi
smartphone Android 8.0+ **senza modalità sviluppatore e senza debug USB**.

## Perché serve un APK release firmato

| | Debug (`assembleDebug`) | Release (`assembleRelease`) |
| --- | --- | --- |
| Firma | chiave di debug generata da Android Studio su ogni PC | chiave di release del progetto |
| Debuggable | sì | no |
| Uso | sviluppo, via cavo/Android Studio | distribuzione pubblica |

Android installa solo APK firmati e accetta un aggiornamento solo se è firmato con **la stessa
chiave** della versione installata e ha un `versionCode` maggiore. Per questo tutte le versioni
pubbliche devono essere firmate sempre con la stessa chiave di release.

Senza credenziali la build release produce `app-release-unsigned.apk`, che **non è installabile**.

## 1. Creare la chiave di firma (una sola volta)

Da PowerShell, con il `keytool` incluso in Android Studio. Le password vengono chieste in modo
interattivo e non finiscono nella cronologia:

```powershell
New-Item -ItemType Directory -Force "$HOME\historia-keys"
```

```powershell
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v -keystore "$HOME\historia-keys\historia-release.jks" -storetype PKCS12 -keyalg RSA -keysize 4096 -validity 10000 -alias historia-release
```

- Se Android Studio è installato in un'altra cartella (per esempio `Android Studio1`), adatta il
  percorso: serve un `jbr\bin\keytool.exe` esistente.
- Con il formato PKCS12 la password della chiave coincide con quella del keystore.
- **Conserva il file `.jks` e le password fuori dal repository**, con almeno una copia di backup
  sicura (per esempio un gestore di password e un supporto offline). Se la chiave va persa non è
  più possibile pubblicare aggiornamenti installabili sopra le versioni esistenti: gli utenti
  dovrebbero disinstallare e reinstallare (dopo aver esportato il progetto).
- `.gitignore` esclude `*.jks`, `*.keystore`, `*.p12`, `*.pk8` e `keystore.properties`.

## 2. Build firmata in locale (facoltativa)

Copia `android/keystore.properties.example` in `android/keystore.properties` (ignorato da git) e
compila percorso, alias e password. In alternativa imposta le variabili d'ambiente
`HISTORIA_KEYSTORE_FILE`, `HISTORIA_KEYSTORE_PASSWORD`, `HISTORIA_KEY_ALIAS`, `HISTORIA_KEY_PASSWORD`
(hanno la precedenza sul file).

```powershell
cd android
.\gradlew.bat :app:assembleRelease "-Phistoria.requireReleaseSigning=true"
```

Il risultato è `android/app/build/outputs/apk/release/app-release.apk`. Con
`-Phistoria.requireReleaseSigning=true` la build fallisce subito se la firma non è configurata,
invece di produrre un APK non firmato. Per controllare la firma:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\36.0.0\apksigner.bat" verify --verbose --print-certs app\build\outputs\apk\release\app-release.apk
```

## 3. Configurare GitHub (una sola volta)

Il workflow `.github/workflows/release.yml` firma l'APK sui server di GitHub usando dei *secrets*
cifrati: il keystore non entra mai nel repository.

1. *Settings → Environments → New environment* → nome `release`.
   Consigliato: in *Deployment branches and tags* consenti solo i tag `v*`; facoltativamente
   aggiungi *Required reviewers* per approvare ogni pubblicazione.
2. Nell'environment `release`, *Add environment secret* per:

   | Secret | Valore |
   | --- | --- |
   | `ANDROID_KEYSTORE_BASE64` | il file `.jks` codificato in Base64 (comando sotto) |
   | `ANDROID_KEYSTORE_PASSWORD` | password del keystore |
   | `ANDROID_KEY_ALIAS` | `historia-release` (o l'alias scelto) |
   | `ANDROID_KEY_PASSWORD` | password della chiave (con PKCS12 uguale alla precedente) |

   Per copiare il keystore in Base64 negli appunti:

   ```powershell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("$HOME\historia-keys\historia-release.jks")) | Set-Clipboard
   ```

   Dopo averlo incollato nel secret, svuota gli appunti.

## 4. Pubblicare una versione

1. In `android/app/build.gradle.kts` aggiorna `versionName` e **incrementa sempre** `versionCode`
   (deve essere maggiore di quello di ogni versione già pubblicata).
2. Verifica `docs/release-checklist.md`, fai commit e push su `main`.
3. Crea e pubblica il tag, che deve essere `v` + `versionName`:

   ```powershell
   git tag -a v0.1.0-alpha -m "Hi-Storia Go 0.1.0-alpha"
   ```

   ```powershell
   git push origin v0.1.0-alpha
   ```

4. Il workflow *Release APK*:
   - controlla che i secrets esistano e che il tag corrisponda a `versionName`;
   - esegue i test unitari;
   - costruisce l'APK release firmato e cancella subito il keystore dal runner;
   - verifica la firma con `apksigner`;
   - crea la release con `HiStoriaGo-<versione>.apk`, il file `.sha256` e note con istruzioni di
     installazione, versionCode e impronte SHA-256 di APK e certificato.

   Le versioni con un suffisso (`-alpha`, `-beta`, `-rc1`) vengono pubblicate come *pre-release*.

Da *Actions → Release APK → Run workflow* si può anche avviare il workflow a mano: costruisce e
firma l'APK e lo allega all'esecuzione come artifact, **senza** creare una release (utile per una
prova prima del tag).

## 5. Installazione sullo smartphone

1. Dallo smartphone apri la pagina della release e tocca `HiStoriaGo-<versione>.apk` in *Assets*
   (oppure copia il file via cavo, chiavetta USB OTG o Drive).
2. Apri il file scaricato. Android chiede di consentire al browser o all'app File di
   *installare app sconosciute*: abilitalo per quell'app e torna indietro. Non è la modalità
   sviluppatore e non richiede il debug USB; dopo l'installazione si può disattivare di nuovo.
3. Tocca **Installa**. Se Google Play Protect segnala un'app sconosciuta, scegli
   *Altri dettagli → Installa comunque* (o equivalente). Al primo avvio Play Protect può proporre
   una scansione dell'app: si può accettare.

### Aggiornamenti

Basta installare il nuovo APK sopra quello esistente: impostazioni, progetto e audio restano.

### Passaggio da una build di sviluppo

Un'installazione debug è firmata con una chiave diversa, quindi Android rifiuta l'APK release
("app non installata" / conflitto con un pacchetto esistente). Procedura:

1. nella vecchia app: *Progetto → Esporta progetto* (vedi `project-transfer.md`);
2. disinstalla l'app;
3. installa l'APK release;
4. *Progetto → Importa progetto*.

### Verificare l'autenticità (facoltativo)

L'impronta SHA-256 del certificato è riportata nelle note di ogni release e non cambia tra le
versioni. Si può confrontare con `apksigner verify --print-certs`, oppure confrontare l'impronta
del file scaricato con il `.sha256` allegato:

```powershell
Get-FileHash .\HiStoriaGo-0.1.0-alpha.apk -Algorithm SHA256
```

## Valutazione di GitHub Releases

**Vantaggi**
- Una pagina pubblica con lo storico delle versioni, le note e il download diretto dell'APK, senza
  costi e senza account per chi scarica.
- Pubblicazione automatica e riproducibile da un tag; firma eseguita in CI con secrets cifrati e
  protetti dall'environment `release`.
- Il link `https://github.com/hi-Storia/Hi-Storia-Go/releases` può essere stampato come QR code
  sulle installazioni o nei materiali per le scuole.

**Limiti**
- Nessun aggiornamento automatico: ogni smartphone va aggiornato scaricando il nuovo APK (si può
  usare un'app come Obtainium, che controlla le GitHub Releases, se in futuro servisse).
- Le *pre-release* non compaiono in `…/releases/latest`: finché le versioni sono `-alpha` va
  condiviso il link `…/releases`. Dalla prima versione stabile (senza suffisso) funzionerà anche
  `https://github.com/hi-Storia/Hi-Storia-Go/releases/latest`.
- Play Protect mostra avvisi per le app installate fuori dal Play Store; vanno spiegati agli utenti.

**Alternative valutate**
- *Google Play*: aggiornamenti automatici e nessun avviso, ma richiede un account sviluppatore,
  AAB, revisione e una politica privacy; prematuro per una versione alpha.
- *F-Droid*: adatto a un progetto EUPL, ma compila e firma l'app con le proprie chiavi e ha tempi
  di inclusione lunghi; valutabile per una versione stabile.

Per la fase attuale (alpha, installazioni gestite da scuole e musei) GitHub Releases è la scelta
più semplice e adeguata.

## Note sulla build release

- `isMinifyEnabled = false`: R8 non è attivo. L'APK pesa circa 28 MB (circa 18 MB sono audio
  standard in `assets/`). Attivare la minificazione ridurrebbe il codice ma richiede regole
  ProGuard verificate su dispositivo per Media3 e usb-serial-for-android.
- Firma APK Signature Scheme v2 (sufficiente con `minSdk` 26).
- Il workflow usa JDK 17 come la CI; la build locale funziona anche con il JBR 21 di Android Studio.
