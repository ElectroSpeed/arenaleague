@echo off
setlocal enabledelayedexpansion

rem ====================================================================
rem  ArenaLeague - lancement de l'application
rem
rem  Repond a la contrainte du sujet : "application demarrable
rem  simplement". Double-cliquer suffit.
rem
rem  Volontairement sans accents : un fichier .bat est lu dans la page
rem  de codes de la console, qui n'est pas UTF-8 par defaut. Des
rem  accents s'y afficheraient en charabia sur un poste different.
rem ====================================================================

rem Se placer dans le dossier du script. La base H2 est configuree en
rem ./data/arenaleague, donc relative au dossier courant : lance
rem d'ailleurs, le programme creerait une base vide au mauvais endroit.
cd /d "%~dp0"

set "JAR=target\arenaleague-1.0.0.jar"
set "JAVA_BIN="

rem ---------------------------------------------------------------
rem  Trouver un Java, du plus explicite au plus devinable
rem ---------------------------------------------------------------
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_BIN=%JAVA_HOME%\bin\java.exe"

if not defined JAVA_BIN (
  for %%J in (java.exe) do if not "%%~$PATH:J"=="" set "JAVA_BIN=%%~$PATH:J"
)

rem Ni JAVA_HOME ni PATH : sur ce poste, le seul JDK disponible est
rem celui embarque dans IntelliJ. On prend de preference la Community,
rem dont le JBR est un 21 comme l'exige maven.compiler.release.
if not defined JAVA_BIN (
  for /d %%D in ("%ProgramFiles%\JetBrains\*Community*") do (
    if exist "%%D\jbr\bin\java.exe" set "JAVA_BIN=%%D\jbr\bin\java.exe"
  )
)
if not defined JAVA_BIN (
  for /d %%D in ("%ProgramFiles%\JetBrains\*") do (
    if exist "%%D\jbr\bin\java.exe" set "JAVA_BIN=%%D\jbr\bin\java.exe"
  )
)

if not defined JAVA_BIN (
  echo.
  echo   Aucun Java trouve.
  echo.
  echo   Installez un JDK 21 ou superieur, ou definissez JAVA_HOME.
  echo   Sur ce poste, celui d'IntelliJ IDEA Community fait l'affaire.
  echo.
  goto :fin
)

rem ---------------------------------------------------------------
rem  Le jar doit exister
rem ---------------------------------------------------------------
if not exist "%JAR%" (
  echo.
  echo   %JAR% est introuvable.
  echo.
  echo   Construisez-le d'abord :
  echo       mvn clean install
  echo.
  echo   Maven n'est pas dans le PATH sur ce poste : voir CLAUDE.md,
  echo   section Environnement, pour le preambule PowerShell.
  echo.
  goto :fin
)

rem ---------------------------------------------------------------
rem  Lancement
rem ---------------------------------------------------------------
echo.
echo   ArenaLeague
echo   Java : %JAVA_BIN%
echo.
echo   Comptes : orga / orga  et  arbitre / arbitre
echo.
echo   Cette console affiche le journal de l'application.
echo   La fermer ferme aussi l'application.
echo.

"%JAVA_BIN%" -jar "%JAR%"
set "CODE=%ERRORLEVEL%"

if not "%CODE%"=="0" (
  echo.
  echo   L'application s'est arretee avec le code %CODE%.
  echo.
  echo   Causes les plus frequentes :
  echo.
  echo     - une autre instance est deja ouverte. H2 en mode fichier
  echo       est a connexion exclusive : une seule a la fois.
  echo.
  echo     - la base est dans un etat incoherent. Supprimez le dossier
  echo       data puis relancez : Flyway rejoue le jeu de donnees.
  echo.
)

:fin
echo.
pause
endlocal
