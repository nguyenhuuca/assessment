#!/bin/sh

#export JAVA_HOME=/usr/local/java
#PATH=/usr/local/java/bin:${PATH}

# .env loading in the shell
loadEnv () {
  if [ -f .env ]
  then
      set -a
      . ./.env
      set +a
  fi
}


# . ../config/env.properties
loadEnv


# java option configure.
JAVA_OPT="${MAX_HEAP} ${MIN_HEAP} -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=logs/heap_dump.hprof"
echo "java option configure:"
echo "${JAVA_OPT}"

echo "" > logs/assessment.log


## in case rung jar file
nohup $JAVA_HOME/bin/java $JAVA_OPT -jar assessment-1.0.0.jar > /dev/null 2>&1 & echo  "$!" > pid/assessment.pid

i=0
while [ $i -lt 5 ]
do

    if grep -q "Tomcat started on port(s)" logs/assessment.log;
        then
            echo "Started FunnyApp"
            echo "process id:$!"
            break
        else
                echo "waiting..."
    fi
    sleep 5

done