#!/bin/sh
# Gradle wrapper script for Unix
GRADLE_APP_NAME="Gradle"
APP_HOME="$(cd "$(dirname "$0")" && pwd -P)"
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
exec "$JAVACMD" "${JVM_OPTS[@]}" \
  -classpath "$CLASSPATH" \
  org.gradle.wrapper.GradleWrapperMain "$@"
