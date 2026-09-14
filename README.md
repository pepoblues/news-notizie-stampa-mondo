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
