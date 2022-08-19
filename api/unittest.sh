#!/bin/sh
# This is a comment!

main() {
  # shellcheck disable=SC2039
  loadEnv
  unit_test_local
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
      echo "Could not perform unit test on $env"
      # shellcheck disable=SC2154
      exit 1
    fi
}

unit_test_local() {

  echo "running unit test on local..."
  mvn -DskipTests=false test
  check_rs "$?"
}

# run main
main "$@"; exit
