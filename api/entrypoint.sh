#!/bin/sh
JAVA_OPTS="${MIN_HEAP} ${MAX_HEAP} --enable-preview"
exec java $JAVA_OPTS -jar app.jar

