#!/bin/sh

main() {
  loadEnv
  build
  start
}

# .env loading in the shell
loadEnv () {
  if [ -f .env ]
  then
      set -a
      . ./.env
      set +a
  fi
}
check_rs() {
   # shellcheck disable=SC2039
   if [[ "$1" -ne 0 ]]; then
      # shellcheck disable=SC2154
      echo "Could not perform on local"
      # shellcheck disable=SC2154
      exit 1
    fi
}

build () {
  echo "building local..."
  mvn clean install
  check_rs "$?"
}

start () {
  java -jar ./target/assessment-1.0.0.jar
  check_rs "$?"
}

# run main
main "$@"; exit
