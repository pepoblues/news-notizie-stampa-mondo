# MondoFeed Android

MVP Android nativo in Kotlin/Jetpack Compose. Legge RSS/Atom, classifica le fonti da `app/src/main/assets/feeds.json`, verifica i feed con GitHub Actions e produce un APK debug.

## Generare l'APK su GitHub
1. Crea un repository GitHub vuoto.
2. Carica tutto il contenuto di questa cartella nella radice del repository.
3. Apri **Actions > Build APK > Run workflow**.
4. Scarica l'artifact `MondoFeed-debug-apk`.

## Verificare i feed
Esegui `python catalog-tools/validate_feeds.py`. Il workflow `Check RSS feeds` lo esegue ogni lunedi e salva `feed-health-report.json` come artifact.

## Catalogo
Il file incluso e un catalogo iniziale dimostrativo, non ancora 250 fonti definitive. Ogni URL deve essere verificato e deve rispettare le condizioni dell'editore. Aggiungi record mantenendo gli ID univoci. Per sostituire una fonte cessata, conserva territorio, lingua e categoria e cambia `id`, `name`, `feedUrl`, `websiteUrl`.

## Nota release
L'APK generato e debug. Per Google Play occorre configurare firma, segreti GitHub, privacy policy, data safety e una release AAB.

## Versione 0.3
- Grafica rinnovata con identita blu notte, blu e oro.
- Lettura degli articoli dentro l'app tramite WebView.
- Indice laterale al 60%, filtri per Paese/testata e preferiti persistenti.
- Catalogo territoriale ampliato. Gli URL candidati vengono certificati dal workflow Check RSS feeds prima della release.

## Versione 0.4 - immagini RSS
Le schede estraggono immagini da media:content, media:thumbnail, enclosure e dal primo tag img della descrizione HTML. Coil 3 gestisce download, ridimensionamento e cache. Se una fonte non fornisce immagini viene mostrato un fallback grafico con il nome della testata.


## Versione 0.4.1 - correzione compatibilita immagini
Coil e stato fissato alla versione 2.7.0, compatibile con compileSdk 35 e Android Gradle Plugin 8.7.3. Rimossa la dipendenza Coil 3.6.2 che richiedeva compileSdk 37 e AGP 9.1.


## Versione 0.4.2 - fix Coil 2
Rimosso l'import non disponibile `coil.request.crossfade`. In Coil 2.7.0, `crossfade(true)` e un metodo di `ImageRequest.Builder` e non richiede tale import.
