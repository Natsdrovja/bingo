# Bingo Auto

App Android (Kotlin) à usage personnel qui **daube automatiquement** les cases
d'un jeu de bingo : elle lit l'écran, reconnaît les numéros annoncés et les
numéros de tes grilles, et tape les cases correspondantes à ta place.

## Comment ça marche

Le jeu de bingo est un jeu graphique : ses numéros ne sont pas exposés à
l'arbre d'accessibilité Android. L'app combine donc trois briques :

1. **MediaProjection** — capture l'écran en continu.
2. **ML Kit (OCR hors-ligne)** — reconnaît les numéros et leurs positions.
3. **Service d'accessibilité** — tape aux coordonnées des cases (sans root).

La logique d'auto-daub ([`BingoEngine`](app/src/main/java/com/nathan/bingoauto/BingoEngine.kt)) :
- les numéros vus dans la **bande haute** (les balls annoncés) alimentent la
  liste des numéros sortis ;
- toute case des grilles dont le numéro est sorti et qui n'a pas encore été
  tapée est daubée une seule fois.

## Utilisation

1. Installe l'APK (téléchargeable dans les Releases GitHub, build auto).
2. Ouvre **Bingo Auto** et accorde les 2 permissions :
   - **Superposition** (pastille flottante),
   - **Accessibilité** → active « Bingo Auto ».
3. Appuie sur **Démarrer le bot**, accepte la capture d'écran, ouvre ton jeu.
4. Une pastille **▶** flotte par-dessus le jeu :
   - **appui court** = lancer / mettre en pause,
   - **appui long** = nouvelle partie (réinitialise les numéros sortis),
   - **✕** = arrêter le bot.

## Réglages

Constantes ajustables dans `BingoEngine.kt` si la détection dérape :
`CALLED_BAND_RATIO` (hauteur de la bande des balls), `COUNTER_ZONE_RATIO`
(zone droite ignorée : score / compteur), `CELL_BUCKET_PX`. La cadence de
capture est `FRAME_INTERVAL_MS` dans `ScreenCaptureService.kt`.

## Build

- Local : `./gradlew assembleDebug` (Android SDK 35 requis).
- CI : à chaque push sur `main`, GitHub Actions compile l'APK debug et
  publie une release `v1.0.<run_number>` avec l'APK en pièce jointe.

> Usage strictement personnel. Automatiser un jeu peut enfreindre ses
> conditions d'utilisation — à tes risques.
